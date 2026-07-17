package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;

import java.util.List;

/**
 * Read API over a single data source. Implemented once per data source
 * ({@code legacy}, {@code modern}); both produce identical DTOs so the two are
 * interchangeable behind {@link LoanService}'s dual-read switch.
 */
public interface LoanDataProvider {

    DataSourceSelector.DataSource dataSource();

    List<LoanSummaryDto> getAllLoans();

    LoanSummaryDto getLoanById(String loanAccountNumber);

    List<BorrowerDto> getAllBorrowers();

    BorrowerDto getBorrowerById(String borrowerId);

    List<PaymentDto> getPaymentsByLoan(String loanAccountNumber);
}
