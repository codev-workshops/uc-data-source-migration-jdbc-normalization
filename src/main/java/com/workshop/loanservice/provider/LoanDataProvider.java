package com.workshop.loanservice.provider;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;

import java.util.List;

/**
 * Read seam over the two data sources. The controllers and {@link
 * com.workshop.loanservice.service.LoanService} only ever see DTOs, so switching
 * {@code loanservice.datasource.mode} between {@code legacy} and {@code modern}
 * changes where the data comes from without touching the API.
 */
public interface LoanDataProvider {

    String name();

    List<LoanSummaryDto> getAllLoans();

    LoanSummaryDto getLoanById(String loanAccountNumber);

    List<BorrowerDto> getAllBorrowers();

    BorrowerDto getBorrowerById(String borrowerId);

    List<PaymentDto> getPaymentsByLoan(String loanAccountNumber);
}
