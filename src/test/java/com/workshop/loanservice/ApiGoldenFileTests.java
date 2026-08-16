package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Golden-file regression: the responses in {@code src/test/resources/golden} were
 * captured from the application while it still read the legacy CDW tables. The
 * modern data source has to reproduce them byte for byte.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiGoldenFileTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.workshop.loanservice.service.LoanService loanService;

    @Test
    void servesTheModernDataSourceByDefault() {
        assertThat(loanService.activeDataSourceMode()).isEqualTo("modern");
    }

    @ParameterizedTest
    @CsvSource({
            "/api/loans, golden/loans.json",
            "/api/loans/LN-2019-00142, golden/loan-LN-2019-00142.json",
            "/api/borrowers, golden/borrowers.json",
            "/api/borrowers/B-10001, golden/borrower-B-10001.json",
            "/api/loans/LN-2019-00142/payments, golden/payments-LN-2019-00142.json"
    })
    void responseMatchesLegacyBaseline(String path, String goldenFile) throws Exception {
        String actual = mockMvc.perform(get(path))
                .andReturn().getResponse().getContentAsString();

        assertThat(actual).isEqualTo(golden(goldenFile));
    }

    private String golden(String name) throws Exception {
        try (var in = new ClassPathResource(name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
