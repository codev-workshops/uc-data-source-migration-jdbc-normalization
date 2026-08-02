package com.workshop.loanservice.contract;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The v1 contract, frozen.
 *
 * <p>The golden files were captured from the original service before any of this work started, so a
 * byte-for-byte match is the only acceptable result: same field order, same {@code MM/DD/YYYY} dates,
 * same display strings, same decimal scale. Anything less is a breaking change for clients that were
 * promised none.
 *
 * <p>The subclasses run the identical assertions against each read source, which is what makes the
 * cutover safe to flip and safe to roll back.
 */
abstract class V1GoldenContractTestBase {

    private static final Path GOLDEN = Path.of("src/test/resources/golden");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loansListMatchesGolden() throws Exception {
        assertMatchesGolden("/api/loans", "loans.json");
    }

    @Test
    void singleLoanMatchesGolden() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142", "loan.json");
    }

    @Test
    void paymentsMatchGolden() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142/payments", "payments.json");
    }

    @Test
    void borrowersListMatchesGolden() throws Exception {
        assertMatchesGolden("/api/borrowers", "borrowers.json");
    }

    @Test
    void singleBorrowerMatchesGolden() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10001", "borrower.json");
    }

    /**
     * The original service answered an unknown id with a 500 whose message repeated the caller's
     * input. The status is now a 404 and the explanation is fixed text; the only place the input
     * still appears is the RFC 9457 {@code instance} member, which is the request path itself.
     */
    @Test
    void unknownLoanIsNotFoundAndTheMessageDoesNotEchoTheIdentifier() throws Exception {
        var response = mockMvc.perform(get("/api/loans/{id}", "LN-does-not-exist<script>"))
            .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(JsonPath.<String>read(response.getContentAsString(), "$.detail"))
            .isEqualTo("loan not found");
    }

    private void assertMatchesGolden(String path, String goldenFile) throws Exception {
        String actual = mockMvc.perform(get(path))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(actual).isEqualTo(Files.readString(GOLDEN.resolve(goldenFile)).trim());
    }
}
