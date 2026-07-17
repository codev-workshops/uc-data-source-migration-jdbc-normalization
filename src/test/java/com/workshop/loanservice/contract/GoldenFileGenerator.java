package com.workshop.loanservice.contract;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * One-off utility that (re)captures the shared golden fixtures from the LEGACY
 * data source -- i.e. Task 4 Step 1, "capture the current API responses as
 * golden files before migration". It is {@code @Disabled} so it never runs in a
 * normal build; enable it and run manually to regenerate the contract fixtures
 * (e.g. after the migration deliberately changes an endpoint).
 *
 * <p>The fixtures it writes are the single source of truth compared by
 * {@link ApiContractTest} for BOTH data-source parameters.
 */
@Disabled("run manually to (re)generate golden fixtures from the legacy data source")
class GoldenFileGenerator {

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");

    // Preserve BigDecimal scale (e.g. 4.750) when pretty-printing: read floats as
    // BigDecimal and do NOT strip trailing zeros (Jackson 2.15 strips them by default).
    private final ObjectMapper prettyMapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    @Test
    void generate() throws Exception {
        DataSourceContext ds = DataSourceContexts.forDataSource("legacy");
        Files.createDirectories(GOLDEN_DIR);

        write(ds, "/api/loans", "loans.json");
        write(ds, "/api/loans/LN-2019-00142", "loan-LN-2019-00142.json");
        write(ds, "/api/loans/LN-2019-00142/payments", "payments-LN-2019-00142.json");
        write(ds, "/api/borrowers", "borrowers.json");
        write(ds, "/api/borrowers/B-10001", "borrower-B-10001.json");
        write(ds, "/api/borrowers/B-10005", "borrower-B-10005.json");
    }

    private void write(DataSourceContext ds, String url, String fileName) throws Exception {
        String raw = ds.mockMvc()
                .perform(get(url))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode tree = prettyMapper.readTree(raw);
        String pretty = prettyMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree) + "\n";
        Files.writeString(GOLDEN_DIR.resolve(fileName), pretty, StandardCharsets.UTF_8);
    }
}
