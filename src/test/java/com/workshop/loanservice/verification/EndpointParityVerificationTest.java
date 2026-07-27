package com.workshop.loanservice.verification;

import com.workshop.loanservice.LoanServiceApplication;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the app under the {@code verification} profile (so the test-only startup migration
 * populates the modern datasource) and asserts that every endpoint returns byte-for-byte identical
 * JSON whether {@code LoanService} is in {@code mode=modern} or {@code mode=legacy}.
 *
 * <p>The primary context is the {@code mode=modern} run. A second, database-isolated context is
 * booted in {@code mode=legacy} to capture the legacy JSON for comparison. Both contexts use
 * dedicated in-memory database names so this test never pollutes the databases shared by the other
 * tests in the suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "loanservice.datasource.mode=modern",
        "spring.datasource.url=jdbc:h2:mem:legacydw_verify;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "modern.datasource.url=jdbc:h2:mem:moderndb_verify;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("verification")
@Import(VerificationMigrationConfig.class)
class EndpointParityVerificationTest {

    private static final List<String> ENDPOINTS = List.of(
            "/api/loans",
            "/api/loans/LN-2019-00142",
            "/api/loans/LN-2019-00142/payments",
            "/api/borrowers",
            "/api/borrowers/B-10001");

    @LocalServerPort
    private int modernPort;

    @Autowired
    private LoanAccountRepository loanAccounts;

    @Autowired
    private PaymentRepository payments;

    @Test
    void modernAndLegacyEndpointsAreByteForByteIdentical() {
        Map<String, String> modern = capture("http://localhost:" + modernPort);

        // Command-line args override application.properties, unlike SpringApplicationBuilder
        // default properties. Unique in-memory database names keep this context isolated from the
        // databases the rest of the suite (and the modern context above) use.
        String dbSuffix = UUID.randomUUID().toString().replace("-", "");
        try (ConfigurableApplicationContext legacyContext = new SpringApplicationBuilder(LoanServiceApplication.class)
                .run(
                        "--loanservice.datasource.mode=legacy",
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:legacydw_" + dbSuffix + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "--modern.datasource.url=jdbc:h2:mem:moderndb_" + dbSuffix + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")) {
            int legacyPort = legacyContext.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            Map<String, String> legacy = capture("http://localhost:" + legacyPort);

            for (String endpoint : ENDPOINTS) {
                assertThat(modern.get(endpoint))
                        .as("byte-for-byte JSON parity for %s", endpoint)
                        .isEqualTo(legacy.get(endpoint));
            }
        }
    }

    /**
     * Investigation point 2: the values actually stored in the modern schema after migration.
     */
    @Test
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    void modernSchemaStoresExpectedRawValues() {
        List<LoanAccount> accounts = loanAccounts.findAll();
        assertThat(accounts).extracting(LoanAccount::getStatus).containsOnly("ACTIVE");
        assertThat(accounts).extracting(LoanAccount::getPropertyType)
                .containsExactlyInAnyOrder("Single Family", "Single Family", "Single Family",
                        "Condominium", "TWN");

        List<Payment> allPayments = payments.findAll();
        assertThat(allPayments).hasSize(10);
        assertThat(allPayments).extracting(Payment::getType).containsOnly("REGULAR");
        assertThat(allPayments).extracting(Payment::getStatus).containsOnly("POSTED");
    }

    private Map<String, String> capture(String baseUrl) {
        HttpClient client = HttpClient.newHttpClient();
        Map<String, String> bodies = new LinkedHashMap<>();
        for (String endpoint : ENDPOINTS) {
            try {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(baseUrl + endpoint)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as("HTTP status for %s", endpoint).isEqualTo(200);
                bodies.put(endpoint, response.body());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to GET " + baseUrl + endpoint, e);
            }
        }
        return bodies;
    }
}
