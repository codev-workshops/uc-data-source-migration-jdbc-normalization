package com.workshop.loanservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Shared golden-file regression assertions for the public API. Subclasses select
 * the data source via {@code loanservice.datasource} (legacy or modern) so the
 * very same golden files validate both read paths.
 *
 * Responses are compared as parsed JSON trees. Numeric values are compared by
 * value, not by serialized form, so that e.g. {@code 285000} (legacy) and
 * {@code 285000.00} (modern, carrying the DB column scale) are treated as equal —
 * the single documented, justified legacy/modern difference. Everything else
 * (field presence, strings, ids, date formats, display values, array ordering)
 * must match exactly.
 */
abstract class AbstractApiGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getAllLoansMatchesGolden() {
        assertMatchesGolden("/api/loans", "golden/loans.json");
    }

    @Test
    void getLoanByIdMatchesGolden() {
        assertMatchesGolden("/api/loans/LN-2019-00142", "golden/loan_LN-2019-00142.json");
    }

    @Test
    void getAllBorrowersMatchesGolden() {
        assertMatchesGolden("/api/borrowers", "golden/borrowers.json");
    }

    @Test
    void getBorrowerByIdMatchesGolden() {
        assertMatchesGolden("/api/borrowers/B-10001", "golden/borrower_B-10001.json");
    }

    @Test
    void getPaymentsByLoanMatchesGolden() {
        assertMatchesGolden("/api/loans/LN-2019-00142/payments", "golden/payments_LN-2019-00142.json");
    }

    private void assertMatchesGolden(String path, String goldenResource) {
        String actualBody = restTemplate.getForObject(path, String.class);
        JsonNode actual = readTree(actualBody);
        JsonNode expected = readGolden(goldenResource);

        List<String> diffs = new ArrayList<>();
        compare("$", expected, actual, diffs);
        if (!diffs.isEmpty()) {
            fail("Response for %s did not match golden file %s:%n%s",
                    path, goldenResource, String.join("\n", diffs));
        }
    }

    /** Recursive, numeric-aware comparison; collects human-readable diffs. */
    private void compare(String path, JsonNode expected, JsonNode actual, List<String> diffs) {
        if (expected.isNumber() && actual.isNumber()) {
            if (expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
                diffs.add(path + ": expected " + expected + " but was " + actual);
            }
            return;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            diffs.add(path + ": expected " + expected + " but was " + actual);
            return;
        }
        if (expected.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = expected.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                String name = field.getKey();
                if (!actual.has(name)) {
                    diffs.add(path + "." + name + ": missing in response");
                } else {
                    compare(path + "." + name, field.getValue(), actual.get(name), diffs);
                }
            }
            for (Iterator<String> it = actual.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                if (!expected.has(name)) {
                    diffs.add(path + "." + name + ": unexpected field in response (" + actual.get(name) + ")");
                }
            }
        } else if (expected.isArray()) {
            if (expected.size() != actual.size()) {
                diffs.add(path + ": expected array of size " + expected.size()
                        + " but was " + actual.size());
                return;
            }
            for (int i = 0; i < expected.size(); i++) {
                compare(path + "[" + i + "]", expected.get(i), actual.get(i), diffs);
            }
        } else if (!expected.equals(actual)) {
            diffs.add(path + ": expected " + expected + " but was " + actual);
        }
    }

    private JsonNode readTree(String json) {
        try {
            assertThat(json).as("response body must not be null").isNotNull();
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response JSON: " + json, e);
        }
    }

    private JsonNode readGolden(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return MAPPER.readTree(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read golden file: " + resource, e);
        }
    }
}
