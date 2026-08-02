package com.workshop.loanservice.service;

import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The concurrency requirement, exercised rather than asserted in prose.
 *
 * <p>The interesting case is not throughput, it is correctness under contention: many threads paying
 * into the <em>same</em> loan is the worst case for a balance update, and a read-modify-write would
 * silently lose money here. The balance is therefore checked to the cent after the run - if a single
 * update were lost, the total would not add up.
 */
@SpringBootTest(properties = "loanservice.dual-write=false")
class PaymentPostingConcurrencyIT {

    private static final String ACCOUNT = "LN-2019-00142";
    private static final BigDecimal PRINCIPAL = new BigDecimal("100.00");
    private static final AtomicLong NEXT_ID = new AtomicLong();

    @Autowired
    private PaymentPostingService paymentPosting;
    @Autowired
    private LoanAccountRepository accounts;
    @Autowired
    private PaymentRepository payments;

    @Test
    void concurrentPaymentsIntoOneLoanNeverLoseAnUpdate() throws Exception {
        int threads = 16;
        int paymentsPerThread = 20;
        int total = threads * paymentsPerThread;
        BigDecimal balanceBefore = balance();
        long rowsBefore = payments.count();

        List<String> failures = runConcurrently(threads, total);

        assertThat(failures).isEmpty();
        assertThat(payments.count()).isEqualTo(rowsBefore + total);
        assertThat(balance()).isEqualByComparingTo(
            balanceBefore.subtract(PRINCIPAL.multiply(BigDecimal.valueOf(total))));
    }

    /**
     * A throughput floor, not a benchmark: 2,000 writes/min/pod is ~33/s, and this asserts the write
     * path clears that on the worst-case single-row contention with an in-memory database. Absolute
     * numbers from H2 are a lower bound for the design, not a production projection.
     */
    @Test
    void sustainsTheRequiredWriteRateUnderContention() throws Exception {
        int threads = 16;
        int total = 400;

        Instant start = Instant.now();
        List<String> failures = runConcurrently(threads, total);
        Duration elapsed = Duration.between(start, Instant.now());

        double perSecond = total / Math.max(elapsed.toMillis() / 1000.0, 0.001);
        assertThat(failures).isEmpty();
        assertThat(perSecond)
            .as("observed %.0f writes/s against the 33/s (2000/min) requirement", perSecond)
            .isGreaterThan(33.0);
    }

    @Test
    void unknownAccountIsRejectedWithoutTouchingTheLedger() {
        long before = payments.count();

        assertThat(catchThrowable(() -> paymentPosting.post(request("NOPE"))))
            .isInstanceOf(PaymentPostingException.class);
        assertThat(payments.count()).isEqualTo(before);
    }

    /** Re-posting the same payment id must not double-charge the borrower. */
    @Test
    void duplicatePaymentIdIsRejected() {
        PaymentPostingService.PaymentRequest request = request(ACCOUNT);
        paymentPosting.post(request);

        assertThat(catchThrowable(() -> paymentPosting.post(request)))
            .isInstanceOf(PaymentPostingException.class);
    }

    /** Collects the reasons, not just a count: a bare number turns a real defect into a mystery. */
    private List<String> runConcurrently(int threads, int total) throws Exception {
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = IntStream.range(0, total)
                .<Callable<Void>>mapToObj(i -> () -> {
                    try {
                        paymentPosting.post(request(ACCOUNT));
                    } catch (RuntimeException e) {
                        failures.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                    return null;
                })
                .toList();
            for (Future<Void> future : pool.invokeAll(tasks)) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    private BigDecimal balance() {
        return accounts.findByAccountNumber(ACCOUNT).map(LoanAccount::getCurrentBalance).orElseThrow();
    }

    /** Payment ids mirror the legacy sequence format, which is capped at 20 characters. */
    private PaymentPostingService.PaymentRequest request(String accountNumber) {
        return new PaymentPostingService.PaymentRequest(
            "PMT-T" + NEXT_ID.incrementAndGet(), accountNumber, LocalDate.now(),
            new BigDecimal("1487.02"), PRINCIPAL, new BigDecimal("1074.02"),
            new BigDecimal("313.00"), BigDecimal.ZERO, "REGULAR");
    }
}
