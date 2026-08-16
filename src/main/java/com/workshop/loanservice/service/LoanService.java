package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.provider.LoanDataProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Delegates every read to the {@link LoanDataProvider} selected by
 * {@code loanservice.datasource.mode} ({@code modern} by default,
 * {@code legacy} to fall back to the CDW tables).
 *
 * <p>The legacy string parsing and code expansion that used to live here now
 * lives in {@link com.workshop.loanservice.migration.LegacyTypeConverter} (used
 * by the migration and the legacy provider) and
 * {@link com.workshop.loanservice.provider.PresentationFormat} (used by both
 * providers to keep the API responses identical).
 */
@Service
public class LoanService {

    private final LoanDataProvider provider;

    public LoanService(List<LoanDataProvider> providers,
                       @Value("${loanservice.datasource.mode:modern}") String mode) {
        this.provider = providers.stream()
                .filter(p -> p.name().equalsIgnoreCase(mode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown loanservice.datasource.mode: " + mode));
    }

    public String activeDataSourceMode() {
        return provider.name();
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
