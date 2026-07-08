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
 * Golden-file regression suite: every API endpoint response must be
 * byte-identical to the snapshot captured from the legacy-backed API in
 * src/test/resources/golden/. Runs in modern mode (the default
 * datasource.mode), with the startup migration populating the modern
 * tables — so any transformation drift in the migration or the modern
 * read path fails the suite. {@link GoldenFileLegacyModeComparisonTest}
 * runs the same comparisons in legacy mode to guard the dual-read flag.
 *
 * <p>NOTE: the payments endpoint is {@code /api/loans/{id}/payments};
 * the README's {@code /api/payments/loan/{loanId}} is inaccurate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GoldenFileComparisonTest {

    static final String[] BORROWER_IDS =
            {"B-10001", "B-10002", "B-10003", "B-10004", "B-10005"};
    static final String[] LOAN_IDS =
            {"LN-2017-00034", "LN-2018-00089", "LN-2019-00142", "LN-2020-00398", "LN-2021-00567"};

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void allLoansMatchesGolden() throws IOException {
        assertMatchesGolden("/api/loans", "loans.json");
    }

    @Test
    void loanByIdMatchesGolden() throws IOException {
        for (String id : LOAN_IDS) {
            assertMatchesGolden("/api/loans/" + id, "loan_" + id + ".json");
        }
    }

    @Test
    void allBorrowersMatchesGolden() throws IOException {
        assertMatchesGolden("/api/borrowers", "borrowers.json");
    }

    @Test
    void borrowerByIdMatchesGolden() throws IOException {
        for (String id : BORROWER_IDS) {
            assertMatchesGolden("/api/borrowers/" + id, "borrower_" + id + ".json");
        }
    }

    @Test
    void paymentsByLoanMatchesGolden() throws IOException {
        for (String id : LOAN_IDS) {
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
