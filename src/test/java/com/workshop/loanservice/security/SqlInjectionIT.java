package com.workshop.loanservice.security;

import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * SQL injection resistance, asserted end to end.
 *
 * <p>The static guard proves no query is built by concatenation; this proves the runtime behaviour
 * that follows from it - a payload in a path variable or query parameter is data, so it produces a
 * 404 or a 400, never a 500, and never a change to the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SqlInjectionIT {

    private static final String[] PAYLOADS = {
        "' OR '1'='1",
        "'; DROP TABLE loan_accounts; --",
        "LN-2019-00142' OR '1'='1",
        "1 UNION SELECT ssn_hash FROM borrowers",
        "%27%20OR%201%3D1",
        "\\'; SELECT * FROM borrowers; --"
    };

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LoanAccountRepository accounts;
    @Autowired
    private PaymentRepository payments;

    @Test
    void injectionPayloadsInPathVariablesNeitherLeakNorDestroy() throws Exception {
        long accountsBefore = accounts.count();
        long paymentsBefore = payments.count();

        for (String payload : PAYLOADS) {
            assertSafe("/api/loans/" + payload);
            assertSafe("/api/borrowers/" + payload);
            assertSafe("/api/loans/" + payload + "/payments");
            assertSafe("/api/v2/loans/" + payload);
            assertSafe("/api/v2/borrowers/" + payload);
        }

        assertThat(accounts.count()).isEqualTo(accountsBefore);
        assertThat(payments.count()).isEqualTo(paymentsBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "id; DROP TABLE loan_accounts",
        "(SELECT ssn_hash FROM borrowers)",
        "id) UNION SELECT 1 --"
    })
    void injectionPayloadsInSortAreRejectedAsBadInput(String payload) throws Exception {
        int status = mockMvc.perform(get("/api/v2/loans").param("sort", payload))
            .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(400);
    }

    @Test
    void malformedPagingParametersAreRejectedNotSwallowed() throws Exception {
        assertThat(mockMvc.perform(get("/api/v2/loans").param("size", "0"))
            .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mockMvc.perform(get("/api/v2/loans").param("page", "-1"))
            .andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    /** An error body that repeats the payload is a stored-XSS and log-injection vector of its own. */
    private void assertSafe(String path) throws Exception {
        var response = mockMvc.perform(get(path)).andReturn().getResponse();
        assertThat(response.getStatus())
            .as("path %s must not fail internally", path)
            .isNotEqualTo(500);
        // The RFC 9457 instance member is the request path, so the payload can appear there; what
        // must never appear is data the payload was fishing for.
        assertThat(response.getContentAsString()).doesNotContain("ENC_XXX", "ssnHash", "B-10001");
    }
}
