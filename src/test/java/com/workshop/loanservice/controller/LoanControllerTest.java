package com.workshop.loanservice.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workshop.loanservice.dto.LoanSummaryDto;
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
 * Slice test for {@link LoanController}, with {@link GlobalExceptionHandler} imported so service
 * exceptions are rendered as {@code ErrorResponse} bodies.
 */
@WebMvcTest(LoanController.class)
class LoanControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private LoanQueryService loanService;

  @Test
  void getAllLoansReturnsJsonArray() throws Exception {
    given(loanService.getAllLoans()).willReturn(List.of(loan()));

    mockMvc
        .perform(get("/api/loans"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].loanAccountNumber").value("LN-2019-00142"))
        .andExpect(jsonPath("$[0].borrowerName").value("James Mitchell"))
        .andExpect(jsonPath("$[0].originalAmount").value(285000))
        .andExpect(jsonPath("$[0].status").value("Active"));
  }

  @Test
  void getLoanReturnsSingleLoan() throws Exception {
    given(loanService.getLoanById("LN-2019-00142")).willReturn(loan());

    mockMvc
        .perform(get("/api/loans/LN-2019-00142"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loanAccountNumber").value("LN-2019-00142"))
        .andExpect(jsonPath("$.productDescription").value("30-Year Fixed Rate Mortgage"))
        .andExpect(jsonPath("$.propertyType").value("Single Family Residence"));
  }

  @Test
  void getLoanReturnsNotFoundErrorResponse() throws Exception {
    given(loanService.getLoanById("missing"))
        .willThrow(new ResourceNotFoundException("Loan", "missing"));

    mockMvc
        .perform(get("/api/loans/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Loan not found: missing"))
        .andExpect(jsonPath("$.path").value("/api/loans/missing"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  void getPaymentsReturnsJsonArray() throws Exception {
    given(loanService.getPaymentsByLoan("LN-2019-00142")).willReturn(List.of(payment()));

    mockMvc
        .perform(get("/api/loans/LN-2019-00142/payments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].paymentId").value("PMT-2025120001"))
        .andExpect(jsonPath("$[0].paymentDate").value("12/15/2025"))
        .andExpect(jsonPath("$[0].totalAmount").value(1487.02))
        .andExpect(jsonPath("$[0].type").value("Regular"))
        .andExpect(jsonPath("$[0].status").value("Posted"));
  }

  private static LoanSummaryDto loan() {
    LoanSummaryDto dto = new LoanSummaryDto();
    dto.setLoanAccountNumber("LN-2019-00142");
    dto.setBorrowerName("James Mitchell");
    dto.setProductDescription("30-Year Fixed Rate Mortgage");
    dto.setOriginalAmount(new BigDecimal("285000"));
    dto.setCurrentBalance(new BigDecimal("271432.56"));
    dto.setInterestRate(new BigDecimal("4.750"));
    dto.setMonthlyPayment(new BigDecimal("1487.02"));
    dto.setStatus("Active");
    dto.setOriginationDate("02/15/2019");
    dto.setPropertyAddress("742 Elm Street, Springfield, IL 62701");
    dto.setPropertyType("Single Family Residence");
    return dto;
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
