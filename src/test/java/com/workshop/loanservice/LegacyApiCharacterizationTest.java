package com.workshop.loanservice;

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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:legacy-characterization;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class LegacyApiCharacterizationTest {

    private static final String[] BORROWER_IDS =
            {"B-10001", "B-10002", "B-10003", "B-10004", "B-10005"};
    private static final String[] LOAN_IDS =
            {"LN-2017-00034", "LN-2018-00089", "LN-2019-00142", "LN-2020-00398", "LN-2021-00567"};

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void allLoansMatchesLegacyBehavior() throws IOException {
        assertMatchesGolden("/api/loans", "loans.json");
    }

    @Test
    void loansByIdMatchLegacyBehavior() throws IOException {
        for (String id : LOAN_IDS) {
            assertMatchesGolden("/api/loans/" + id, "loan_" + id + ".json");
        }
    }

    @Test
    void allBorrowersMatchesLegacyBehavior() throws IOException {
        assertMatchesGolden("/api/borrowers", "borrowers.json");
    }

    @Test
    void borrowersByIdMatchLegacyBehavior() throws IOException {
        for (String id : BORROWER_IDS) {
            assertMatchesGolden("/api/borrowers/" + id, "borrower_" + id + ".json");
        }
    }

    @Test
    void paymentsByLoanMatchLegacyBehavior() throws IOException {
        for (String id : LOAN_IDS) {
            assertMatchesGolden("/api/loans/" + id + "/payments", "payments_" + id + ".json");
        }
    }

    private void assertMatchesGolden(String path, String goldenFile) throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        assertEquals(200, response.getStatusCode().value(), "GET " + path);
        assertNotNull(response.getBody(), "GET " + path + " returned no body");

        String expected = new String(
                new ClassPathResource("golden/" + goldenFile).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertEquals(expected, response.getBody(), "GET " + path + " changed");
    }
}
