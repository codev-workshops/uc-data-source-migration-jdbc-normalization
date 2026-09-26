package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import java.util.List;

/**
 * Read model behind the REST layer. Exactly one implementation is wired at runtime, selected by the
 * {@code application.data-mode} property: {@link NormalizedLoanService} (default) or the deprecated
 * {@link LegacyLoanService}.
 */
public interface LoanQueryService {

  List<LoanSummaryDto> getAllLoans();

  LoanSummaryDto getLoanById(String loanAccountNumber);

  List<BorrowerDto> getAllBorrowers();

  BorrowerDto getBorrowerById(String borrowerId);

  List<PaymentDto> getPaymentsByLoan(String loanAccountNumber);
}
