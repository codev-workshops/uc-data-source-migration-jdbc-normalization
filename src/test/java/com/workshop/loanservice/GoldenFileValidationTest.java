package com.workshop.loanservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Golden File Validation Tests (Task 4).
 *
 * Compares the modern data source API responses against golden files
 * captured from the legacy implementation to prove API parity.
 *
 * Known intentional difference:
 * - paymentId: Legacy uses sequence strings (e.g. "PMT-2025120001"),
 *   modern uses auto-increment Long IDs (e.g. "1"). This field is
 *   excluded from strict comparison.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.sql.init.continue-on-error=true")
class GoldenFileValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/loans — all loans match golden file")
    void getAllLoans_matchesGoldenFile() throws Exception {
        String expected = loadGoldenFile("loans_all.json");
        String actual = mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("GET /api/loans/{id} — single loan matches golden file")
    void getLoanById_matchesGoldenFile() throws Exception {
        String expected = loadGoldenFile("loan_detail.json");
        String actual = mockMvc.perform(get("/api/loans/LN-2019-00142"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("GET /api/loans/{id}/payments — payments match golden file (paymentId excluded)")
    void getPaymentsByLoan_matchesGoldenFile() throws Exception {
        String expected = loadGoldenFile("loan_payments.json");
        String actual = mockMvc.perform(get("/api/loans/LN-2019-00142/payments"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // paymentId changed from legacy sequence (PMT-*) to auto-increment Long;
        // this is a documented intentional difference
        JSONAssert.assertEquals(expected, actual,
                new CustomComparator(JSONCompareMode.STRICT,
                        new Customization("[*].paymentId", (o1, o2) -> true)));
    }

    @Test
    @DisplayName("GET /api/borrowers — all borrowers match golden file")
    void getAllBorrowers_matchesGoldenFile() throws Exception {
        String expected = loadGoldenFile("borrowers_all.json");
        String actual = mockMvc.perform(get("/api/borrowers"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("GET /api/borrowers/{id} — borrower detail matches golden file")
    void getBorrowerById_matchesGoldenFile() throws Exception {
        String expected = loadGoldenFile("borrower_detail.json");
        String actual = mockMvc.perform(get("/api/borrowers/B-10001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }

    private String loadGoldenFile(String filename) throws Exception {
        byte[] bytes = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("golden/" + filename),
                "Golden file not found: " + filename
        ).readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
