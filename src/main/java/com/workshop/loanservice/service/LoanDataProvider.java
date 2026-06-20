package com.workshop.loanservice.service;

import com.workshop.loanservice.config.DataSourceMode;
import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;

import java.util.List;

/**
 * Read-side abstraction over the loan data. Two implementations exist
 * (modern and legacy) so the application can switch data sources at runtime
 * via the dual-read feature flag. All implementations must return identical,
 * contract-compatible DTOs.
 */
public interface LoanDataProvider {

    DataSourceMode mode();

    List<LoanSummaryDto> getAllLoans();

    LoanSummaryDto getLoanById(String loanAccountNumber);

    List<BorrowerDto> getAllBorrowers();

    BorrowerDto getBorrowerById(String borrowerId);

    List<PaymentDto> getPaymentsByLoan(String loanAccountNumber);
}
