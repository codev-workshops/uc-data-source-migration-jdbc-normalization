package com.workshop.loanservice.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Same golden-file comparisons as {@link GoldenFileComparisonTest}, but with
 * the dual-read flag set to {@code datasource.mode=legacy}: the legacy read
 * path must keep producing the exact captured responses.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "datasource.mode=legacy")
public class GoldenFileLegacyModeComparisonTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void allLoansMatchesGolden() throws IOException {
        assertMatchesGolden("/api/loans", "loans.json");
    }

    @Test
    void loanByIdMatchesGolden() throws IOException {
        for (String id : GoldenFileComparisonTest.LOAN_IDS) {
            assertMatchesGolden("/api/loans/" + id, "loan_" + id + ".json");
        }
    }

    @Test
    void allBorrowersMatchesGolden() throws IOException {
        assertMatchesGolden("/api/borrowers", "borrowers.json");
    }

    @Test
    void borrowerByIdMatchesGolden() throws IOException {
        for (String id : GoldenFileComparisonTest.BORROWER_IDS) {
            assertMatchesGolden("/api/borrowers/" + id, "borrower_" + id + ".json");
        }
    }

    @Test
    void paymentsByLoanMatchesGolden() throws IOException {
        for (String id : GoldenFileComparisonTest.LOAN_IDS) {
            assertMatchesGolden("/api/loans/" + id + "/payments", "payments_" + id + ".json");
        }
    }

    private void assertMatchesGolden(String path, String goldenFile) throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        assertEquals(200, response.getStatusCode().value(), "GET " + path);
        String body = response.getBody();
        assertNotNull(body, "GET " + path + " returned empty body");
        String golden = new String(
                new ClassPathResource("golden/" + goldenFile).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertEquals(golden, body,
                "GET " + path + " differs from golden file " + goldenFile);
    }
}
