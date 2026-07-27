package com.workshop.loanservice.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.migration.MigrationIdMap;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Boots the app under the {@code verification} profile (so the test-only startup migration
 * populates the modern datasource from the legacy CDW seed) and verifies that the {@code mode=modern}
 * responses are STRUCTURALLY equivalent to the committed legacy golden files under
 * {@code src/test/resources/golden/}.
 *
 * <p>"Structural" means, for every one of the 5 endpoints and applied by the SAME recursive
 * comparator: the same set of fields (presence), the same JSON node types, and the same values.
 * Numbers are compared by numeric value ({@link BigDecimal#compareTo}) so a pure decimal-scale
 * rendering difference (e.g. {@code 4.75} vs {@code 4.750}) is reconciled and reported as a
 * formatting note rather than masking a genuine value mismatch; any real value mismatch fails.
 *
 * <p>The golden files are the legacy contract, captured once by booting the app in {@code mode=legacy}
 * and calling all 5 endpoints (see {@code docs/PARITY_REPORT.md}).
 *
 * <p>The payment {@code paymentId} is a modern-only auto-generated primary key surfaced through the
 * DTO, so it is EXCLUDED from the exact structural match for the payments endpoint and instead
 * validated separately (present, non-null, unique, and correct FK mapping back to its loan via
 * {@link MigrationIdMap}). This exclusion lives purely in test logic; no modern read code is changed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "loanservice.datasource.mode=modern",
        "spring.datasource.url=jdbc:h2:mem:legacydw_verify;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "modern.datasource.url=jdbc:h2:mem:moderndb_verify;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("verification")
@Import(VerificationMigrationConfig.class)
class EndpointParityVerificationTest {

    /** Endpoint path -> golden file on the classpath. Every endpoint runs the same comparator. */
    private static final Map<String, String> ENDPOINT_GOLDEN = new LinkedHashMap<>() {{
        put("/api/loans", "golden/loans.json");
        put("/api/loans/LN-2019-00142", "golden/loan-detail.json");
        put("/api/loans/LN-2019-00142/payments", "golden/payments.json");
        put("/api/borrowers", "golden/borrowers.json");
        put("/api/borrowers/B-10001", "golden/borrower-detail.json");
    }};

    private static final String PAYMENTS_ENDPOINT = "/api/loans/LN-2019-00142/payments";
    /** Modern-only auto-generated field excluded from the exact structural match (validated apart). */
    private static final String EXCLUDED_PAYMENT_FIELD = "paymentId";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int modernPort;

    @Autowired
    private LoanAccountRepository loanAccounts;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private MigrationIdMap idMap;

    @Autowired
    @Qualifier("modernTransactionManager")
    private PlatformTransactionManager modernTransactionManager;

    /**
     * Task 1 guard: the {@code verification} startup migration must actually have populated the
     * modern datasource, otherwise {@code LoanService} silently falls back to legacy and the
     * comparison below would not be testing the modern read path at all.
     */
    @Test
    void verificationMigrationPopulatedTheModernDatasource() {
        TransactionTemplate tx = new TransactionTemplate(modernTransactionManager);
        tx.setReadOnly(true);
        tx.executeWithoutResult(status -> {
            assertThat(loanAccounts.findAll())
                    .as("modern loan_accounts must be populated by the startup migration "
                            + "(empty => LoanService falls back to legacy and the parity test is vacuous)")
                    .isNotEmpty();
            assertThat(payments.findAll())
                    .as("modern payments must be populated by the startup migration")
                    .isNotEmpty();
            assertThat(idMap.count(MigrationIdMap.PAYMENT))
                    .as("migration_id_map must have payment mappings")
                    .isGreaterThan(0);
        });
    }

    @Test
    void modernEndpointsAreStructurallyEquivalentToLegacyGolden() {
        Map<String, JsonNode> modern = captureAsJson("http://localhost:" + modernPort);

        List<Difference> valueMismatches = new ArrayList<>();
        List<Difference> formattingNotes = new ArrayList<>();
        for (Map.Entry<String, String> entry : ENDPOINT_GOLDEN.entrySet()) {
            String endpoint = entry.getKey();
            JsonNode golden = readGolden(entry.getValue());
            JsonNode actual = modern.get(endpoint);

            // The SAME comparator runs for every endpoint; only the excluded-field set differs.
            Set<String> excluded = endpoint.equals(PAYMENTS_ENDPOINT)
                    ? Set.of(EXCLUDED_PAYMENT_FIELD) : Set.of();
            compare(endpoint, golden, actual, excluded, valueMismatches, formattingNotes);
        }

        if (!valueMismatches.isEmpty()) {
            fail(renderFailure(valueMismatches, formattingNotes));
        }
    }

    /**
     * The payment {@code paymentId} is excluded from the exact structural match, so it is validated
     * here instead: present, non-null, unique across payments, and mapping back — via
     * {@link MigrationIdMap} — to a modern payment whose loan matches the JSON {@code loanAccountNumber}.
     */
    @Test
    void excludedPaymentIdIsPresentUniqueAndCorrectlyMapped() {
        JsonNode paymentsJson = captureAsJson("http://localhost:" + modernPort).get(PAYMENTS_ENDPOINT);
        assertThat(paymentsJson.isArray()).as("payments endpoint returns a JSON array").isTrue();

        Set<String> seen = new HashSet<>();
        TransactionTemplate tx = new TransactionTemplate(modernTransactionManager);
        tx.setReadOnly(true);
        tx.executeWithoutResult(status -> {
            for (JsonNode payment : paymentsJson) {
                JsonNode idNode = payment.get(EXCLUDED_PAYMENT_FIELD);
                assertThat(idNode).as("payment must carry a %s field", EXCLUDED_PAYMENT_FIELD).isNotNull();
                assertThat(idNode.isNull()).as("%s must be non-null", EXCLUDED_PAYMENT_FIELD).isFalse();
                String paymentId = idNode.asText();
                assertThat(seen.add(paymentId))
                        .as("%s '%s' must be unique across payments", EXCLUDED_PAYMENT_FIELD, paymentId)
                        .isTrue();

                String loanFromJson = payment.get("loanAccountNumber").asText();
                Long modernId = idMap.findModernId(MigrationIdMap.PAYMENT, paymentId).orElse(null);
                assertThat(modernId)
                        .as("%s '%s' must map to a modern payment via MigrationIdMap", EXCLUDED_PAYMENT_FIELD, paymentId)
                        .isNotNull();
                Payment modernPayment = payments.findById(modernId).orElse(null);
                assertThat(modernPayment)
                        .as("MigrationIdMap points %s '%s' at modern payment id %s which must exist",
                                EXCLUDED_PAYMENT_FIELD, paymentId, modernId)
                        .isNotNull();
                LoanAccount loan = modernPayment.getLoanAccount();
                assertThat(loan).as("modern payment %s must reference a loan", modernId).isNotNull();
                assertThat(loan.getAccountNumber())
                        .as("FK check: payment %s ('%s') loan must match the JSON loanAccountNumber",
                                modernId, paymentId)
                        .isEqualTo(loanFromJson);
            }
        });
    }

    // =========================================================================
    // STRUCTURAL COMPARATOR (single logic reused for all 5 endpoints)
    // =========================================================================

    private void compare(String path, JsonNode golden, JsonNode actual, Set<String> excludedFields,
                         List<Difference> mismatches, List<Difference> formattingNotes) {
        if (golden == null || actual == null) {
            mismatches.add(new Difference(path, "type",
                    golden == null ? "<absent>" : nodeType(golden),
                    actual == null ? "<absent>" : nodeType(actual)));
            return;
        }
        if (!sameType(golden, actual)) {
            mismatches.add(new Difference(path, "type", nodeType(golden), nodeType(actual)));
            return;
        }
        if (golden.isObject()) {
            Set<String> goldenFields = fieldNames(golden);
            Set<String> actualFields = fieldNames(actual);
            for (String field : goldenFields) {
                if (excludedFields.contains(field)) {
                    continue;
                }
                if (!actualFields.contains(field)) {
                    mismatches.add(new Difference(path + "." + field, "presence", "present", "<absent>"));
                    continue;
                }
                compare(path + "." + field, golden.get(field), actual.get(field),
                        excludedFields, mismatches, formattingNotes);
            }
            for (String field : actualFields) {
                if (!excludedFields.contains(field) && !goldenFields.contains(field)) {
                    mismatches.add(new Difference(path + "." + field, "presence", "<absent>", "present"));
                }
            }
        } else if (golden.isArray()) {
            if (golden.size() != actual.size()) {
                mismatches.add(new Difference(path, "array length",
                        String.valueOf(golden.size()), String.valueOf(actual.size())));
                return;
            }
            for (int i = 0; i < golden.size(); i++) {
                compare(path + "[" + i + "]", golden.get(i), actual.get(i),
                        excludedFields, mismatches, formattingNotes);
            }
        } else if (golden.isNumber() && actual.isNumber()) {
            BigDecimal g = golden.decimalValue();
            BigDecimal a = actual.decimalValue();
            if (g.compareTo(a) != 0) {
                mismatches.add(new Difference(path, "number value", golden.asText(), actual.asText()));
            } else if (!golden.asText().equals(actual.asText())) {
                // Same numeric value, different decimal-scale rendering: reconciled, reported only.
                formattingNotes.add(new Difference(path, "decimal scale", golden.asText(), actual.asText()));
            }
        } else if (!golden.equals(actual)) {
            mismatches.add(new Difference(path, "value", golden.asText(), actual.asText()));
        }
    }

    private static boolean sameType(JsonNode a, JsonNode b) {
        if (a.isObject() && b.isObject()) return true;
        if (a.isArray() && b.isArray()) return true;
        if (a.isNumber() && b.isNumber()) return true;
        if (a.isTextual() && b.isTextual()) return true;
        if (a.isBoolean() && b.isBoolean()) return true;
        if (a.isNull() && b.isNull()) return true;
        return false;
    }

    private static String nodeType(JsonNode n) {
        if (n.isObject()) return "object";
        if (n.isArray()) return "array";
        if (n.isNumber()) return "number";
        if (n.isTextual()) return "string";
        if (n.isBoolean()) return "boolean";
        if (n.isNull()) return "null";
        return n.getNodeType().name().toLowerCase();
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }

    private static String renderFailure(List<Difference> mismatches, List<Difference> formattingNotes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Structural parity FAILED between legacy golden and mode=modern responses.\n");
        sb.append("Each row is a field a reviewer must decide on (golden = legacy contract):\n\n");
        for (Difference d : mismatches) {
            sb.append(String.format("  [%s] %s%n      golden(legacy): %s%n      modern       : %s%n",
                    d.kind, d.path, d.golden, d.modern));
        }
        if (!formattingNotes.isEmpty()) {
            sb.append("\nReconciled formatting-only differences (same value, different rendering):\n");
            for (Difference d : formattingNotes) {
                sb.append(String.format("  [%s] %s: golden=%s modern=%s%n",
                        d.kind, d.path, d.golden, d.modern));
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // CAPTURE / GOLDEN LOADING
    // =========================================================================

    private Map<String, JsonNode> captureAsJson(String baseUrl) {
        HttpClient client = HttpClient.newHttpClient();
        Map<String, JsonNode> bodies = new LinkedHashMap<>();
        for (String endpoint : ENDPOINT_GOLDEN.keySet()) {
            try {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(baseUrl + endpoint)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as("HTTP status for %s", endpoint).isEqualTo(200);
                bodies.put(endpoint, MAPPER.readTree(response.body()));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to GET " + baseUrl + endpoint, e);
            }
        }
        return bodies;
    }

    private JsonNode readGolden(String classpathLocation) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            return MAPPER.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("Missing/unreadable golden file: " + classpathLocation, e);
        }
    }

    private record Difference(String path, String kind, String golden, String modern) {
    }
}
