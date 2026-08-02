package com.workshop.loanservice.service;

import com.workshop.loanservice.config.ReadSourceProperties;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Internal write path. There is deliberately no HTTP endpoint in front of it: the workshop's
 * concurrency target (~2,000 writes/min/pod) is exercised from integration and load tests, and an
 * unauthenticated public write API would be a far larger risk than anything it demonstrates.
 *
 * <p><b>Concurrency design.</b> Posting a payment inserts a row and decrements the account balance.
 * The decrement is the contended operation, so it is done as a single conditional UPDATE guarded by
 * the {@code @Version} column rather than a read-modify-write: two concurrent posts can never
 * silently overwrite each other's balance, because the second one's version predicate fails and the
 * whole attempt is retried. Nothing is held across the retry, and the transaction spans two
 * statements, so lock windows stay in the sub-millisecond range and deadlocks have no cycle to form:
 * every transaction touches {@code loan_accounts} before {@code payments}, in that order.
 *
 * <p>Isolation stays at the driver default (READ_COMMITTED on any production engine). The lost
 * update the workload is actually exposed to is prevented by the version predicate, not by
 * isolation, so paying for SERIALIZABLE would buy contention and no additional safety.
 */
@Service
public class PaymentPostingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentPostingService.class);

    /** Bounded: a request that cannot win in this many attempts is shedding load, not unlucky. */
    private static final int MAX_ATTEMPTS = 8;

    /**
     * After this many optimistic conflicts the row is not merely busy, it is hot, and retrying
     * optimistically just burns work. From here on the attempt takes a row lock and waits its turn.
     */
    private static final int PESSIMISTIC_AFTER_ATTEMPT = 2;

    private static final long BASE_BACKOFF_NANOS = 200_000L;

    private final LoanAccountRepository accounts;
    private final PaymentRepository payments;
    private final LegacyPaymentRepository legacyPayments;
    private final ReadSourceProperties properties;
    private final TransactionTemplate modernTx;
    private final TransactionTemplate legacyTx;
    private final Counter retries;
    private final Counter failures;
    private final Timer postTimer;

    public PaymentPostingService(LoanAccountRepository accounts,
                                 PaymentRepository payments,
                                 LegacyPaymentRepository legacyPayments,
                                 ReadSourceProperties properties,
                                 @Qualifier("modernTransactionManager") PlatformTransactionManager modernTxManager,
                                 @Qualifier("legacyTransactionManager") PlatformTransactionManager legacyTxManager,
                                 MeterRegistry meterRegistry) {
        this.accounts = accounts;
        this.payments = payments;
        this.legacyPayments = legacyPayments;
        this.properties = properties;
        this.modernTx = new TransactionTemplate(modernTxManager);
        this.modernTx.setTimeout(5);
        this.legacyTx = new TransactionTemplate(legacyTxManager);
        this.legacyTx.setTimeout(5);
        this.retries = meterRegistry.counter("loanservice.payment.retry");
        this.failures = meterRegistry.counter("loanservice.payment.failed");
        this.postTimer = Timer.builder("loanservice.payment.post")
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    /**
     * Posts a payment and applies it to the balance.
     *
     * @return the id of the inserted modern payment row
     * @throws PaymentPostingException if the account does not exist, or the balance update kept
     *                                 losing to concurrent writers
     */
    public Long post(PaymentRequest request) {
        return postTimer.record(() -> postWithRetry(request));
    }

    private Long postWithRetry(PaymentRequest request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                boolean lockRow = attempt > PESSIMISTIC_AFTER_ATTEMPT;
                Long id = modernTx.execute(status -> insertAndApply(request, lockRow));
                if (properties.isDualWrite()) {
                    mirrorToLegacy(request);
                }
                return id;
            } catch (OptimisticLockingFailureException | OptimisticLockException | CannotAcquireLockException e) {
                retries.increment();
                if (attempt == MAX_ATTEMPTS) {
                    failures.increment();
                    log.warn("Payment post gave up after {} attempts type={}", attempt, e.getClass().getSimpleName());
                    throw new PaymentPostingException("balance update contended");
                }
                backoff(attempt);
            } catch (DuplicateKeyException e) {
                // Unique legacy_id: this payment was already posted, so refusing it is what keeps the
                // write path idempotent rather than double-charging the borrower.
                failures.increment();
                throw new PaymentPostingException("duplicate payment");
            } catch (DataIntegrityViolationException e) {
                failures.increment();
                throw new PaymentPostingException("payment rejected by the database");
            }
        }
        throw new PaymentPostingException("balance update contended");
    }

    /**
     * @param lockRow take a pessimistic write lock on the account first. Optimistic control is right
     *                for the common case - most payments touch different loans - but under sustained
     *                contention on one loan it degenerates into livelock, where every writer keeps
     *                losing and no one makes progress. Escalating to a row lock converts that into a
     *                short queue, which is slower per request and finishes far sooner overall.
     */
    private Long insertAndApply(PaymentRequest request, boolean lockRow) {
        LoanAccount account = (lockRow
            ? accounts.findByAccountNumberForUpdate(request.accountNumber())
            : accounts.findByAccountNumber(request.accountNumber()))
            .orElseThrow(() -> new PaymentPostingException("unknown loan account"));
        Long accountId = account.getId();

        // Parent before child, always: a stable lock order is what keeps concurrent posts from
        // deadlocking against the migration's chunk writes.
        int updated = accounts.applyPaymentToBalance(accountId,
            account.getCurrentBalance().subtract(request.principalAmount()), account.getVersion());
        if (updated == 0) {
            throw new OptimisticLockingFailureException("loan_accounts version changed");
        }

        Payment payment = new Payment();
        payment.setLegacyId(request.paymentId());
        // The balance update cleared the persistence context, so the account is referenced by id
        // rather than by the now-detached instance.
        payment.setLoanAccount(accounts.getReferenceById(accountId));
        payment.setPaymentDate(request.paymentDate());
        payment.setTotalAmount(request.totalAmount());
        payment.setPrincipalAmount(request.principalAmount());
        payment.setInterestAmount(request.interestAmount());
        payment.setEscrowAmount(request.escrowAmount());
        payment.setLateFee(request.lateFee());
        payment.setType(request.type());
        payment.setStatus("POSTED");
        payment.setReceivedDate(request.paymentDate());
        payment.setProcessedDate(request.paymentDate());
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        return payments.save(payment).getId();
    }

    /**
     * Keeps the legacy store writable during the reconciliation window so a rollback to
     * {@code read-source=legacy} does not lose payments taken after the cutover.
     */
    private void mirrorToLegacy(PaymentRequest request) {
        legacyTx.executeWithoutResult(status -> {
            LegacyPayment legacy = new LegacyPayment();
            legacy.setPaymentSequenceNumber(request.paymentId());
            legacy.setLoanAccountNumber(request.accountNumber());
            legacy.setPaymentDate(V1Format.date(request.paymentDate()));
            legacy.setTotalAmount(request.totalAmount().toPlainString());
            legacy.setPrincipalAmount(request.principalAmount().toPlainString());
            legacy.setInterestAmount(request.interestAmount().toPlainString());
            legacy.setEscrowAmount(request.escrowAmount().toPlainString());
            legacy.setLateFee(request.lateFee().toPlainString());
            legacy.setTypeCode(legacyTypeCode(request.type()));
            legacy.setStatusCode("PST");
            legacy.setReceivedDate(V1Format.date(request.paymentDate()));
            legacy.setProcessedDate(V1Format.date(request.paymentDate()));
            legacy.setCreatedDate(V1Format.date(LocalDate.now()));
            legacy.setUpdatedDate(V1Format.date(LocalDate.now()));
            legacyPayments.save(legacy);
        });
    }

    private String legacyTypeCode(String modernType) {
        return switch (modernType) {
            case "REGULAR" -> "REG";
            case "EXTRA" -> "EXT";
            case "PARTIAL" -> "PRT";
            case "PREPAYMENT" -> "PRE";
            default -> modernType;
        };
    }

    /** Exponential backoff with jitter, so retrying writers do not resynchronise into a thundering herd. */
    private void backoff(int attempt) {
        long nanos = BASE_BACKOFF_NANOS * (1L << (attempt - 1));
        long jittered = ThreadLocalRandom.current().nextLong(nanos / 2, nanos + 1);
        try {
            Thread.sleep(jittered / 1_000_000L, (int) (jittered % 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentPostingException("interrupted");
        }
    }

    /** A payment to post. Amounts are already typed: no legacy string parsing on the write path. */
    public record PaymentRequest(String paymentId,
                                 String accountNumber,
                                 LocalDate paymentDate,
                                 BigDecimal totalAmount,
                                 BigDecimal principalAmount,
                                 BigDecimal interestAmount,
                                 BigDecimal escrowAmount,
                                 BigDecimal lateFee,
                                 String type) {
    }
}
