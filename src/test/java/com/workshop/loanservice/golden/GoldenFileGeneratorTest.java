package com.workshop.loanservice.golden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden File Generator — Phase 0A of the data architecture modernization plan.
 *
 * This integration test boots the full application with the legacy H2 data source,
 * calls each API endpoint, and writes the JSON responses to golden files under
 * src/test/resources/golden/. These snapshots serve as the contract baseline for
 * Phase 3 validation testing, ensuring API parity after the migration to the modern schema.
 *
 * IMPORTANT: These golden files capture the EXACT current API responses from the legacy
 * data source. Do not modify them unless the API contract intentionally changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GoldenFileGeneratorTest {

    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/golden");

    private static ObjectMapper objectMapper;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void setup() throws IOException {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        Files.createDirectories(GOLDEN_DIR);
    }

    @Test
    void generateGetAllLoans() throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/loans", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();

        writeGoldenFile("get-all-loans.json", response.getBody());
    }

    @Test
    void generateGetLoanById() throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/loans/LN-2019-00142", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();

        writeGoldenFile("get-loan-by-id.json", response.getBody());
    }

    @Test
    void generateGetPaymentsByLoan() throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/loans/LN-2019-00142/payments", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();

        writeGoldenFile("get-payments-by-loan.json", response.getBody());
    }

    @Test
    void generateGetAllBorrowers() throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/borrowers", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();

        writeGoldenFile("get-all-borrowers.json", response.getBody());
    }

    @Test
    void generateGetBorrowerById() throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/borrowers/B-10001", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();

        writeGoldenFile("get-borrower-by-id.json", response.getBody());
    }

    private void writeGoldenFile(String filename, String jsonResponse) throws IOException {
        // Pretty-print the JSON for readability
        Object json = objectMapper.readValue(jsonResponse, Object.class);
        String prettyJson = objectMapper.writeValueAsString(json);

        Path filePath = GOLDEN_DIR.resolve(filename);
        Files.writeString(filePath, prettyJson + "\n");
    }
}
