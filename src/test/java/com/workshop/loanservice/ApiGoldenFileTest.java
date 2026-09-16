package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavioral equivalence tests: every endpoint must still return the JSON
 * captured from the legacy implementation before the migration.
 *
 * <p>Comparison is strict on structure, ordering and values. Numbers compare by
 * value, so {@code 285000} and {@code 285000.00} are equal — the only
 * difference the migration introduces, because whole-dollar amounts now come
 * from a {@code DECIMAL(12,2)} column instead of a legacy string.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiGoldenFileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAllLoansMatchesGolden() throws Exception {
        assertMatchesGolden("/api/loans", "loans.json");
    }

    @Test
    void getLoanByIdMatchesGolden() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142", "loan-LN-2019-00142.json");
    }

    @Test
    void getAllBorrowersMatchesGolden() throws Exception {
        assertMatchesGolden("/api/borrowers", "borrowers.json");
    }

    @Test
    void getBorrowerByIdMatchesGolden() throws Exception {
        assertMatchesGolden("/api/borrowers/B-10001", "borrower-B-10001.json");
    }

    @Test
    void getPaymentsByLoanMatchesGolden() throws Exception {
        assertMatchesGolden("/api/loans/LN-2019-00142/payments", "payments-LN-2019-00142.json");
    }

    private void assertMatchesGolden(String path, String goldenFile) throws Exception {
        String actual = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JSONAssert.assertEquals(readGolden(goldenFile), actual, JSONCompareMode.STRICT);
    }

    private String readGolden(String fileName) throws Exception {
        try (var in = new ClassPathResource("golden/" + fileName).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
