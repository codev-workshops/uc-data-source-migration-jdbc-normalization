package com.workshop.loanservice.golden;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compares API responses with golden files captured from the legacy application
 * before the migration (src/test/resources/golden). Numbers are compared by value,
 * so {@code 285000} and {@code 285000.00} are equal; everything else must match exactly.
 */
abstract class AbstractGoldenApiTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private static final Comparator<JsonNode> NUMBERS_BY_VALUE = (a, b) -> {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue());
        }
        return a.equals(b) ? 0 : 1;
    };

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "/api/loans,                          loans.json",
            "/api/loans/LN-2019-00142,            loan-LN-2019-00142.json",
            "/api/borrowers,                      borrowers.json",
            "/api/borrowers/B-10001,              borrower-B-10001.json",
            "/api/borrowers/B-10005,              borrower-B-10005.json",
            "/api/loans/LN-2019-00142/payments,   payments-LN-2019-00142.json",
            "/api/loans/LN-2018-00089/payments,   payments-LN-2018-00089.json"
    })
    void responseMatchesGoldenFile(String url, String goldenFile) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode actual = MAPPER.readTree(body);
        JsonNode expected;
        try (InputStream in = new ClassPathResource("golden/" + goldenFile).getInputStream()) {
            expected = MAPPER.readTree(in);
        }

        assertThat(actual.equals(NUMBERS_BY_VALUE, expected))
                .as("%s should match golden/%s%nexpected: %s%nactual:   %s", url, goldenFile, expected, actual)
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "/api/loans/1,                 Loan not found: 1",
            "/api/loans/INVALID,           Loan not found: INVALID",
            "/api/borrowers/INVALID,       Borrower not found: INVALID",
            "/api/borrowers/1,             Borrower not found: 1",
            "/api/loans/INVALID/payments,  Loan not found: INVALID",
            "/api/loans/1/payments,        Loan not found: 1"
    })
    void missingResourceReturns404(String url, String message) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(url))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
