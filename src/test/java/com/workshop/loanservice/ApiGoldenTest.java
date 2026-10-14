package com.workshop.loanservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Compares every API response with the snapshot captured from the legacy data source.
 *
 * <p>Each file in {@code src/test/resources/golden} is named after its endpoint, with {@code /}
 * replaced by {@code _}: {@code api_loans_LN-2019-00142_payments.json} is the response of {@code
 * GET /api/loans/LN-2019-00142/payments}. Numbers are compared by value, so {@code 285000} equals
 * {@code 285000.00}; everything else, including array order, must match exactly.
 *
 * <p>Uses its own in-memory database: the default one is named and kept open, so a second test
 * context would re-run the schema script against existing tables.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:api-golden-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ApiGoldenTest {

    private static final String GOLDEN_PATTERN = "classpath:golden/*.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private static final Comparator<JsonNode> NUMBERS_BY_VALUE =
            (expected, actual) -> {
                if (expected.isNumber() && actual.isNumber()) {
                    return expected.decimalValue().compareTo(actual.decimalValue());
                }
                return expected.equals(actual) ? 0 : 1;
            };

    @Autowired private MockMvc mockMvc;

    static Stream<String> goldenFiles() throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver().getResources(GOLDEN_PATTERN);
        return Arrays.stream(resources).map(Resource::getFilename).sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFiles")
    void responseMatchesGolden(String fileName) throws Exception {
        String path = "/" + fileName.replace(".json", "").replace('_', '/');
        JsonNode expected = readGolden(fileName);

        String body =
                mockMvc.perform(get(path))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        JsonNode actual = MAPPER.readTree(body);

        assertThat(expected.equals(NUMBERS_BY_VALUE, actual))
                .as("GET %s%nexpected: %s%nactual:   %s", path, expected, actual)
                .isTrue();
    }

    private static JsonNode readGolden(String fileName) throws IOException {
        try (InputStream in = ApiGoldenTest.class.getResourceAsStream("/golden/" + fileName)) {
            return MAPPER.readTree(in);
        }
    }
}
