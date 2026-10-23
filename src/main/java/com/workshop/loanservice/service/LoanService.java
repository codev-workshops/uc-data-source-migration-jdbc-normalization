package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Entry point for the REST controllers. Delegates to the {@link LoanDataSource} selected by
 * {@code loan-service.read-source}, so the API contract is the same whichever schema is read.
 */
@Service
public class LoanService {

    private final LoanDataSource loanDataSource;

    public LoanService(LoanDataSource loanDataSource) {
        this.loanDataSource = loanDataSource;
    }

    public List<LoanSummaryDto> getAllLoans() {
        return loanDataSource.getAllLoans();
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return loanDataSource.getLoanById(loanAccountNumber);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return loanDataSource.getAllBorrowers();
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        return loanDataSource.getBorrowerById(borrowerId);
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return loanDataSource.getPaymentsByLoan(loanAccountNumber);
    }
}
