package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service layer for the loan API. Delegates to the {@link LoanDataProvider}
 * selected by the {@code datasource.mode} property: {@code modern} (default,
 * normalized schema) or {@code legacy} (CDW dual-read fallback). All
 * type translation lives in the providers; this layer is source-agnostic.
 */
@Service
public class LoanService {

    private final LoanDataProvider dataProvider;

    public LoanService(LoanDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    public List<LoanSummaryDto> getAllLoans() {
        return dataProvider.getAllLoans();
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return dataProvider.getLoanById(loanAccountNumber);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return dataProvider.getAllBorrowers();
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        return dataProvider.getBorrowerById(borrowerId);
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return dataProvider.getPaymentsByLoan(loanAccountNumber);
    }
}
