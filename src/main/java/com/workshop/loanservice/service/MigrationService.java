package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.LoanProduct;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import com.workshop.loanservice.service.migration.LegacyCodeMapper;
import com.workshop.loanservice.service.migration.LegacyValueParser;
import com.workshop.loanservice.service.migration.MigrationException;
import com.workshop.loanservice.service.migration.MigrationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static com.workshop.loanservice.service.migration.LegacyValueParser.optionalAmount;
import static com.workshop.loanservice.service.migration.LegacyValueParser.optionalDate;
import static com.workshop.loanservice.service.migration.LegacyValueParser.optionalInteger;
import static com.workshop.loanservice.service.migration.LegacyValueParser.optionalText;
import static com.workshop.loanservice.service.migration.LegacyValueParser.optionalTimestamp;
import static com.workshop.loanservice.service.migration.LegacyValueParser.requireAmount;
import static com.workshop.loanservice.service.migration.LegacyValueParser.requireDate;
import static com.workshop.loanservice.service.migration.LegacyValueParser.requireInteger;
import static com.workshop.loanservice.service.migration.LegacyValueParser.requireText;

/**
 * Copies every legacy CDW record into the modern normalized schema, applying the
 * transformations in data/mappings/column_mappings.md.
 *
 * <p>Tables are processed in FK-safe order (borrowers, loan_products, loan_accounts, payments).
 * Records that cannot be transformed or whose parent row is missing are quarantined with a
 * logged reason rather than inserted with defaulted values. The run is idempotent: rows whose
 * natural key (borrower external_id, product code, loan account_number, payment legacy_payment_id)
 * already exists in the modern schema are skipped. Runs are serialized within this JVM, and the
 * UNIQUE constraints on those natural keys guard against duplicates from any other writer.
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    public static final String TABLE_BORROWERS = "borrowers";
    public static final String TABLE_LOAN_PRODUCTS = "loan_products";
    public static final String TABLE_LOAN_ACCOUNTS = "loan_accounts";
    public static final String TABLE_PAYMENTS = "payments";

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyLoanAccounts;
    private final LegacyPaymentRepository legacyPayments;
    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository loanAccounts;
    private final PaymentRepository payments;
    private final TransactionTemplate transaction;
    private final ReentrantLock runLock = new ReentrantLock();

    public MigrationService(LegacyBorrowerRepository legacyBorrowers,
                            LegacyLoanProductRepository legacyProducts,
                            LegacyLoanAccountRepository legacyLoanAccounts,
                            LegacyPaymentRepository legacyPayments,
                            BorrowerRepository borrowers,
                            LoanProductRepository products,
                            LoanAccountRepository loanAccounts,
                            PaymentRepository payments,
                            PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyLoanAccounts = legacyLoanAccounts;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.loanAccounts = loanAccounts;
        this.payments = payments;
    }

    /**
     * Runs the whole migration in a single transaction. The lock is held until that
     * transaction has committed, so overlapping calls (startup runner + admin endpoint,
     * or two concurrent POSTs) cannot both pass the read-before-write duplicate checks.
     */
    public MigrationSummary migrate() {
        runLock.lock();
        try {
            return transaction.execute(status -> {
                MigrationSummary summary = new MigrationSummary();
                migrateBorrowers(summary);
                migrateLoanProducts(summary);
                migrateLoanAccounts(summary);
                migratePayments(summary);
                payments.flush();
                log.info("Legacy -> modern migration finished: {} quarantined, tables={}",
                        summary.getQuarantinedCount(), summary.getTables());
                return summary;
            });
        } finally {
            runLock.unlock();
        }
    }

    private void migrateBorrowers(MigrationSummary summary) {
        List<LegacyBorrower> legacy = legacyBorrowers.findAll();
        int inserted = 0;
        int skipped = 0;
        for (LegacyBorrower src : legacy) {
            String id = src.getBorrowerId();
            try {
                String externalId = requireText(id, id, "BORR_ID");
                if (borrowers.findByExternalId(externalId).isPresent()) {
                    skipped++;
                    continue;
                }
                borrowers.save(toBorrower(src));
                inserted++;
            } catch (MigrationException e) {
                quarantine(summary, TABLE_BORROWERS, id, e);
            }
        }
        summary.addTable(TABLE_BORROWERS, legacy.size(), inserted, skipped);
    }

    private void migrateLoanProducts(MigrationSummary summary) {
        List<LegacyLoanProduct> legacy = legacyProducts.findAll();
        int inserted = 0;
        int skipped = 0;
        for (LegacyLoanProduct src : legacy) {
            String id = src.getProductCode();
            try {
                String code = requireText(id, id, "PROD_CD");
                if (products.findByCode(code).isPresent()) {
                    skipped++;
                    continue;
                }
                products.save(toLoanProduct(src));
                inserted++;
            } catch (MigrationException e) {
                quarantine(summary, TABLE_LOAN_PRODUCTS, id, e);
            }
        }
        summary.addTable(TABLE_LOAN_PRODUCTS, legacy.size(), inserted, skipped);
    }

    private void migrateLoanAccounts(MigrationSummary summary) {
        List<LegacyLoanAccount> legacy = legacyLoanAccounts.findAll();
        int inserted = 0;
        int skipped = 0;
        for (LegacyLoanAccount src : legacy) {
            String id = src.getLoanAccountNumber();
            try {
                String accountNumber = requireText(id, id, "LN_ACCT_NBR");
                if (loanAccounts.findByAccountNumber(accountNumber).isPresent()) {
                    skipped++;
                    continue;
                }
                Borrower borrower = borrowers.findByExternalId(requireText(src.getBorrowerId(), id, "BORR_ID"))
                        .orElseThrow(() -> new MigrationException(id, "BORR_ID",
                                "no modern borrower with external_id '" + src.getBorrowerId() + "'"));
                LoanProduct product = products.findByCode(requireText(src.getProductCode(), id, "PROD_CD"))
                        .orElseThrow(() -> new MigrationException(id, "PROD_CD",
                                "no modern loan_product with code '" + src.getProductCode() + "'"));
                loanAccounts.save(toLoanAccount(src, borrower, product));
                inserted++;
            } catch (MigrationException e) {
                quarantine(summary, TABLE_LOAN_ACCOUNTS, id, e);
            }
        }
        summary.addTable(TABLE_LOAN_ACCOUNTS, legacy.size(), inserted, skipped);
    }

    private void migratePayments(MigrationSummary summary) {
        List<LegacyPayment> legacy = legacyPayments.findAll();
        int inserted = 0;
        int skipped = 0;
        Set<String> seenIds = new HashSet<>();
        for (LegacyPayment src : legacy) {
            String id = src.getPaymentSequenceNumber();
            try {
                String legacyId = requireText(id, id, "PMT_SEQ_NBR");
                if (!seenIds.add(legacyId) || payments.findByLegacyPaymentId(legacyId).isPresent()) {
                    skipped++;
                    continue;
                }
                Optional<LoanAccount> account =
                        loanAccounts.findByAccountNumber(requireText(src.getLoanAccountNumber(), id, "LN_ACCT_NBR"));
                if (account.isEmpty()) {
                    throw new MigrationException(id, "LN_ACCT_NBR",
                            "no modern loan_account with account_number '" + src.getLoanAccountNumber() + "'");
                }
                payments.save(toPayment(src, account.get()));
                inserted++;
            } catch (MigrationException e) {
                quarantine(summary, TABLE_PAYMENTS, id, e);
            }
        }
        summary.addTable(TABLE_PAYMENTS, legacy.size(), inserted, skipped);
    }

    private void quarantine(MigrationSummary summary, String table, String recordId, MigrationException e) {
        log.warn("Quarantined {} record {}: {}", table, recordId, e.getMessage());
        summary.quarantine(table, Objects.toString(recordId, "<null id>"), e.getMessage());
    }

    static Borrower toBorrower(LegacyBorrower src) {
        String id = src.getBorrowerId();
        Borrower b = new Borrower();
        b.setExternalId(requireText(id, id, "BORR_ID"));
        b.setFirstName(requireText(src.getFirstName(), id, "BORR_FST_NM"));
        b.setLastName(requireText(src.getLastName(), id, "BORR_LST_NM"));
        b.setMiddleInitial(optionalText(src.getMiddleInitial()));
        b.setSsnHash(optionalText(src.getSsnEncrypted()));
        b.setDateOfBirth(optionalDate(src.getDateOfBirth(), id, "BORR_DOB_DT"));
        b.setAddressLine1(optionalText(src.getAddressLine1()));
        b.setAddressLine2(optionalText(src.getAddressLine2()));
        b.setCity(optionalText(src.getCity()));
        b.setState(optionalText(src.getStateCode()));
        b.setZipCode(optionalText(src.getZipCode()));
        b.setPhone(optionalText(src.getPhoneNumber()));
        b.setEmail(optionalText(src.getEmail()));
        b.setCreditScore(optionalInteger(src.getCreditScore(), id, "BORR_CRDT_SCR"));
        b.setEmploymentStatus(optionalText(src.getEmploymentStatus()));
        b.setAnnualIncome(optionalAmount(src.getAnnualIncome(), id, "BORR_ANN_INCM", 12, 2));
        b.setStatus(LegacyCodeMapper.borrowerStatus(src.getStatusCode(), id));
        b.setCreatedAt(optionalTimestamp(src.getCreatedDate(), id, "BORR_CRET_DT"));
        b.setUpdatedAt(optionalTimestamp(src.getUpdatedDate(), id, "BORR_UPDT_DT"));
        return b;
    }

    static LoanProduct toLoanProduct(LegacyLoanProduct src) {
        String id = src.getProductCode();
        LoanProduct p = new LoanProduct();
        p.setCode(requireText(id, id, "PROD_CD"));
        p.setName(requireText(src.getDescription(), id, "PROD_DESC_TXT"));
        p.setType(requireText(src.getTypeCode(), id, "PROD_TYP_CD"));
        p.setTermMonths(requireInteger(src.getTermMonths(), id, "PROD_TERM_MOS"));
        p.setRateType(requireText(src.getRateType(), id, "PROD_RT_TYP"));
        p.setMinAmount(optionalAmount(src.getMinAmount(), id, "PROD_MIN_AMT", 12, 2));
        p.setMaxAmount(optionalAmount(src.getMaxAmount(), id, "PROD_MAX_AMT", 12, 2));
        p.setIsActive(LegacyCodeMapper.productActive(src.getStatusCode(), id));
        p.setEffectiveDate(optionalDate(src.getEffectiveDate(), id, "PROD_EFF_DT"));
        p.setExpirationDate(optionalDate(src.getExpirationDate(), id, "PROD_EXP_DT"));
        return p;
    }

    static LoanAccount toLoanAccount(LegacyLoanAccount src, Borrower borrower, LoanProduct product) {
        String id = src.getLoanAccountNumber();
        LoanAccount a = new LoanAccount();
        a.setAccountNumber(requireText(id, id, "LN_ACCT_NBR"));
        a.setBorrower(borrower);
        a.setProduct(product);
        a.setOriginalAmount(requireAmount(src.getOriginalAmount(), id, "LN_ORIG_AMT", 12, 2));
        a.setCurrentBalance(requireAmount(src.getCurrentBalance(), id, "LN_CURR_BAL", 12, 2));
        a.setInterestRate(requireAmount(src.getInterestRate(), id, "LN_INT_RT", 5, 3));
        a.setTermMonths(requireInteger(src.getTermMonths(), id, "LN_TERM_MOS"));
        a.setMonthlyPayment(requireAmount(src.getMonthlyPayment(), id, "LN_PMT_AMT", 10, 2));
        a.setOriginationDate(requireDate(src.getOriginationDate(), id, "LN_ORIG_DT"));
        a.setMaturityDate(requireDate(src.getMaturityDate(), id, "LN_MAT_DT"));
        a.setFirstPaymentDate(optionalDate(src.getFirstPaymentDate(), id, "LN_1ST_PMT_DT"));
        a.setNextPaymentDate(optionalDate(src.getNextPaymentDate(), id, "LN_NXT_PMT_DT"));
        a.setStatus(LegacyCodeMapper.loanStatus(src.getStatusCode(), id));
        a.setDelinquencyDays(requireInteger(src.getDelinquencyDays(), id, "LN_DLQ_DAYS"));
        a.setEscrowBalance(requireAmount(src.getEscrowBalance(), id, "LN_ESCROW_BAL", 10, 2));
        a.setLtvPercent(optionalAmount(src.getLtvPercent(), id, "LN_LTV_PCT", 5, 2));
        a.setPropertyAddress(optionalText(src.getPropertyAddress()));
        a.setPropertyCity(optionalText(src.getPropertyCity()));
        a.setPropertyState(optionalText(src.getPropertyState()));
        a.setPropertyZip(optionalText(src.getPropertyZip()));
        a.setPropertyType(LegacyValueParser.isBlank(src.getPropertyType())
                ? null : LegacyCodeMapper.propertyType(src.getPropertyType(), id));
        a.setAppraisedValue(optionalAmount(src.getAppraisedValue(), id, "PROP_APRS_VAL", 12, 2));
        a.setCreatedAt(optionalTimestamp(src.getCreatedDate(), id, "LN_CRET_DT"));
        a.setUpdatedAt(optionalTimestamp(src.getUpdatedDate(), id, "LN_UPDT_DT"));
        return a;
    }

    static Payment toPayment(LegacyPayment src, LoanAccount account) {
        String id = src.getPaymentSequenceNumber();
        Payment p = new Payment();
        p.setLoanAccount(account);
        p.setLegacyPaymentId(requireText(id, id, "PMT_SEQ_NBR"));
        p.setPaymentDate(requireDate(src.getPaymentDate(), id, "PMT_DT"));
        p.setTotalAmount(requireAmount(src.getTotalAmount(), id, "PMT_AMT", 10, 2));
        p.setPrincipalAmount(optionalAmount(src.getPrincipalAmount(), id, "PMT_PRIN_AMT", 10, 2));
        p.setInterestAmount(optionalAmount(src.getInterestAmount(), id, "PMT_INT_AMT", 10, 2));
        p.setEscrowAmount(optionalAmount(src.getEscrowAmount(), id, "PMT_ESCROW_AMT", 10, 2));
        p.setLateFee(optionalAmount(src.getLateFee(), id, "PMT_LATE_FEE", 10, 2));
        p.setType(LegacyCodeMapper.paymentType(src.getTypeCode(), id));
        p.setStatus(LegacyCodeMapper.paymentStatus(src.getStatusCode(), id));
        p.setReceivedDate(optionalDate(src.getReceivedDate(), id, "PMT_RECV_DT"));
        p.setProcessedDate(optionalDate(src.getProcessedDate(), id, "PMT_PROC_DT"));
        p.setCreatedAt(optionalTimestamp(src.getCreatedDate(), id, "PMT_CRET_DT"));
        p.setUpdatedAt(optionalTimestamp(src.getUpdatedDate(), id, "PMT_UPDT_DT"));
        return p;
    }
}
