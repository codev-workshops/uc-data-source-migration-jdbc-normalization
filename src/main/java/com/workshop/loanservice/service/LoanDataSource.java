package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import java.util.List;

/**
 * Read side of the REST API. Exactly one implementation is active, selected at startup by
 * {@code loan-service.read-source}: {@code modern} (default, {@link ModernLoanDataSource}) or
 * {@code legacy} ({@link LegacyLoanDataSource}). Both return identical DTOs for migrated data.
 */
public interface LoanDataSource {

    String READ_SOURCE_PROPERTY = "loan-service.read-source";

    List<LoanSummaryDto> getAllLoans();

    LoanSummaryDto getLoanById(String loanAccountNumber);

    List<BorrowerDto> getAllBorrowers();

    BorrowerDto getBorrowerById(String borrowerId);

    List<PaymentDto> getPaymentsByLoan(String loanAccountNumber);
}
