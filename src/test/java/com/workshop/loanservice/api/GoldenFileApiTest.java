package com.workshop.loanservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Golden-file regression tests for every endpoint in {@code LoanController} and
 * {@code BorrowerController}, run against the legacy seed data in
 * {@code src/main/resources/data-legacy.sql}.
 *
 * <p>Each request is executed through {@link MockMvc} and the response is wrapped in an
 * envelope of the form
 * <pre>{"status": &lt;http status&gt;, "body": &lt;parsed JSON body or null&gt;}</pre>
 * which is compared against the golden file
 * {@code src/test/resources/golden/legacy/&lt;METHOD&gt;_&lt;path with '/' replaced by '_'&gt;.json}
 * (e.g. {@code GET_api_loans_LN-2019-00142_payments.json}). Comparison uses
 * {@link JSONCompareMode#NON_EXTENSIBLE}: the field set must match exactly (no missing or
 * extra fields, array order matters) but object key order is ignored.
 *
 * <p><b>Unhandled exceptions.</b> The legacy service signals "not found" by throwing a bare
 * {@link RuntimeException} with no {@code @ExceptionHandler}. {@link MockMvc} does not run the
 * container's error-page dispatch, so it would surface that as a {@link ServletException}
 * instead of a response. When that happens the test replays the same request against the
 * embedded server (hence {@code webEnvironment = RANDOM_PORT}) and records the real
 * {@code BasicErrorController} response: HTTP 500 with the standard
 * {@code {timestamp, status, error, path}} body. The volatile {@code timestamp} field is
 * normalised to the placeholder {@value #NORMALISED} before comparison.
 *
 * <p>The test uses its own in-memory H2 database name so that the legacy schema script can run
 * independently of any other Spring context cached in the same JVM (the shared
 * {@code legacydw} database would otherwise fail with "table already exists").
 *
 * <p><b>Regenerating the goldens.</b> Run
 * <pre>./mvnw test -Dtest=GoldenFileApiTest -Dgolden.update=true</pre>
 * to rewrite every golden file under {@code src/test/resources/golden/legacy/} from the live
 * responses (pretty-printed). In update mode the assertions are skipped, so always run the
 * suite again without the flag and review the resulting diff before committing it. Goldens
 * must only be regenerated when an API contract change has been explicitly approved.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:goldenlegacydw;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class GoldenFileApiTest {

    private static final Path GOLDEN_DIR = Paths.get("src", "test", "resources", "golden", "legacy");
    private static final boolean UPDATE_GOLDENS = Boolean.getBoolean("golden.update");
    private static final String NORMALISED = "<normalised>";

    private static final List<String> LOAN_IDS = List.of(
            "LN-2019-00142", "LN-2020-00398", "LN-2018-00089", "LN-2021-00567", "LN-2017-00034");
    private static final List<String> BORROWER_IDS = List.of(
            "B-10001", "B-10002", "B-10003", "B-10004", "B-10005");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    static Stream<Arguments> endpoints() {
        Stream.Builder<Arguments> b = Stream.builder();
        b.add(Arguments.of("/api/loans"));
        LOAN_IDS.forEach(id -> b.add(Arguments.of("/api/loans/" + id)));
        LOAN_IDS.forEach(id -> b.add(Arguments.of("/api/loans/" + id + "/payments")));
        b.add(Arguments.of("/api/borrowers"));
        BORROWER_IDS.forEach(id -> b.add(Arguments.of("/api/borrowers/" + id)));
        b.add(Arguments.of("/api/loans/UNKNOWN"));
        b.add(Arguments.of("/api/borrowers/UNKNOWN"));
        return b.build();
    }

    @ParameterizedTest(name = "GET {0}")
    @MethodSource("endpoints")
    void responseMatchesGolden(String path) throws Exception {
        String actual = capture(path);
        Path goldenFile = GOLDEN_DIR.resolve(goldenFileName(path));

        if (UPDATE_GOLDENS) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(goldenFile, actual + System.lineSeparator(), StandardCharsets.UTF_8);
            return;
        }

        assertThat(goldenFile)
                .as("Golden file missing; run with -Dgolden.update=true to create it")
                .exists();
        String expected = Files.readString(goldenFile, StandardCharsets.UTF_8);
        JSONAssert.assertEquals("GET " + path + " differs from " + goldenFile,
                expected, actual, JSONCompareMode.NON_EXTENSIBLE);
    }

    private String capture(String path) throws Exception {
        try {
            MockHttpServletResponse response = mockMvc.perform(get(path)).andReturn().getResponse();
            return toEnvelope(response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
        } catch (ServletException unhandled) {
            ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
            return toEnvelope(response.getStatusCode().value(), response.getBody());
        }
    }

    private String toEnvelope(int status, String body) throws IOException {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("status", status);
        JsonNode bodyNode = (body == null || body.isEmpty()) ? null : objectMapper.readTree(body);
        if (bodyNode instanceof ObjectNode object && object.has("timestamp")) {
            object.put("timestamp", NORMALISED);
        }
        envelope.set("body", bodyNode);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope);
    }

    static String goldenFileName(String path) {
        return "GET" + path.replace('/', '_') + ".json";
    }
}
