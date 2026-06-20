package com.workshop.loanservice.service;

import com.workshop.loanservice.config.DataSourceMode;
import com.workshop.loanservice.config.DataSourceModeHolder;
import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Facade that the controllers depend on. It delegates every read to the
 * {@link LoanDataProvider} selected by the dual-read feature flag
 * ({@link DataSourceModeHolder}). The default is the modern, normalized schema;
 * flipping the flag to {@code LEGACY} serves the same DTO contract from the
 * legacy CDW tables, enabling a safe, reversible cutover.
 */
@Service
public class LoanService {

    private final DataSourceModeHolder modeHolder;
    private final Map<DataSourceMode, LoanDataProvider> providers = new EnumMap<>(DataSourceMode.class);

    public LoanService(DataSourceModeHolder modeHolder, List<LoanDataProvider> providerBeans) {
        this.modeHolder = modeHolder;
        for (LoanDataProvider provider : providerBeans) {
            providers.put(provider.mode(), provider);
        }
    }

    private LoanDataProvider active() {
        DataSourceMode mode = modeHolder.getMode();
        LoanDataProvider provider = providers.get(mode);
        if (provider == null) {
            throw new IllegalStateException("No LoanDataProvider registered for mode " + mode);
        }
        return provider;
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
