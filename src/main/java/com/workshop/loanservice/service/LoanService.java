package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.provider.DataSourceModeSelector;
import com.workshop.loanservice.provider.LoanDataProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Delegates every read to the {@link LoanDataProvider} currently selected by
 * {@link DataSourceModeSelector} ({@code modern} by default, {@code legacy} to
 * fall back to the CDW tables). The provider is resolved per call, so the mode
 * can be switched at runtime.
 *
 * <p>The legacy string parsing and code expansion that used to live here now
 * lives in {@link com.workshop.loanservice.migration.LegacyTypeConverter} (used
 * by the migration and the legacy provider) and
 * {@link com.workshop.loanservice.provider.PresentationFormat} (used by both
 * providers to keep the API responses identical).
 */
@Service
public class LoanService {

    private final DataSourceModeSelector selector;

    public LoanService(DataSourceModeSelector selector) {
        this.selector = selector;
    }

    public String activeDataSourceMode() {
        return selector.activeMode();
    }

    public List<LoanSummaryDto> getAllLoans() {
        return selector.active().getAllLoans();
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return selector.active().getLoanById(loanAccountNumber);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return selector.active().getAllBorrowers();
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        return selector.active().getBorrowerById(borrowerId);
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return selector.active().getPaymentsByLoan(loanAccountNumber);
    }
}
