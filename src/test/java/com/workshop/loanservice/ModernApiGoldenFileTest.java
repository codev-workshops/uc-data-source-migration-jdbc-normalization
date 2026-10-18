package com.workshop.loanservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies every LoanController and BorrowerController endpoint, now served from the modern schema,
 * returns the same JSON that the legacy implementation produced. The golden files under
 * {@code src/test/resources/golden} were captured from the application before the migration.
 *
 * <p>Comparison is structural: field names, field order within arrays, strings, nulls and booleans
 * must match exactly; numbers must be numerically equal (e.g. {@code 285000} vs {@code 285000.00}),
 * since the modern DECIMAL(15,2) columns normalise the scale of whole-dollar legacy amounts.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModernApiGoldenFileTest {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private static final Comparator<JsonNode> NUMERIC_VALUE_EQUALITY = (expected, actual) -> {
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue());
        }
        return expected.equals(actual) ? 0 : 1;
    };

    private static final List<String> LOAN_IDS = List.of(
            "LN-2019-00142", "LN-2020-00398", "LN-2018-00089", "LN-2021-00567", "LN-2017-00034");
    private static final List<String> BORROWER_IDS = List.of(
            "B-10001", "B-10002", "B-10003", "B-10004", "B-10005");

    @Autowired
    private MockMvc mockMvc;

    static Stream<Arguments> endpoints() {
        List<Arguments> endpoints = new ArrayList<>();
        endpoints.add(Arguments.of("/api/loans", "loans.json"));
        endpoints.add(Arguments.of("/api/borrowers", "borrowers.json"));
        for (String loanId : LOAN_IDS) {
            endpoints.add(Arguments.of("/api/loans/" + loanId, "loan_" + loanId + ".json"));
            endpoints.add(Arguments.of("/api/loans/" + loanId + "/payments",
                    "loan_" + loanId + "_payments.json"));
        }
        for (String borrowerId : BORROWER_IDS) {
            endpoints.add(Arguments.of("/api/borrowers/" + borrowerId, "borrower_" + borrowerId + ".json"));
        }
        endpoints.add(Arguments.of("/api/loans/LN-0000-00000/payments", "loan_LN-0000-00000_payments.json"));
        return endpoints.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void responseMatchesPreMigrationGoldenFile(String path, String goldenFile) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode expected = readGolden(goldenFile);
        JsonNode actual = MAPPER.readTree(body);

        assertThat(expected.equals(NUMERIC_VALUE_EQUALITY, actual))
                .as("%s differs from golden/%s%nexpected: %s%nactual:   %s", path, goldenFile, expected, actual)
                .isTrue();
    }

    private static JsonNode readGolden(String goldenFile) throws IOException {
        try (InputStream in = new ClassPathResource("golden/" + goldenFile).getInputStream()) {
            return MAPPER.readTree(in);
        }
    }
}
