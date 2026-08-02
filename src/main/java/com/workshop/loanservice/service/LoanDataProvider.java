package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;

import java.util.List;

/**
 * One read model, two implementations: legacy CDW and the modern schema. Both must return exactly
 * the same DTOs so the store can be swapped, shadow-compared and rolled back without the v1
 * contract noticing.
 */
public interface LoanDataProvider {

    String name();

    List<LoanSummaryDto> getAllLoans();

    LoanSummaryDto getLoanById(String loanAccountNumber);

    List<BorrowerDto> getAllBorrowers();

    BorrowerDto getBorrowerById(String borrowerId);

    List<PaymentDto> getPaymentsByLoan(String loanAccountNumber);
}
