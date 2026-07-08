package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;

import java.util.List;

/**
 * Data-source abstraction for the loan API. Implementations read from either
 * the legacy CDW schema or the modern normalized schema and produce identical
 * DTOs, allowing the operative source to be selected with the
 * {@code datasource.mode} property (see {@link LegacyLoanDataProvider} and
 * {@link ModernLoanDataProvider}).
 */
public interface LoanDataProvider {

    List<LoanSummaryDto> getAllLoans();

    LoanSummaryDto getLoanById(String loanAccountNumber);

    List<BorrowerDto> getAllBorrowers();

    BorrowerDto getBorrowerById(String borrowerId);

    List<PaymentDto> getPaymentsByLoan(String loanAccountNumber);
}
