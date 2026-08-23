package com.workshop.loanservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares live responses served from the modern schema against golden files captured
 * from the legacy implementation before the migration.
 *
 * Numbers are compared by value: the modern schema stores fixed-scale DECIMAL columns,
 * so e.g. 285000 is now serialized as 285000.00.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiCompatibilityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void endpointsMatchLegacyGoldenResponses() throws IOException {
        Map<String, String> endpointsToGoldenFiles = Map.of(
                "/api/loans", "loans.json",
                "/api/borrowers", "borrowers.json",
                "/api/loans/LN-2019-00142", "loan-LN-2019-00142.json",
                "/api/borrowers/B-10003", "borrower-B-10003.json",
                "/api/loans/LN-2019-00142/payments", "payments-LN-2019-00142.json");

        for (Map.Entry<String, String> entry : endpointsToGoldenFiles.entrySet()) {
            ResponseEntity<String> response = restTemplate.getForEntity(entry.getKey(), String.class);
            assertThat(response.getStatusCode()).as(entry.getKey()).isEqualTo(HttpStatus.OK);
            assertThat(normalize(MAPPER.readTree(response.getBody())))
                    .as(entry.getKey())
                    .isEqualTo(normalize(MAPPER.readTree(new ClassPathResource("golden/" + entry.getValue())
                            .getInputStream())));
        }
    }

    @Test
    void paymentsEndpointsAreEquivalent() {
        String nested = restTemplate.getForObject("/api/loans/LN-2019-00142/payments", String.class);
        String flat = restTemplate.getForObject("/api/payments/loan/LN-2019-00142", String.class);
        assertThat(flat).isEqualTo(nested);
    }

    @Test
    void unknownIdentifiersKeepLegacyErrorBehaviour() {
        assertThat(restTemplate.getForEntity("/api/loans/NOPE", String.class).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(restTemplate.getForEntity("/api/borrowers/NOPE", String.class).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void paymentsForUnknownLoanReturnEmptyList() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/loans/NOPE/payments", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    /** Rewrites numeric nodes to a canonical text form so scale differences do not matter. */
    private static JsonNode normalize(JsonNode node) {
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, normalize(array.get(i)));
            }
        } else if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<String> fields = object.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                object.set(field, normalize(object.get(field)));
            }
        } else if (node.isNumber()) {
            return new TextNode(new BigDecimal(node.asText()).stripTrailingZeros().toPlainString());
        }
        return node;
    }
}
