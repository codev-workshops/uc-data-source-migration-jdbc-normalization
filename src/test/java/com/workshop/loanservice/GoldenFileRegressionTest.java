package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression oracle: boots the application on the modern schema and asserts that
 * every endpoint returns exactly the JSON captured (from the original
 * legacy-backed app) in {@code src/test/resources/golden/}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GoldenFileRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void allLoansMatchesGolden() {
        assertMatchesGolden("/api/loans", "golden/loans.json");
    }

    @Test
    void loanByIdMatchesGolden() {
        assertMatchesGolden("/api/loans/LN-2019-00142", "golden/loan_LN-2019-00142.json");
    }

    @Test
    void allBorrowersMatchesGolden() {
        assertMatchesGolden("/api/borrowers", "golden/borrowers.json");
    }

    @Test
    void borrowerByIdMatchesGolden() {
        assertMatchesGolden("/api/borrowers/B-10001", "golden/borrower_B-10001.json");
    }

    @Test
    void paymentsByLoanMatchesGolden() {
        assertMatchesGolden("/api/loans/LN-2019-00142/payments", "golden/payments_LN-2019-00142.json");
    }

    private void assertMatchesGolden(String path, String goldenResource) {
        String actual = restTemplate.getForObject("http://localhost:" + port + path, String.class);
        String expected = readGolden(goldenResource);
        assertThat(normalize(actual))
                .as("response body for %s must match %s", path, goldenResource)
                .isEqualTo(normalize(expected));
    }

    private static String readGolden(String resource) {
        try {
            return new String(new ClassPathResource(resource).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read golden file: " + resource, e);
        }
    }

    private static String normalize(String body) {
        return body == null ? null : body.strip();
    }
}
