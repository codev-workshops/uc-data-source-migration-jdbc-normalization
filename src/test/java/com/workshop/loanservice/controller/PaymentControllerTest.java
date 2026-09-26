package com.workshop.loanservice.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.exception.GlobalExceptionHandler;
import com.workshop.loanservice.exception.ResourceNotFoundException;
import com.workshop.loanservice.service.LoanQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for {@link PaymentController}, with {@link GlobalExceptionHandler} imported so
 * service exceptions are rendered as {@code ErrorResponse} bodies.
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private LoanQueryService loanQueryService;

  @Test
  void getPaymentHistoryReturnsJsonArray() throws Exception {
    given(loanQueryService.getPaymentsByLoan("LN-2019-00142")).willReturn(List.of(payment()));

    mockMvc
        .perform(get("/api/payments/loan/LN-2019-00142"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].paymentId").value("PMT-2025120001"))
        .andExpect(jsonPath("$[0].loanAccountNumber").value("LN-2019-00142"))
        .andExpect(jsonPath("$[0].paymentDate").value("12/15/2025"))
        .andExpect(jsonPath("$[0].totalAmount").value(1487.02))
        .andExpect(jsonPath("$[0].principalAmount").value(456.78))
        .andExpect(jsonPath("$[0].interestAmount").value(1074.69))
        .andExpect(jsonPath("$[0].escrowAmount").value(355.55))
        .andExpect(jsonPath("$[0].lateFee").value(0.00))
        .andExpect(jsonPath("$[0].type").value("Regular"))
        .andExpect(jsonPath("$[0].status").value("Posted"));
  }

  @Test
  void getPaymentHistoryReturnsEmptyArrayWhenNoPayments() throws Exception {
    given(loanQueryService.getPaymentsByLoan("LN-UNKNOWN")).willReturn(List.of());

    mockMvc
        .perform(get("/api/payments/loan/LN-UNKNOWN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void getPaymentHistoryReturnsNotFoundErrorResponse() throws Exception {
    given(loanQueryService.getPaymentsByLoan("missing"))
        .willThrow(new ResourceNotFoundException("Loan", "missing"));

    mockMvc
        .perform(get("/api/payments/loan/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Loan not found: missing"))
        .andExpect(jsonPath("$.path").value("/api/payments/loan/missing"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  void getPaymentHistoryReturnsInternalErrorResponseForUnexpectedException() throws Exception {
    given(loanQueryService.getPaymentsByLoan("LN-2019-00142"))
        .willThrow(new RuntimeException("boom"));

    mockMvc
        .perform(get("/api/payments/loan/LN-2019-00142"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Unexpected internal error"))
        .andExpect(jsonPath("$.path").value("/api/payments/loan/LN-2019-00142"));
  }

  private static PaymentDto payment() {
    PaymentDto dto = new PaymentDto();
    dto.setPaymentId("PMT-2025120001");
    dto.setLoanAccountNumber("LN-2019-00142");
    dto.setPaymentDate("12/15/2025");
    dto.setTotalAmount(new BigDecimal("1487.02"));
    dto.setPrincipalAmount(new BigDecimal("456.78"));
    dto.setInterestAmount(new BigDecimal("1074.69"));
    dto.setEscrowAmount(new BigDecimal("355.55"));
    dto.setLateFee(new BigDecimal("0.00"));
    dto.setType("Regular");
    dto.setStatus("Posted");
    return dto;
  }
}
