package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Public service used by the controllers. Delegates every read to the
 * {@link LoanDataProvider} selected by the {@code loanservice.datasource}
 * feature flag:
 *
 * <pre>
 *   loanservice.datasource=legacy   (default) → LegacyLoanDataProvider
 *   loanservice.datasource=modern             → ModernLoanDataProvider
 * </pre>
 *
 * Both providers return identical DTOs, so the public API contract is unchanged
 * regardless of which data source backs it. The legacy path remains the default
 * and is not removed.
 */
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanDataProvider provider;

    public LoanService(LegacyLoanDataProvider legacyProvider,
                       ModernLoanDataProvider modernProvider,
                       @Value("${loanservice.datasource:legacy}") String dataSource) {
        this.provider = "modern".equalsIgnoreCase(dataSource) ? modernProvider : legacyProvider;
        log.info("Loan read path: loanservice.datasource={} → {}",
                dataSource, this.provider.getClass().getSimpleName());
    }

    public List<LoanSummaryDto> getAllLoans() {
        return provider.getAllLoans();
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return provider.getLoanById(loanAccountNumber);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return provider.getAllBorrowers();
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        return provider.getBorrowerById(borrowerId);
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return provider.getPaymentsByLoan(loanAccountNumber);
    }
}
