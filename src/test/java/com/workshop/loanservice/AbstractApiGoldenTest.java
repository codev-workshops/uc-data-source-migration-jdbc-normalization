package com.workshop.loanservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;

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

        List<String> diffs = JsonCompare.diff(expected, actual);
        if (!diffs.isEmpty()) {
            fail("Response for %s did not match golden file %s:%n%s",
                    path, goldenResource, String.join("\n", diffs));
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
