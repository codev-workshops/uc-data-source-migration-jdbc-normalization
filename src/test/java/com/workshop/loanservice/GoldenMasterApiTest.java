package com.workshop.loanservice;

import jakarta.servlet.ServletException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Golden-master contract for the REST API.
 *
 * The JSON files under src/test/resources/golden were captured verbatim from the
 * application while it was still reading the legacy CDW_* tables. Every response
 * must remain byte-for-byte identical to those captures regardless of the
 * underlying data source.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoldenMasterApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listLoansMatchesGoldenMaster() throws Exception {
        assertGolden("/api/loans", "loans.json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LN-2019-00142", "LN-2020-00398", "LN-2018-00089", "LN-2021-00567", "LN-2017-00034"})
    void singleLoanMatchesGoldenMaster(String loanId) throws Exception {
        assertGolden("/api/loans/" + loanId, "loan-" + loanId + ".json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LN-2019-00142", "LN-2020-00398", "LN-2018-00089", "LN-2021-00567", "LN-2017-00034"})
    void loanPaymentsMatchGoldenMaster(String loanId) throws Exception {
        assertGolden("/api/loans/" + loanId + "/payments", "payments-" + loanId + ".json");
    }

    @Test
    void listBorrowersMatchesGoldenMaster() throws Exception {
        assertGolden("/api/borrowers", "borrowers.json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"B-10001", "B-10002", "B-10003", "B-10004", "B-10005"})
    void singleBorrowerMatchesGoldenMaster(String borrowerId) throws Exception {
        assertGolden("/api/borrowers/" + borrowerId, "borrower-" + borrowerId + ".json");
    }

    @Test
    void unknownLoanRaisesRuntimeException() {
        assertNotFoundRuntimeException("/api/loans/LN-0000-00000", "Loan not found: LN-0000-00000");
    }

    @Test
    void unknownBorrowerRaisesRuntimeException() {
        assertNotFoundRuntimeException("/api/borrowers/B-00000", "Borrower not found: B-00000");
    }

    /**
     * In the real servlet container the unhandled RuntimeException surfaces as HTTP 500;
     * MockMvc rethrows it wrapped in a ServletException, so assert on the cause.
     */
    private void assertNotFoundRuntimeException(String url, String expectedMessage) {
        ServletException ex = assertThrows(ServletException.class, () -> mockMvc.perform(get(url)));
        assertEquals(RuntimeException.class, ex.getCause().getClass());
        assertEquals(expectedMessage, ex.getCause().getMessage());
    }

    @Test
    void paymentsForUnknownLoanIsEmptyList() throws Exception {
        mockMvc.perform(get("/api/loans/LN-0000-00000/payments"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    private void assertGolden(String url, String goldenFile) throws Exception {
        String actual = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(readGolden(goldenFile), actual, "Response for " + url + " diverged from golden master");
    }

    private static String readGolden(String name) throws IOException {
        return new ClassPathResource("golden/" + name).getContentAsString(StandardCharsets.UTF_8).trim();
    }
}
