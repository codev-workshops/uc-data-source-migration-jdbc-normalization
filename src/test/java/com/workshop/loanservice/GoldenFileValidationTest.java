package com.workshop.loanservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Golden-file regression tests for Task 4.
 *
 * Each test hits an API endpoint via TestRestTemplate, parses the JSON
 * response into a Jackson tree, and compares it against the corresponding
 * golden file captured from the legacy system (before migration).
 *
 * A passing test suite proves that the modern data source produces
 * identical API responses to the legacy system.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GoldenFileValidationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    // =========================================================================
    // LOAN ENDPOINTS
    // =========================================================================

    @Test
    void testGetAllLoans() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans", "golden/get-all-loans.json");
    }

    @Test
    void testGetLoanLN2019_00142() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2019-00142", "golden/get-loan-LN-2019-00142.json");
    }

    @Test
    void testGetLoanLN2020_00398() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2020-00398", "golden/get-loan-LN-2020-00398.json");
    }

    @Test
    void testGetLoanLN2018_00089() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2018-00089", "golden/get-loan-LN-2018-00089.json");
    }

    @Test
    void testGetLoanLN2021_00567() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2021-00567", "golden/get-loan-LN-2021-00567.json");
    }

    @Test
    void testGetLoanLN2017_00034() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2017-00034", "golden/get-loan-LN-2017-00034.json");
    }

    // =========================================================================
    // BORROWER ENDPOINTS
    // =========================================================================

    @Test
    void testGetAllBorrowers() throws IOException {
        assertEndpointMatchesGoldenFile("/api/borrowers", "golden/get-all-borrowers.json");
    }

    @Test
    void testGetBorrowerB10001() throws IOException {
        assertEndpointMatchesGoldenFile("/api/borrowers/B-10001", "golden/get-borrower-B-10001.json");
    }

    @Test
    void testGetBorrowerB10002() throws IOException {
        assertEndpointMatchesGoldenFile("/api/borrowers/B-10002", "golden/get-borrower-B-10002.json");
    }

    @Test
    void testGetBorrowerB10003() throws IOException {
        assertEndpointMatchesGoldenFile("/api/borrowers/B-10003", "golden/get-borrower-B-10003.json");
    }

    @Test
    void testGetBorrowerB10004() throws IOException {
        assertEndpointMatchesGoldenFile("/api/borrowers/B-10004", "golden/get-borrower-B-10004.json");
    }

    @Test
    void testGetBorrowerB10005() throws IOException {
        assertEndpointMatchesGoldenFile("/api/borrowers/B-10005", "golden/get-borrower-B-10005.json");
    }

    // =========================================================================
    // PAYMENT ENDPOINTS
    // =========================================================================

    @Test
    void testGetPaymentsLN2019_00142() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2019-00142/payments", "golden/get-payments-LN-2019-00142.json");
    }

    @Test
    void testGetPaymentsLN2020_00398() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2020-00398/payments", "golden/get-payments-LN-2020-00398.json");
    }

    @Test
    void testGetPaymentsLN2018_00089() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2018-00089/payments", "golden/get-payments-LN-2018-00089.json");
    }

    @Test
    void testGetPaymentsLN2021_00567() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2021-00567/payments", "golden/get-payments-LN-2021-00567.json");
    }

    @Test
    void testGetPaymentsLN2017_00034() throws IOException {
        assertEndpointMatchesGoldenFile("/api/loans/LN-2017-00034/payments", "golden/get-payments-LN-2017-00034.json");
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    /**
     * Fetches the given endpoint, parses both the response and the golden file
     * as Jackson JsonNode trees, and asserts deep equality.
     */
    private void assertEndpointMatchesGoldenFile(String endpoint, String goldenFilePath) throws IOException {
        String responseBody = restTemplate.getForObject(baseUrl + endpoint, String.class);
        assertNotNull(responseBody, "Response body should not be null for " + endpoint);

        JsonNode actualTree = objectMapper.readTree(responseBody);

        try (InputStream goldenStream = new ClassPathResource(goldenFilePath).getInputStream()) {
            JsonNode expectedTree = objectMapper.readTree(goldenStream);
            assertEquals(expectedTree, actualTree,
                    "API response for " + endpoint + " does not match golden file " + goldenFilePath);
        }
    }
}
