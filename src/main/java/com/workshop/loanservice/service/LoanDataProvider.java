package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;

import java.util.List;

/**
 * Read-side abstraction over a data source. Implementations produce the exact
 * same DTOs (same fields, ids, date formats and display values) regardless of
 * whether the data is read from the legacy CDW tables or the modern schema.
 *
 * Selected at runtime by {@code LoanService} based on the
 * {@code loanservice.datasource} feature flag.
 */
public interface LoanDataProvider {

    List<LoanSummaryDto> getAllLoans();

    LoanSummaryDto getLoanById(String loanAccountNumber);

    List<BorrowerDto> getAllBorrowers();

    BorrowerDto getBorrowerById(String borrowerId);

    List<PaymentDto> getPaymentsByLoan(String loanAccountNumber);
}
