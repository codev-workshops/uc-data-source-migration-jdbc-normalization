package com.workshop.loanservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file regression tests.
 *
 * Captures the API responses of every endpoint and compares them against
 * checked-in golden JSON files under {@code src/test/resources/golden}. The
 * golden files were captured from the current (legacy) data source and act as
 * the regression oracle for the data-source migration: any later change to the
 * read path must keep these responses byte-for-byte equivalent.
 *
 * Comparison is done on parsed JSON trees so that insignificant whitespace in
 * the golden files is ignored while values, keys and array ordering must match.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGoldenTest {

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
        assertThat(actual)
                .as("Response for %s should match golden file %s", path, goldenResource)
                .isEqualTo(expected);
    }

    private JsonNode readTree(String json) {
        try {
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
