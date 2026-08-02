package com.workshop.loanservice.migration;

import com.workshop.loanservice.config.MigrationProperties;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.modern.ProductCatalog;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyChunkSource;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Backfills the modern schema from the legacy CDW tables.
 *
 * <p>Three properties matter more than speed here:
 * <ul>
 *   <li><b>Idempotent</b> — every row is keyed by its legacy natural key, so a re-run after a
 *       partial failure skips what already landed instead of duplicating it.</li>
 *   <li><b>Chunked</b> — each chunk is its own transaction, so a 500k-row backfill never holds one
 *       long transaction, never grows an unbounded persistence context, and can resume.</li>
 *   <li><b>Loud about bad data</b> — an unparseable row is rejected with a reason, never coerced to
 *       zero. In strict mode a single rejection fails the run.</li>
 * </ul>
 *
 * <p>Order is forced by the foreign keys: borrowers and products first, then accounts, then
 * payments.
 */
@Service
public class LegacyToModernMigrationService {

    private static final Logger log = LoggerFactory.getLogger(LegacyToModernMigrationService.class);

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyAccounts;
    private final LegacyPaymentRepository legacyPayments;
    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository accounts;
    private final PaymentRepository payments;
    private final LegacyValueParser parser;
    private final CodeTranslator codes;
    private final ProductCatalog productCatalog;
    private final MigrationProperties properties;
    private final EntityManagerFactory legacyEntityManagerFactory;
    private final TransactionTemplate modernTx;
    private final TransactionTemplate legacyTx;

    public LegacyToModernMigrationService(LegacyBorrowerRepository legacyBorrowers,
                                          LegacyLoanProductRepository legacyProducts,
                                          LegacyLoanAccountRepository legacyAccounts,
                                          LegacyPaymentRepository legacyPayments,
                                          BorrowerRepository borrowers,
                                          LoanProductRepository products,
                                          LoanAccountRepository accounts,
                                          PaymentRepository payments,
                                          LegacyValueParser parser,
                                          CodeTranslator codes,
                                          ProductCatalog productCatalog,
                                          MigrationProperties properties,
                                          @Qualifier("legacyEntityManagerFactory") EntityManagerFactory legacyEntityManagerFactory,
                                          @Qualifier("modernTransactionManager") PlatformTransactionManager txManager,
                                          @Qualifier("legacyTransactionManager") PlatformTransactionManager legacyTxManager) {
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyAccounts = legacyAccounts;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.accounts = accounts;
        this.payments = payments;
        this.parser = parser;
        this.codes = codes;
        this.productCatalog = productCatalog;
        this.properties = properties;
        this.legacyEntityManagerFactory = legacyEntityManagerFactory;
        this.modernTx = new TransactionTemplate(txManager);
        this.modernTx.setTimeout(properties.getChunkTimeoutSeconds());
        // The extract side is one long read-only scan; only the write side is chunked and timed out.
        this.legacyTx = new TransactionTemplate(legacyTxManager);
        this.legacyTx.setReadOnly(true);
    }

    public MigrationReport migrate() {
        Instant start = Instant.now();
        MigrationReport report = new MigrationReport();
        log.info("Migration started mode={} chunkSize={}", properties.getMode(), properties.getChunkSize());

        migrateBorrowers(report);
        migrateProducts(report);
        migrateLoanAccounts(report);
        migratePayments(report);

        report.setDuration(Duration.between(start, Instant.now()));
        log.info("Migration finished {}", report.summary());

        if (report.hasRejections()) {
            report.getRejections().forEach(r ->
                log.warn("Rejected {} {}: {}", r.table(), r.legacyId(), r.reason()));
            if (properties.isStrict()) {
                throw new MigrationFailedException(report);
            }
        }
        return report;
    }

    private void migrateBorrowers(MigrationReport report) {
        forEachChunk(legacyBorrowers, chunk -> {
            List<String> ids = chunk.stream().map(LegacyBorrower::getBorrowerId).toList();
            Map<String, Borrower> existing = index(borrowers.findByExternalIdIn(ids), Borrower::getExternalId);

            List<Borrower> batch = new ArrayList<>();
            for (LegacyBorrower legacy : chunk) {
                if (existing.containsKey(legacy.getBorrowerId())) {
                    report.addSkipped("borrowers", 1);
                    continue;
                }
                try {
                    batch.add(toBorrower(legacy));
                } catch (LegacyValueParseException e) {
                    report.addRejection("borrowers", legacy.getBorrowerId(), e.getMessage());
                }
            }
            borrowers.saveAll(batch);
            report.addRead("borrowers", chunk.size());
            report.addWritten("borrowers", batch.size());
        });
    }

    private void migrateProducts(MigrationReport report) {
        forEachChunk(legacyProducts, chunk -> {
            List<LoanProduct> batch = new ArrayList<>();
            for (LegacyLoanProduct legacy : chunk) {
                if (products.findByCode(legacy.getProductCode()).isPresent()) {
                    report.addSkipped("loan_products", 1);
                    continue;
                }
                try {
                    batch.add(toProduct(legacy));
                } catch (LegacyValueParseException e) {
                    report.addRejection("loan_products", legacy.getProductCode(), e.getMessage());
                }
            }
            products.saveAll(batch);
            productCatalog.invalidate();
            report.addRead("loan_products", chunk.size());
            report.addWritten("loan_products", batch.size());
        });
    }

    private void migrateLoanAccounts(MigrationReport report) {
        forEachChunk(legacyAccounts, chunk -> {
            List<String> accountNumbers = chunk.stream().map(LegacyLoanAccount::getLoanAccountNumber).toList();
            Map<String, LoanAccount> existing =
                index(accounts.findByAccountNumberIn(accountNumbers), LoanAccount::getAccountNumber);
            Map<String, Borrower> borrowersByExternalId = index(
                borrowers.findByExternalIdIn(chunk.stream().map(LegacyLoanAccount::getBorrowerId).distinct().toList()),
                Borrower::getExternalId);
            Map<String, Long> productIdsByCode = productCatalog.idsByCode();

            List<LoanAccount> batch = new ArrayList<>();
            for (LegacyLoanAccount legacy : chunk) {
                if (existing.containsKey(legacy.getLoanAccountNumber())) {
                    report.addSkipped("loan_accounts", 1);
                    continue;
                }
                Borrower borrower = borrowersByExternalId.get(legacy.getBorrowerId());
                Long productId = productIdsByCode.get(legacy.getProductCode());
                if (borrower == null || productId == null) {
                    // The legacy schema has no foreign keys, so dangling references are expected.
                    report.addRejection("loan_accounts", legacy.getLoanAccountNumber(),
                        borrower == null
                            ? "unresolved borrower " + legacy.getBorrowerId()
                            : "unresolved product " + legacy.getProductCode());
                    continue;
                }
                try {
                    batch.add(toLoanAccount(legacy, borrower, productCatalog.reference(productId)));
                } catch (LegacyValueParseException e) {
                    report.addRejection("loan_accounts", legacy.getLoanAccountNumber(), e.getMessage());
                }
            }
            accounts.saveAll(batch);
            report.addRead("loan_accounts", chunk.size());
            report.addWritten("loan_accounts", batch.size());
        });
    }

    private void migratePayments(MigrationReport report) {
        forEachChunk(legacyPayments, chunk -> {
            List<String> legacyIds = chunk.stream().map(LegacyPayment::getPaymentSequenceNumber).toList();
            Map<String, Payment> existing = index(payments.findByLegacyIdIn(legacyIds), Payment::getLegacyId);
            Map<String, LoanAccount> accountsByNumber = index(
                accounts.findByAccountNumberIn(
                    chunk.stream().map(LegacyPayment::getLoanAccountNumber).distinct().toList()),
                LoanAccount::getAccountNumber);

            List<Payment> batch = new ArrayList<>();
            for (LegacyPayment legacy : chunk) {
                if (existing.containsKey(legacy.getPaymentSequenceNumber())) {
                    report.addSkipped("payments", 1);
                    continue;
                }
                LoanAccount account = accountsByNumber.get(legacy.getLoanAccountNumber());
                if (account == null) {
                    report.addRejection("payments", legacy.getPaymentSequenceNumber(),
                        "unresolved loan account " + legacy.getLoanAccountNumber());
                    continue;
                }
                try {
                    batch.add(toPayment(legacy, account));
                } catch (LegacyValueParseException e) {
                    report.addRejection("payments", legacy.getPaymentSequenceNumber(), e.getMessage());
                }
            }
            payments.saveAll(batch);
            report.addRead("payments", chunk.size());
            report.addWritten("payments", batch.size());
        });
    }

    /**
     * Streams the legacy table once and hands it to {@code work} in chunks, each in its own modern
     * transaction.
     *
     * <p>One forward scan rather than repeated paged queries: offset paging re-reads and discards
     * everything already migrated, which on the 2M-row payment table turned the tail of a 500k-loan
     * backfill into seconds per chunk, and keyset paging would reorder the rows. The scan is
     * unordered, so the modern ids land in the warehouse's physical row order - the order the frozen
     * v1 list endpoints have always returned.
     *
     * <p>The legacy persistence context is cleared after every chunk. Without that, a single read
     * transaction over 2M rows would retain all of them.
     */
    private <T> void forEachChunk(LegacyChunkSource<T> repository,
                                  java.util.function.Consumer<List<T>> work) {
        legacyTx.executeWithoutResult(status -> {
            List<T> chunk = new ArrayList<>(properties.getChunkSize());
            try (Stream<T> rows = repository.streamAll()) {
                for (T row : (Iterable<T>) rows::iterator) {
                    chunk.add(row);
                    if (chunk.size() == properties.getChunkSize()) {
                        flushChunk(chunk, work);
                    }
                }
            }
            if (!chunk.isEmpty()) {
                flushChunk(chunk, work);
            }
        });
    }

    private <T> void flushChunk(List<T> chunk, java.util.function.Consumer<List<T>> work) {
        List<T> batch = List.copyOf(chunk);
        modernTx.executeWithoutResult(status -> work.accept(batch));
        chunk.clear();
        EntityManagerFactoryUtils.getTransactionalEntityManager(legacyEntityManagerFactory).clear();
    }

    private static <T> Map<String, T> index(Collection<T> values, Function<T, String> key) {
        Map<String, T> map = new HashMap<>();
        values.forEach(v -> map.put(key.apply(v), v));
        return map;
    }

    // =========================================================================
    // Legacy row -> modern entity
    // =========================================================================

    Borrower toBorrower(LegacyBorrower legacy) {
        Borrower b = new Borrower();
        b.setExternalId(legacy.getBorrowerId());
        b.setFirstName(legacy.getFirstName());
        b.setLastName(legacy.getLastName());
        b.setMiddleInitial(legacy.getMiddleInitial());
        // Preserved verbatim: re-hashing would break every downstream system that matches on it.
        b.setSsnHash(legacy.getSsnEncrypted());
        b.setDateOfBirth(parser.parseDate(legacy.getDateOfBirth(), "BORR_DOB_DT"));
        b.setAddressLine1(legacy.getAddressLine1());
        b.setAddressLine2(legacy.getAddressLine2());
        b.setCity(legacy.getCity());
        b.setState(legacy.getStateCode());
        b.setZipCode(legacy.getZipCode());
        b.setPhone(legacy.getPhoneNumber());
        b.setEmail(legacy.getEmail());
        b.setCreditScore(parser.parseInteger(legacy.getCreditScore(), "BORR_CRDT_SCR"));
        b.setEmploymentStatus(legacy.getEmploymentStatus());
        b.setAnnualIncome(parser.parseAmount(legacy.getAnnualIncome(), "BORR_ANN_INCM"));
        b.setStatus(codes.borrowerStatus(legacy.getStatusCode()));
        b.setCreatedAt(parser.parseTimestamp(legacy.getCreatedDate(), "BORR_CRET_DT"));
        b.setUpdatedAt(parser.parseTimestamp(legacy.getUpdatedDate(), "BORR_UPDT_DT"));
        // BORR_REC_TYP is intentionally dropped: a warehouse artefact with no business meaning.
        return b;
    }

    LoanProduct toProduct(LegacyLoanProduct legacy) {
        LoanProduct p = new LoanProduct();
        p.setCode(legacy.getProductCode());
        p.setName(legacy.getDescription());
        p.setType(legacy.getTypeCode());
        p.setTermMonths(parser.parseInteger(legacy.getTermMonths(), "PROD_TERM_MOS"));
        p.setRateType(legacy.getRateType());
        p.setMinAmount(parser.parseAmount(legacy.getMinAmount(), "PROD_MIN_AMT"));
        p.setMaxAmount(parser.parseAmount(legacy.getMaxAmount(), "PROD_MAX_AMT"));
        p.setActive(codes.productActive(legacy.getStatusCode()));
        p.setEffectiveDate(parser.parseDate(legacy.getEffectiveDate(), "PROD_EFF_DT"));
        p.setExpirationDate(parser.parseDate(legacy.getExpirationDate(), "PROD_EXP_DT"));
        return p;
    }

    LoanAccount toLoanAccount(LegacyLoanAccount legacy, Borrower borrower, LoanProduct product) {
        LoanAccount a = new LoanAccount();
        a.setAccountNumber(legacy.getLoanAccountNumber());
        a.setBorrower(borrower);
        a.setProduct(product);
        // BORR_FST_NM / BORR_LST_NM / BORR_SSN_LST4 are dropped: that is the denormalization the
        // migration exists to remove. They now come from the borrower row through the FK.
        a.setOriginalAmount(parser.parseAmount(legacy.getOriginalAmount(), "LN_ORIG_AMT"));
        a.setCurrentBalance(parser.parseAmount(legacy.getCurrentBalance(), "LN_CURR_BAL"));
        a.setInterestRate(parser.parseDecimal(legacy.getInterestRate(), "LN_INT_RT"));
        a.setTermMonths(parser.parseInteger(legacy.getTermMonths(), "LN_TERM_MOS"));
        a.setMonthlyPayment(parser.parseAmount(legacy.getMonthlyPayment(), "LN_PMT_AMT"));
        a.setOriginationDate(parser.parseDate(legacy.getOriginationDate(), "LN_ORIG_DT"));
        a.setMaturityDate(parser.parseDate(legacy.getMaturityDate(), "LN_MAT_DT"));
        a.setFirstPaymentDate(parser.parseDate(legacy.getFirstPaymentDate(), "LN_1ST_PMT_DT"));
        a.setNextPaymentDate(parser.parseDate(legacy.getNextPaymentDate(), "LN_NXT_PMT_DT"));
        a.setStatus(codes.loanStatus(legacy.getStatusCode()));
        a.setDelinquencyDays(parser.parseInteger(legacy.getDelinquencyDays(), "LN_DLQ_DAYS"));
        a.setEscrowBalance(parser.parseAmount(legacy.getEscrowBalance(), "LN_ESCROW_BAL"));
        a.setLtvPercent(parser.parseDecimal(legacy.getLtvPercent(), "LN_LTV_PCT"));
        a.setPropertyAddress(legacy.getPropertyAddress());
        a.setPropertyCity(legacy.getPropertyCity());
        a.setPropertyState(legacy.getPropertyState());
        a.setPropertyZip(legacy.getPropertyZip());
        a.setPropertyType(codes.propertyType(legacy.getPropertyType()));
        a.setAppraisedValue(parser.parseAmount(legacy.getAppraisedValue(), "PROP_APRS_VAL"));
        a.setCreatedAt(parser.parseTimestamp(legacy.getCreatedDate(), "LN_CRET_DT"));
        a.setUpdatedAt(parser.parseTimestamp(legacy.getUpdatedDate(), "LN_UPDT_DT"));
        return a;
    }

    Payment toPayment(LegacyPayment legacy, LoanAccount account) {
        Payment p = new Payment();
        p.setLegacyId(legacy.getPaymentSequenceNumber());
        p.setLoanAccount(account);
        p.setPaymentDate(parser.parseDate(legacy.getPaymentDate(), "PMT_DT"));
        p.setTotalAmount(parser.parseAmount(legacy.getTotalAmount(), "PMT_AMT"));
        p.setPrincipalAmount(parser.parseAmount(legacy.getPrincipalAmount(), "PMT_PRIN_AMT"));
        p.setInterestAmount(parser.parseAmount(legacy.getInterestAmount(), "PMT_INT_AMT"));
        p.setEscrowAmount(parser.parseAmount(legacy.getEscrowAmount(), "PMT_ESCROW_AMT"));
        p.setLateFee(parser.parseAmount(legacy.getLateFee(), "PMT_LATE_FEE"));
        p.setType(codes.paymentType(legacy.getTypeCode()));
        p.setStatus(codes.paymentStatus(legacy.getStatusCode()));
        p.setReceivedDate(parser.parseDate(legacy.getReceivedDate(), "PMT_RECV_DT"));
        p.setProcessedDate(parser.parseDate(legacy.getProcessedDate(), "PMT_PROC_DT"));
        p.setCreatedAt(parser.parseTimestamp(legacy.getCreatedDate(), "PMT_CRET_DT"));
        p.setUpdatedAt(parser.parseTimestamp(legacy.getUpdatedDate(), "PMT_UPDT_DT"));
        return p;
    }
}
