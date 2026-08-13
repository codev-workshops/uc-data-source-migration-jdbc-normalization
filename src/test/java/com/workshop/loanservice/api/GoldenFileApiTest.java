package com.workshop.loanservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replays every REST endpoint against the modern-backed application and compares the
 * response to the golden files captured from the legacy-backed application.
 *
 * <p>Numbers are compared by value, not by textual scale: the legacy schema stored
 * amounts as strings so {@code 285,000} serialized as {@code 285000}, while the modern
 * {@code DECIMAL(12,2)} column serializes as {@code 285000.00}. That is the only
 * intentional difference (see DATA_SOURCE_MIGRATION_NOTES.md).</p>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:goldenapidb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class GoldenFileApiTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allLoansMatchesGoldenFile() throws Exception {
        assertMatchesGolden("/api/loans", "golden/loans.json");
    }

    @Test
    void singleLoanMatchesGoldenFile() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142", "golden/loan-LN-2019-00142.json");
    }

    @Test
    void loanPaymentsMatchGoldenFile() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142/payments", "golden/payments-LN-2019-00142.json");
    }

    @Test
    void allBorrowersMatchGoldenFile() throws Exception {
        assertMatchesGolden("/api/borrowers", "golden/borrowers.json");
    }

    @Test
    void singleBorrowerMatchesGoldenFile() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10001", "golden/borrower-B-10001.json");
    }

    private void assertMatchesGolden(String path, String goldenResource) throws Exception {
        String actual = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode expectedTree = canonicalize(MAPPER.readTree(readGolden(goldenResource)));
        JsonNode actualTree = canonicalize(MAPPER.readTree(actual));

        assertEquals(expectedTree.toPrettyString(), actualTree.toPrettyString(),
                "Response for " + path + " differs from " + goldenResource);
    }

    private String readGolden(String resource) throws IOException {
        try (var in = new ClassPathResource(resource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Renders numbers in a scale-independent form so 285000 and 285000.00 compare equal. */
    private static JsonNode canonicalize(JsonNode node) {
        if (node.isNumber()) {
            BigDecimal value = node.decimalValue().stripTrailingZeros();
            return new TextNode(value.toPlainString());
        }
        if (node.isArray()) {
            ArrayNode array = MAPPER.createArrayNode();
            node.forEach(child -> array.add(canonicalize(child)));
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = MAPPER.createObjectNode();
            node.fields().forEachRemaining(entry -> object.set(entry.getKey(), canonicalize(entry.getValue())));
            return object;
        }
        return node;
    }
}
