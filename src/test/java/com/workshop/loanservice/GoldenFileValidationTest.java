package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GoldenFileValidationTest {

    @Autowired
    private MockMvc mockMvc;

    // --- Loan endpoints ---

    @Test
    void getAllLoans_matchesGolden() throws Exception {
        assertMatchesGolden("/api/loans", "golden/all_loans.json");
    }

    @Test
    void getLoan_LN_2019_00142() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142", "golden/loan_LN-2019-00142.json");
    }

    @Test
    void getLoan_LN_2020_00398() throws Exception {
        assertMatchesGolden("/api/loans/LN-2020-00398", "golden/loan_LN-2020-00398.json");
    }

    @Test
    void getLoan_LN_2018_00089() throws Exception {
        assertMatchesGolden("/api/loans/LN-2018-00089", "golden/loan_LN-2018-00089.json");
    }

    @Test
    void getLoan_LN_2021_00567() throws Exception {
        assertMatchesGolden("/api/loans/LN-2021-00567", "golden/loan_LN-2021-00567.json");
    }

    @Test
    void getLoan_LN_2017_00034() throws Exception {
        assertMatchesGolden("/api/loans/LN-2017-00034", "golden/loan_LN-2017-00034.json");
    }

    // --- Borrower endpoints ---

    @Test
    void getAllBorrowers_matchesGolden() throws Exception {
        assertMatchesGolden("/api/borrowers", "golden/all_borrowers.json");
    }

    @Test
    void getBorrower_B_10001() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10001", "golden/borrower_B-10001.json");
    }

    @Test
    void getBorrower_B_10002() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10002", "golden/borrower_B-10002.json");
    }

    @Test
    void getBorrower_B_10003() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10003", "golden/borrower_B-10003.json");
    }

    @Test
    void getBorrower_B_10004() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10004", "golden/borrower_B-10004.json");
    }

    @Test
    void getBorrower_B_10005() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10005", "golden/borrower_B-10005.json");
    }

    // --- Payment endpoints ---

    @Test
    void getPayments_LN_2019_00142() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142/payments", "golden/payments_LN-2019-00142.json");
    }

    @Test
    void getPayments_LN_2020_00398() throws Exception {
        assertMatchesGolden("/api/loans/LN-2020-00398/payments", "golden/payments_LN-2020-00398.json");
    }

    @Test
    void getPayments_LN_2018_00089() throws Exception {
        assertMatchesGolden("/api/loans/LN-2018-00089/payments", "golden/payments_LN-2018-00089.json");
    }

    @Test
    void getPayments_LN_2021_00567() throws Exception {
        assertMatchesGolden("/api/loans/LN-2021-00567/payments", "golden/payments_LN-2021-00567.json");
    }

    @Test
    void getPayments_LN_2017_00034() throws Exception {
        assertMatchesGolden("/api/loans/LN-2017-00034/payments", "golden/payments_LN-2017-00034.json");
    }

    private void assertMatchesGolden(String endpoint, String goldenPath) throws Exception {
        String expected = new String(
                Files.readAllBytes(new ClassPathResource(goldenPath).getFile().toPath()),
                StandardCharsets.UTF_8);

        mockMvc.perform(get(endpoint))
                .andExpect(status().isOk())
                .andExpect(content().json(expected, true));
    }
}
