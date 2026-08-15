package com.workshop.loanservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts that the modern-backed service returns exactly the JSON the legacy
 * (CDW-backed) service returned. The golden files were captured from the
 * legacy implementation before the service layer was rewired.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allLoansMatchGolden() throws Exception {
        assertMatchesGolden("/api/loans", "golden/loans.json");
    }

    @Test
    void loanByIdMatchesGolden() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142", "golden/loan-LN-2019-00142.json");
    }

    @Test
    void paymentsByLoanMatchGolden() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142/payments", "golden/payments-LN-2019-00142.json");
    }

    @Test
    void allBorrowersMatchGolden() throws Exception {
        assertMatchesGolden("/api/borrowers", "golden/borrowers.json");
    }

    @Test
    void borrowerByIdMatchesGolden() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10001", "golden/borrower-B-10001.json");
    }

    private void assertMatchesGolden(String url, String goldenResource) throws Exception {
        String actual = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode expectedTree = MAPPER.readTree(new ClassPathResource(goldenResource).getInputStream());
        JsonNode actualTree = MAPPER.readTree(actual);
        assertThat(actualTree)
                .as("response of %s must equal golden file %s", url, goldenResource)
                .isEqualTo(expectedTree);
    }
}
