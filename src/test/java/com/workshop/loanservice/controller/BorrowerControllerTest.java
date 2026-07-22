package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BorrowerController.class)
class BorrowerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoanService loanService;

    private BorrowerDto borrower() {
        BorrowerDto dto = new BorrowerDto();
        dto.setId("B-1");
        dto.setFullName("James R. Mitchell");
        dto.setEmail("j.mitchell@email.com");
        dto.setCreditScore(745);
        LoanSummaryDto loan = new LoanSummaryDto();
        loan.setLoanAccountNumber("LN-1");
        dto.setLoans(List.of(loan));
        return dto;
    }

    @Test
    void getAllBorrowers_returnsJsonArray() throws Exception {
        when(loanService.getAllBorrowers()).thenReturn(List.of(borrower()));

        mockMvc.perform(get("/api/borrowers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("B-1"))
                .andExpect(jsonPath("$[0].fullName").value("James R. Mitchell"))
                .andExpect(jsonPath("$[0].creditScore").value(745));
    }

    @Test
    void getBorrowerById_returnsJsonObject() throws Exception {
        when(loanService.getBorrowerById("B-1")).thenReturn(borrower());

        mockMvc.perform(get("/api/borrowers/B-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("B-1"))
                .andExpect(jsonPath("$.fullName").value("James R. Mitchell"))
                .andExpect(jsonPath("$.email").value("j.mitchell@email.com"))
                .andExpect(jsonPath("$.loans[0].loanAccountNumber").value("LN-1"));
    }
}
