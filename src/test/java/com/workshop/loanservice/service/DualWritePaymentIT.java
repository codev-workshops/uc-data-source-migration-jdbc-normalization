package com.workshop.loanservice.service;

import com.workshop.loanservice.migration.ReconciliationService;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dual write is what makes the cutover reversible: while it is on, a payment taken after the switch
 * to modern reads still exists in the legacy tables, so flipping {@code read-source} back to
 * {@code legacy} loses nothing. Without it, rollback would silently drop post-cutover writes.
 */
@SpringBootTest(properties = "loanservice.dual-write=true")
class DualWritePaymentIT {

    @Autowired
    private PaymentPostingService paymentPosting;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private LegacyPaymentRepository legacyPayments;
    @Autowired
    private ReconciliationService reconciliation;

    private static final AtomicLong NEXT_ID = new AtomicLong();

    /** The legacy sequence column is 20 characters wide, so ids stay short. */
    private static String nextPaymentId() {
        return "PMT-DW" + NEXT_ID.incrementAndGet();
    }

    private static PaymentPostingService.PaymentRequest request(String paymentId) {
        return new PaymentPostingService.PaymentRequest(
            paymentId, "LN-2019-00142", LocalDate.of(2024, 3, 1),
            new BigDecimal("1487.02"), new BigDecimal("100.00"), new BigDecimal("1074.02"),
            new BigDecimal("313.00"), BigDecimal.ZERO, "REGULAR");
    }

    @Test
    void aPaymentLandsInBothStoresAndKeepsThemReconciled() {
        String paymentId = nextPaymentId();

        paymentPosting.post(request(paymentId));

        assertThat(payments.findByLegacyId(paymentId)).isPresent();
        assertThat(legacyPayments.findById(paymentId)).isPresent();
        assertThat(reconciliation.isFullyReconciled()).isTrue();
    }

    /** The mirrored row must be readable by the untouched legacy code path, in its own format. */
    @Test
    void theMirroredLegacyRowKeepsTheLegacyStringFormats() {
        String paymentId = nextPaymentId();

        paymentPosting.post(request(paymentId));

        var legacy = legacyPayments.findById(paymentId).orElseThrow();
        assertThat(legacy.getPaymentDate()).isEqualTo("03/01/2024");
        assertThat(legacy.getStatusCode()).isEqualTo("PST");
        assertThat(legacy.getTypeCode()).isEqualTo("REG");
    }
}
