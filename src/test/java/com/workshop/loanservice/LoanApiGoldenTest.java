package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests proving the modern-backed API returns responses that are
 * <strong>byte-for-byte identical</strong> to the legacy-backed baseline.
 *
 * <p>The golden files under {@code src/test/resources/golden/} were captured
 * from the original legacy-backed application before the data source was
 * rewired to the modern schema. Any drift in derived names, expanded codes,
 * date formatting, or numeric scale will fail these tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:loansvc-${random.uuid};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class LoanApiGoldenTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAllLoans_matchesGolden() throws Exception {
        assertMatchesGolden("/api/loans", "golden/loans.json");
    }

    @Test
    void getLoanById_matchesGolden() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142", "golden/loan_LN-2019-00142.json");
    }

    @Test
    void getPaymentsByLoan_matchesGolden() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142/payments", "golden/payments_LN-2019-00142.json");
    }

    @Test
    void getPaymentsByLoan_withLateFee_matchesGolden() throws Exception {
        assertMatchesGolden("/api/loans/LN-2018-00089/payments", "golden/payments_LN-2018-00089.json");
    }

    @Test
    void getAllBorrowers_matchesGolden() throws Exception {
        assertMatchesGolden("/api/borrowers", "golden/borrowers.json");
    }

    @Test
    void getBorrowerById_matchesGolden() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10001", "golden/borrower_B-10001.json");
    }

    @Test
    void getBorrowerById_withLoans_matchesGolden() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10002", "golden/borrower_B-10002.json");
    }

    @Test
    void getBorrowerById_nullMiddleInitial_matchesGolden() throws Exception {
        // B-10005 has a null middle initial: fullName must NOT include a middle segment.
        assertMatchesGolden("/api/borrowers/B-10005", "golden/borrower_B-10005.json");
    }

    private void assertMatchesGolden(String url, String goldenResource) throws Exception {
        String actual = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String expected = readGolden(goldenResource);
        assertThat(actual)
                .as("Response for %s must match golden file %s byte-for-byte", url, goldenResource)
                .isEqualTo(expected);
    }

    private String readGolden(String resource) throws Exception {
        try (var in = new ClassPathResource(resource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
