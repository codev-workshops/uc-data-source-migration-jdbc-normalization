package com.workshop.loanservice.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.service.LoanQueryService;
import jakarta.servlet.ServletException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for {@link BorrowerController}. See {@link LoanControllerTest} for why unhandled
 * service exceptions are asserted as a {@link ServletException} rather than an HTTP 500.
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
  void getBorrowerPropagatesUnhandledRuntimeException() {
    given(loanService.getBorrowerById("missing"))
        .willThrow(new RuntimeException("Borrower not found: missing"));

    assertThatThrownBy(() -> mockMvc.perform(get("/api/borrowers/missing")))
        .isInstanceOf(ServletException.class)
        .hasCauseExactlyInstanceOf(RuntimeException.class)
        .hasRootCauseMessage("Borrower not found: missing");
  }

  @Test
  void getAllBorrowersPropagatesUnhandledNumberFormatException() {
    given(loanService.getAllBorrowers())
        .willThrow(new NumberFormatException("For input string: \"abc\""));

    assertThatThrownBy(() -> mockMvc.perform(get("/api/borrowers")))
        .isInstanceOf(ServletException.class)
        .hasCauseInstanceOf(NumberFormatException.class);
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
