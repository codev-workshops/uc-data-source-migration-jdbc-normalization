package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import java.util.List;

/**
 * Read model behind the REST layer. Controllers inject the {@code @Primary}
 * {@link com.workshop.loanservice.routing.LoanServiceRouter}, which delegates per request to
 * {@link NormalizedLoanService} (default) or the deprecated {@link LegacyLoanService} based on the
 * {@code serviceImpl} query parameter.
 */
public interface LoanQueryService {

  List<LoanSummaryDto> getAllLoans();

  LoanSummaryDto getLoanById(String loanAccountNumber);

  List<BorrowerDto> getAllBorrowers();

  BorrowerDto getBorrowerById(String borrowerId);

  List<PaymentDto> getPaymentsByLoan(String loanAccountNumber);
}
