package com.workshop.loanservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:legacygolden;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegacyApiGoldenTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode golden;

    @BeforeAll
    void loadGoldenResponses() throws IOException {
        golden = objectMapper.readTree(
                new ClassPathResource("golden/legacy-api.json").getInputStream()
        );
    }

    @Test
    void loanEndpointsMatchGoldenResponses() throws Exception {
        ArrayNode loans = (ArrayNode) golden.get("loans");
        assertResponse("/api/loans", loans);

        for (JsonNode loan : loans) {
            assertResponse("/api/loans/" + loan.get("loanAccountNumber").asText(), loan);
        }
    }

    @Test
    void borrowerEndpointsMatchGoldenResponses() throws Exception {
        ArrayNode borrowers = (ArrayNode) golden.get("borrowers");
        JsonNode borrowerLoans = golden.get("borrowerLoans");
        ArrayNode loans = (ArrayNode) golden.get("loans");
        assertResponse("/api/borrowers", borrowers);

        for (JsonNode borrower : borrowers) {
            String borrowerId = borrower.get("id").asText();
            String loanAccountNumber = borrowerLoans.get(borrowerId).asText();
            JsonNode loan = findLoan(loans, loanAccountNumber);
            ObjectNode detail = ((ObjectNode) borrower).deepCopy();
            ArrayNode borrowerLoan = objectMapper.createArrayNode();
            borrowerLoan.add(loan);
            detail.set("loans", borrowerLoan);
            assertResponse("/api/borrowers/" + borrowerId, detail);
        }
    }

    @Test
    void paymentEndpointsMatchGoldenResponses() throws Exception {
        Iterator<Map.Entry<String, JsonNode>> payments = golden.get("payments").fields();

        while (payments.hasNext()) {
            Map.Entry<String, JsonNode> entry = payments.next();
            assertResponse("/api/loans/" + entry.getKey() + "/payments", entry.getValue());
        }
    }

    @Test
    void notFoundBehaviorMatchesLegacyResponses() throws Exception {
        assertServerError("/api/loans/DOES-NOT-EXIST");
        assertServerError("/api/borrowers/DOES-NOT-EXIST");
        assertResponse(
                "/api/loans/DOES-NOT-EXIST/payments",
                objectMapper.createArrayNode()
        );
    }

    private JsonNode findLoan(ArrayNode loans, String loanAccountNumber) {
        for (JsonNode loan : loans) {
            if (loanAccountNumber.equals(loan.get("loanAccountNumber").asText())) {
                return loan;
            }
        }
        throw new IllegalStateException("Golden loan not found: " + loanAccountNumber);
    }

    private void assertResponse(String path, JsonNode expected) throws JSONException {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        assertEquals(200, response.getStatusCode().value(), path);
        JSONAssert.assertEquals(
                expected.toString(),
                response.getBody(),
                JSONCompareMode.STRICT
        );
    }

    private void assertServerError(String path) throws Exception {
        ResponseEntity<String> result = restTemplate.getForEntity(path, String.class);
        JsonNode response = objectMapper.readTree(result.getBody());
        assertEquals(500, result.getStatusCode().value(), path);
        assertNotNull(response.get("timestamp"), path);
        assertEquals(500, response.get("status").asInt(), path);
        assertEquals("Internal Server Error", response.get("error").asText(), path);
        assertEquals(path, response.get("path").asText(), path);
    }
}
