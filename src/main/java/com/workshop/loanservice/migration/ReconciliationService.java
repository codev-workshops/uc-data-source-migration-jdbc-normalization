package com.workshop.loanservice.migration;

import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Row-count reconciliation between the two stores.
 *
 * <p>This is the gate for the cutover: dual-write keeps the stores converging, but nothing should
 * flip {@code read-source} to {@code MODERN} until the counts agree. Publishing the drift as a gauge
 * makes "are we safe to cut over?" a dashboard question instead of an argument.
 *
 * <p>Counts, not row-by-row diffs, on purpose: a full comparison of 500k accounts belongs in an
 * offline job with access to PII, not in a service that also serves traffic. Per-request divergence
 * is caught separately by the shadow read in {@code LoanService}.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyAccounts;
    private final LegacyPaymentRepository legacyPayments;
    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository accounts;
    private final PaymentRepository payments;
    private final MeterRegistry meterRegistry;

    public ReconciliationService(LegacyBorrowerRepository legacyBorrowers,
                                 LegacyLoanProductRepository legacyProducts,
                                 LegacyLoanAccountRepository legacyAccounts,
                                 LegacyPaymentRepository legacyPayments,
                                 BorrowerRepository borrowers,
                                 LoanProductRepository products,
                                 LoanAccountRepository accounts,
                                 PaymentRepository payments,
                                 MeterRegistry meterRegistry) {
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyAccounts = legacyAccounts;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.accounts = accounts;
        this.payments = payments;
        this.meterRegistry = meterRegistry;
    }

    /** Difference per table, legacy count minus modern count. Zero everywhere means safe to cut over. */
    public record Drift(String table, long legacyCount, long modernCount) {
        public long difference() {
            return legacyCount - modernCount;
        }

        public boolean isReconciled() {
            return difference() == 0;
        }
    }

    public List<Drift> reconcile() {
        Map<String, Drift> drifts = new LinkedHashMap<>();
        drifts.put("borrowers", new Drift("borrowers", legacyBorrowers.count(), borrowers.count()));
        drifts.put("loan_products", new Drift("loan_products", legacyProducts.count(), products.count()));
        drifts.put("loan_accounts", new Drift("loan_accounts", legacyAccounts.count(), accounts.count()));
        drifts.put("payments", new Drift("payments", legacyPayments.count(), payments.count()));

        drifts.values().forEach(drift -> {
            meterRegistry.gauge("loanservice.reconciliation.drift",
                List.of(Tag.of("table", drift.table())), drift, d -> (double) d.difference());
            if (!drift.isReconciled()) {
                log.warn("Reconciliation drift table={} legacy={} modern={}",
                    drift.table(), drift.legacyCount(), drift.modernCount());
            }
        });
        return List.copyOf(drifts.values());
    }

    public boolean isFullyReconciled() {
        return reconcile().stream().allMatch(Drift::isReconciled);
    }
}
