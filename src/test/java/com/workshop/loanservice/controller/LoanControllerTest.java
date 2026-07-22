package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
class LoanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoanService loanService;

    private LoanSummaryDto loan() {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber("LN-1");
        dto.setBorrowerName("James Mitchell");
        dto.setProductDescription("30-Year Fixed Rate Mortgage");
        dto.setOriginalAmount(new BigDecimal("285000"));
        dto.setStatus("Active");
        dto.setPropertyType("Single Family Residence");
        return dto;
    }

    private PaymentDto payment() {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId("PMT-1");
        dto.setLoanAccountNumber("LN-1");
        dto.setTotalAmount(new BigDecimal("1487.02"));
        dto.setType("Regular");
        dto.setStatus("Posted");
        return dto;
    }

    @Test
    void getAllLoans_returnsJsonArray() throws Exception {
        when(loanService.getAllLoans()).thenReturn(List.of(loan()));

        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loanAccountNumber").value("LN-1"))
                .andExpect(jsonPath("$[0].borrowerName").value("James Mitchell"))
                .andExpect(jsonPath("$[0].status").value("Active"));
    }

    @Test
    void getLoanById_returnsJsonObject() throws Exception {
        when(loanService.getLoanById("LN-1")).thenReturn(loan());

        mockMvc.perform(get("/api/loans/LN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountNumber").value("LN-1"))
                .andExpect(jsonPath("$.productDescription").value("30-Year Fixed Rate Mortgage"))
                .andExpect(jsonPath("$.propertyType").value("Single Family Residence"));
    }

    @Test
    void getPayments_returnsJsonArray() throws Exception {
        when(loanService.getPaymentsByLoan("LN-1")).thenReturn(List.of(payment()));

        mockMvc.perform(get("/api/loans/LN-1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value("PMT-1"))
                .andExpect(jsonPath("$[0].type").value("Regular"))
                .andExpect(jsonPath("$[0].status").value("Posted"));
    }
}
