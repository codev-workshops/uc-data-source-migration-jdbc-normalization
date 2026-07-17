package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade the controllers depend on. It owns no translation logic anymore; it
 * delegates every read to whichever {@link LoanDataProvider} the runtime
 * {@link DataSourceSelector} points at (dual-read feature flag). Both providers
 * return identical DTOs, so the REST API contract is the same regardless of the
 * active data source.
 */
@Service
public class LoanService {

    private final LegacyLoanDataProvider legacyProvider;
    private final ModernLoanDataProvider modernProvider;
    private final DataSourceSelector selector;

    public LoanService(LegacyLoanDataProvider legacyProvider,
                       ModernLoanDataProvider modernProvider,
                       DataSourceSelector selector) {
        this.legacyProvider = legacyProvider;
        this.modernProvider = modernProvider;
        this.selector = selector;
    }

    private LoanDataProvider active() {
        return switch (selector.getActive()) {
            case LEGACY -> legacyProvider;
            case MODERN -> modernProvider;
        };
    }

    public List<LoanSummaryDto> getAllLoans() {
        return active().getAllLoans();
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return active().getLoanById(loanAccountNumber);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return active().getAllBorrowers();
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        return active().getBorrowerById(borrowerId);
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return active().getPaymentsByLoan(loanAccountNumber);
    }
}
