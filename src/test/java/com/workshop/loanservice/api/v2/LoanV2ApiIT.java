package com.workshop.loanservice.api.v2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoanV2ApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pagesAreBoundedByDefault() throws Exception {
        mockMvc.perform(get("/api/v2/loans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(5))
            .andExpect(jsonPath("$.totalElements").doesNotExist());
    }

    @Test
    void countIsOptIn() throws Exception {
        mockMvc.perform(get("/api/v2/loans").param("count", "true"))
            .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void sizeIsHonouredAndHasNextIsReported() throws Exception {
        mockMvc.perform(get("/api/v2/loans").param("size", "2"))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void sizeIsClampedInsteadOfTrusted() throws Exception {
        mockMvc.perform(get("/api/v2/loans").param("size", "100000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(5));
    }

    @Test
    void keysetCursorWalksTheTable() throws Exception {
        mockMvc.perform(get("/api/v2/loans").param("size", "2").param("afterId", "0"))
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.nextAfterId").value(2));

        mockMvc.perform(get("/api/v2/loans").param("size", "2").param("afterId", "2"))
            .andExpect(jsonPath("$.content[0].id").value(3))
            .andExpect(jsonPath("$.nextAfterId").value(4));
    }

    @Test
    void lastKeysetPageReportsNoMore() throws Exception {
        mockMvc.perform(get("/api/v2/loans").param("size", "50").param("afterId", "0"))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.nextAfterId").doesNotExist());
    }

    @Test
    void allowListedSortIsApplied() throws Exception {
        mockMvc.perform(get("/api/v2/loans").param("sort", "currentBalance,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].accountNumber").value("LN-2021-00567"));
    }

    @Test
    void unknownSortIsRejectedRatherThanPassedToTheQuery() throws Exception {
        mockMvc.perform(get("/api/v2/loans").param("sort", "ssnHash"))
            .andExpect(status().isBadRequest());
    }

    /** v2 speaks ISO-8601 and canonical codes; the display strings stayed behind in v1. */
    @Test
    void v2UsesIsoDatesAndRawCodes() throws Exception {
        mockMvc.perform(get("/api/v2/loans/LN-2019-00142"))
            .andExpect(jsonPath("$.originationDate").value("2019-02-15"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.propertyType").value("SINGLE_FAMILY"));
    }

    @Test
    void borrowerResponseNeverCarriesTheSsn() throws Exception {
        mockMvc.perform(get("/api/v2/borrowers/B-10001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ssnHash").doesNotExist())
            .andExpect(jsonPath("$.ssn").doesNotExist());
    }

    @Test
    void paymentsArePagedNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v2/loans/LN-2019-00142/payments").param("size", "1"))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].paymentDate").value("2025-12-15"));
    }

    @Test
    void unknownLoanIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v2/loans/LN-nope"))
            .andExpect(status().isNotFound());
    }
}
