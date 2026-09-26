package com.workshop.loanservice.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.exception.GlobalExceptionHandler;
import com.workshop.loanservice.exception.ResourceNotFoundException;
import com.workshop.loanservice.service.LoanQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for {@link BorrowerController}, with {@link GlobalExceptionHandler} imported so
 * service exceptions are rendered as {@code ErrorResponse} bodies.
 */
@WebMvcTest(BorrowerController.class)
class BorrowerControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private LoanQueryService loanService;

  @Test
  void getAllBorrowersReturnsJsonArray() throws Exception {
    given(loanService.getAllBorrowers()).willReturn(List.of(borrower()));

    mockMvc
        .perform(get("/api/borrowers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("B-10001"))
        .andExpect(jsonPath("$[0].fullName").value("James R. Mitchell"))
        .andExpect(jsonPath("$[0].creditScore").value(745));
  }

  @Test
  void getBorrowerReturnsBorrowerWithLoans() throws Exception {
    BorrowerDto borrower = borrower();
    LoanSummaryDto loan = new LoanSummaryDto();
    loan.setLoanAccountNumber("LN-2019-00142");
    borrower.setLoans(List.of(loan));
    given(loanService.getBorrowerById("B-10001")).willReturn(borrower);

    mockMvc
        .perform(get("/api/borrowers/B-10001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("B-10001"))
        .andExpect(jsonPath("$.email").value("j.mitchell@email.com"))
        .andExpect(jsonPath("$.loans.length()").value(1))
        .andExpect(jsonPath("$.loans[0].loanAccountNumber").value("LN-2019-00142"));
  }

  @Test
  void getBorrowerReturnsNotFoundErrorResponse() throws Exception {
    given(loanService.getBorrowerById("missing"))
        .willThrow(new ResourceNotFoundException("Borrower", "missing"));

    mockMvc
        .perform(get("/api/borrowers/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Borrower not found: missing"))
        .andExpect(jsonPath("$.path").value("/api/borrowers/missing"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  private static BorrowerDto borrower() {
    BorrowerDto dto = new BorrowerDto();
    dto.setId("B-10001");
    dto.setFullName("James R. Mitchell");
    dto.setEmail("j.mitchell@email.com");
    dto.setPhone("217-555-0142");
    dto.setCity("Springfield");
    dto.setState("IL");
    dto.setCreditScore(745);
    dto.setEmploymentStatus("EMPLOYED");
    return dto;
  }
}
