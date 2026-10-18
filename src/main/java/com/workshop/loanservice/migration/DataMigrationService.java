package com.workshop.loanservice.migration;

import static com.workshop.loanservice.migration.LegacyValueParser.parseDate;
import static com.workshop.loanservice.migration.LegacyValueParser.parseDecimal;
import static com.workshop.loanservice.migration.LegacyValueParser.parseInteger;
import static com.workshop.loanservice.migration.LegacyValueParser.parseTimestamp;
import static com.workshop.loanservice.migration.LegacyValueParser.require;
import static com.workshop.loanservice.migration.LegacyValueParser.trimToNull;

import com.workshop.loanservice.entity.legacy.LegacyBorrower;
import com.workshop.loanservice.entity.legacy.LegacyLoanAccount;
import com.workshop.loanservice.entity.legacy.LegacyLoanProduct;
import com.workshop.loanservice.entity.legacy.LegacyPayment;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.LoanProduct;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.legacy.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.legacy.LegacyPaymentRepository;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Copies every legacy CDW row into the modern schema, applying the transformations in
 * {@code data/mappings/column_mappings.md}, then reconciles row counts and amount totals.
 *
 * <p>The run is idempotent: rows whose business key (borrower external id, product code,
 * account number, payment sequence number) already exists in the modern schema are not inserted
 * again, but are compared field by field with the transformed legacy row; any difference is an error.
 * Rows with missing required values, malformed values, unknown codes or unresolvable foreign
 * keys are rejected and listed in the {@link MigrationReport} rather than aborting the run.
 */
@SuppressWarnings("deprecation")
@Service
public class DataMigrationService {

    static final String BORROWERS = "borrowers";
    static final String LOAN_PRODUCTS = "loan_products";
    static final String LOAN_ACCOUNTS = "loan_accounts";
    static final String PAYMENTS = "payments";

    private final LegacyBorrowerRepository legacyBorrowerRepository;
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    private final LegacyPaymentRepository legacyPaymentRepository;
    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    public DataMigrationService(LegacyBorrowerRepository legacyBorrowerRepository,
                                LegacyLoanProductRepository legacyLoanProductRepository,
                                LegacyLoanAccountRepository legacyLoanAccountRepository,
                                LegacyPaymentRepository legacyPaymentRepository,
                                BorrowerRepository borrowerRepository,
                                LoanProductRepository loanProductRepository,
                                LoanAccountRepository loanAccountRepository,
                                PaymentRepository paymentRepository) {
        this.legacyBorrowerRepository = legacyBorrowerRepository;
        this.legacyLoanProductRepository = legacyLoanProductRepository;
        this.legacyLoanAccountRepository = legacyLoanAccountRepository;
        this.legacyPaymentRepository = legacyPaymentRepository;
        this.borrowerRepository = borrowerRepository;
        this.loanProductRepository = loanProductRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Runs the migration in a single transaction. With {@code failOnError}, a failed validation throws
     * {@link MigrationFailedException} before commit so nothing is persisted; otherwise successfully
     * transformed rows are committed and failures are only reported.
     */
    @Transactional
    public MigrationReport migrate(boolean failOnError) {
        MigrationReport report = new MigrationReport();
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
        List<LegacyLoanAccount> legacyLoans = legacyLoanAccountRepository.findAll();
        List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();

        migrateRows(report, BORROWERS, legacyBorrowers, LegacyBorrower::getBorrowerId,
                borrowerRepository::findByExternalId, this::toBorrower, borrowerRepository::save);
        migrateRows(report, LOAN_PRODUCTS, legacyProducts, LegacyLoanProduct::getProductCode,
                loanProductRepository::findByCode, this::toLoanProduct, loanProductRepository::save);

        Map<String, Borrower> borrowersByExternalId = borrowerRepository.findAll().stream()
                .collect(Collectors.toMap(Borrower::getExternalId, Function.identity()));
        Map<String, LoanProduct> productsByCode = loanProductRepository.findAll().stream()
                .collect(Collectors.toMap(LoanProduct::getCode, Function.identity()));
        migrateRows(report, LOAN_ACCOUNTS, legacyLoans, LegacyLoanAccount::getLoanAccountNumber,
                loanAccountRepository::findByAccountNumber,
                legacy -> toLoanAccount(legacy, borrowersByExternalId, productsByCode, report),
                loanAccountRepository::save);

        Map<String, LoanAccount> loansByAccountNumber = loanAccountRepository.findAll().stream()
                .collect(Collectors.toMap(LoanAccount::getAccountNumber, Function.identity()));
        migrateRows(report, PAYMENTS, legacyPayments, LegacyPayment::getPaymentSequenceNumber,
                paymentRepository::findByExternalId,
                legacy -> toPayment(legacy, loansByAccountNumber), paymentRepository::save);

        reconcile(report, legacyBorrowers, legacyProducts, legacyLoans, legacyPayments);
        if (failOnError && !report.isSuccessful()) {
            throw new MigrationFailedException(report);
        }
        return report;
    }

    private <L, M> void migrateRows(MigrationReport report, String table, List<L> legacyRows,
                                    Function<L, String> businessKey, Function<String, Optional<M>> findExisting,
                                    Function<L, M> transform, Consumer<M> save) {
        MigrationReport.TableStats stats = report.table(table);
        Set<String> migratedKeys = new HashSet<>();
        for (L legacy : legacyRows) {
            stats.recordRead();
            String key = trimToNull(businessKey.apply(legacy));
            if (key == null) {
                stats.recordRejected();
                report.error(table + ": row with blank business key rejected");
                continue;
            }
            if (migratedKeys.contains(key)) {
                stats.recordDuplicate();
                continue;
            }
            try {
                M candidate = transform.apply(legacy);
                ModernEntityInspector.validateColumnConstraints(candidate);
                Optional<M> existing = findExisting.apply(key);
                if (existing.isPresent()) {
                    stats.recordDuplicate();
                    List<String> differences = ModernEntityInspector.differingFields(candidate, existing.get());
                    if (!differences.isEmpty()) {
                        report.error(table + " [" + key + "]: existing modern row differs from legacy: "
                                + String.join(", ", differences));
                    }
                } else {
                    save.accept(candidate);
                    stats.recordInserted();
                }
                migratedKeys.add(key);
            } catch (LegacyDataException e) {
                stats.recordRejected();
                report.error(table + " [" + key + "]: " + e.getMessage());
            }
        }
    }

    private Borrower toBorrower(LegacyBorrower legacy) {
        Borrower borrower = new Borrower();
        borrower.setExternalId(require("BORR_ID", legacy.getBorrowerId()));
        borrower.setFirstName(require("BORR_FST_NM", legacy.getFirstName()));
        borrower.setLastName(require("BORR_LST_NM", legacy.getLastName()));
        borrower.setMiddleInitial(trimToNull(legacy.getMiddleInitial()));
        borrower.setSsnHash(trimToNull(legacy.getSsnEncrypted()));
        borrower.setDateOfBirth(parseDate("BORR_DOB_DT", legacy.getDateOfBirth()));
        borrower.setAddressLine1(trimToNull(legacy.getAddressLine1()));
        borrower.setAddressLine2(trimToNull(legacy.getAddressLine2()));
        borrower.setCity(trimToNull(legacy.getCity()));
        borrower.setState(trimToNull(legacy.getStateCode()));
        borrower.setZipCode(trimToNull(legacy.getZipCode()));
        borrower.setPhone(trimToNull(legacy.getPhoneNumber()));
        borrower.setEmail(trimToNull(legacy.getEmail()));
        borrower.setCreditScore(parseInteger("BORR_CRDT_SCR", legacy.getCreditScore()));
        borrower.setEmploymentStatus(trimToNull(legacy.getEmploymentStatus()));
        borrower.setAnnualIncome(parseDecimal("BORR_ANN_INCM", legacy.getAnnualIncome()));
        borrower.setStatus(LegacyCodeMapper.borrowerStatus(legacy.getStatusCode()));
        borrower.setCreatedAt(timestampOrNow("BORR_CRET_DT", legacy.getCreatedDate()));
        borrower.setUpdatedAt(timestampOrNow("BORR_UPDT_DT", legacy.getUpdatedDate()));
        return borrower;
    }

    private LoanProduct toLoanProduct(LegacyLoanProduct legacy) {
        LoanProduct product = new LoanProduct();
        product.setCode(require("PROD_CD", legacy.getProductCode()));
        product.setName(require("PROD_DESC_TXT", legacy.getDescription()));
        product.setType(require("PROD_TYP_CD", legacy.getTypeCode()));
        product.setTermMonths(require("PROD_TERM_MOS", parseInteger("PROD_TERM_MOS", legacy.getTermMonths())));
        product.setRateType(require("PROD_RT_TYP", legacy.getRateType()));
        product.setMinAmount(parseDecimal("PROD_MIN_AMT", legacy.getMinAmount()));
        product.setMaxAmount(parseDecimal("PROD_MAX_AMT", legacy.getMaxAmount()));
        product.setActive(LegacyCodeMapper.productActive(legacy.getStatusCode()));
        product.setEffectiveDate(parseDate("PROD_EFF_DT", legacy.getEffectiveDate()));
        product.setExpirationDate(parseDate("PROD_EXP_DT", legacy.getExpirationDate()));
        return product;
    }

    private LoanAccount toLoanAccount(LegacyLoanAccount legacy, Map<String, Borrower> borrowersByExternalId,
                                      Map<String, LoanProduct> productsByCode, MigrationReport report) {
        String accountNumber = require("LN_ACCT_NBR", legacy.getLoanAccountNumber());
        Borrower borrower = resolve("BORR_ID", legacy.getBorrowerId(), borrowersByExternalId);
        LoanProduct product = resolve("PROD_CD", legacy.getProductCode(), productsByCode);
        warnOnDenormalizedNameDrift(legacy, borrower, report);

        LoanAccount account = new LoanAccount();
        account.setAccountNumber(accountNumber);
        account.setBorrower(borrower);
        account.setProduct(product);
        account.setOriginalAmount(require("LN_ORIG_AMT", parseDecimal("LN_ORIG_AMT", legacy.getOriginalAmount())));
        account.setCurrentBalance(require("LN_CURR_BAL", parseDecimal("LN_CURR_BAL", legacy.getCurrentBalance())));
        account.setInterestRate(require("LN_INT_RT", parseDecimal("LN_INT_RT", legacy.getInterestRate())));
        account.setTermMonths(require("LN_TERM_MOS", parseInteger("LN_TERM_MOS", legacy.getTermMonths())));
        account.setMonthlyPayment(require("LN_PMT_AMT", parseDecimal("LN_PMT_AMT", legacy.getMonthlyPayment())));
        account.setOriginationDate(require("LN_ORIG_DT", parseDate("LN_ORIG_DT", legacy.getOriginationDate())));
        account.setMaturityDate(require("LN_MAT_DT", parseDate("LN_MAT_DT", legacy.getMaturityDate())));
        account.setFirstPaymentDate(parseDate("LN_1ST_PMT_DT", legacy.getFirstPaymentDate()));
        account.setNextPaymentDate(parseDate("LN_NXT_PMT_DT", legacy.getNextPaymentDate()));
        account.setStatus(LegacyCodeMapper.loanStatus(legacy.getStatusCode()));
        account.setDelinquencyDays(Objects.requireNonNullElse(
                parseInteger("LN_DLQ_DAYS", legacy.getDelinquencyDays()), 0));
        account.setEscrowBalance(Objects.requireNonNullElse(
                parseDecimal("LN_ESCROW_BAL", legacy.getEscrowBalance()), BigDecimal.ZERO));
        account.setLtvPercent(parseDecimal("LN_LTV_PCT", legacy.getLtvPercent()));
        account.setPropertyAddress(legacy.getPropertyAddress());
        account.setPropertyCity(legacy.getPropertyCity());
        account.setPropertyState(legacy.getPropertyState());
        account.setPropertyZip(legacy.getPropertyZip());
        account.setPropertyType(LegacyCodeMapper.propertyType(legacy.getPropertyType()));
        account.setAppraisedValue(parseDecimal("PROP_APRS_VAL", legacy.getAppraisedValue()));
        account.setCreatedAt(timestampOrNow("LN_CRET_DT", legacy.getCreatedDate()));
        account.setUpdatedAt(timestampOrNow("LN_UPDT_DT", legacy.getUpdatedDate()));
        return account;
    }

    private Payment toPayment(LegacyPayment legacy, Map<String, LoanAccount> loansByAccountNumber) {
        Payment payment = new Payment();
        payment.setExternalId(require("PMT_SEQ_NBR", legacy.getPaymentSequenceNumber()));
        payment.setLoanAccount(resolve("LN_ACCT_NBR", legacy.getLoanAccountNumber(), loansByAccountNumber));
        payment.setPaymentDate(require("PMT_DT", parseDate("PMT_DT", legacy.getPaymentDate())));
        payment.setTotalAmount(require("PMT_AMT", parseDecimal("PMT_AMT", legacy.getTotalAmount())));
        payment.setPrincipalAmount(parseDecimal("PMT_PRIN_AMT", legacy.getPrincipalAmount()));
        payment.setInterestAmount(parseDecimal("PMT_INT_AMT", legacy.getInterestAmount()));
        payment.setEscrowAmount(parseDecimal("PMT_ESCROW_AMT", legacy.getEscrowAmount()));
        payment.setLateFee(Objects.requireNonNullElse(
                parseDecimal("PMT_LATE_FEE", legacy.getLateFee()), BigDecimal.ZERO));
        payment.setType(LegacyCodeMapper.paymentType(legacy.getTypeCode()));
        payment.setStatus(LegacyCodeMapper.paymentStatus(legacy.getStatusCode()));
        payment.setReceivedDate(parseDate("PMT_RECV_DT", legacy.getReceivedDate()));
        payment.setProcessedDate(parseDate("PMT_PROC_DT", legacy.getProcessedDate()));
        payment.setCreatedAt(timestampOrNow("PMT_CRET_DT", legacy.getCreatedDate()));
        payment.setUpdatedAt(timestampOrNow("PMT_UPDT_DT", legacy.getUpdatedDate()));
        return payment;
    }

    private static <T> T resolve(String column, String legacyKey, Map<String, T> modernRowsByKey) {
        String key = require(column, legacyKey);
        T row = modernRowsByKey.get(key);
        if (row == null) {
            throw new LegacyDataException(column + " '" + key + "' does not resolve to a migrated row");
        }
        return row;
    }

    private static LocalDateTime timestampOrNow(String column, String raw) {
        return Objects.requireNonNullElseGet(parseTimestamp(column, raw), LocalDateTime::now);
    }

    private static void warnOnDenormalizedNameDrift(LegacyLoanAccount legacy, Borrower borrower,
                                                    MigrationReport report) {
        String legacyName = trimToNull(legacy.getBorrowerFirstName()) + " " + trimToNull(legacy.getBorrowerLastName());
        String masterName = borrower.getFirstName() + " " + borrower.getLastName();
        if (!legacyName.equals(masterName)) {
            report.warning(LOAN_ACCOUNTS + " [" + legacy.getLoanAccountNumber() + "]: denormalized borrower name '"
                    + legacyName + "' differs from CDW_BORR_MSTR '" + masterName + "'; using borrower FK");
        }
    }

    private void reconcile(MigrationReport report, List<LegacyBorrower> legacyBorrowers,
                           List<LegacyLoanProduct> legacyProducts, List<LegacyLoanAccount> legacyLoans,
                           List<LegacyPayment> legacyPayments) {
        List<Borrower> borrowers = borrowerRepository.findAll();
        List<LoanAccount> loans = loanAccountRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();

        report.check(BORROWERS + " row count", (long) legacyBorrowers.size(), (long) borrowers.size());
        report.check(LOAN_PRODUCTS + " row count", (long) legacyProducts.size(), loanProductRepository.count());
        report.check(LOAN_ACCOUNTS + " row count", (long) legacyLoans.size(), (long) loans.size());
        report.check(PAYMENTS + " row count", (long) legacyPayments.size(), (long) payments.size());

        report.check("borrowers.annual_income", legacySum(legacyBorrowers, LegacyBorrower::getAnnualIncome),
                modernSum(borrowers, Borrower::getAnnualIncome));
        report.check("loan_accounts.original_amount", legacySum(legacyLoans, LegacyLoanAccount::getOriginalAmount),
                modernSum(loans, LoanAccount::getOriginalAmount));
        report.check("loan_accounts.current_balance", legacySum(legacyLoans, LegacyLoanAccount::getCurrentBalance),
                modernSum(loans, LoanAccount::getCurrentBalance));
        report.check("loan_accounts.monthly_payment", legacySum(legacyLoans, LegacyLoanAccount::getMonthlyPayment),
                modernSum(loans, LoanAccount::getMonthlyPayment));
        report.check("loan_accounts.escrow_balance", legacySum(legacyLoans, LegacyLoanAccount::getEscrowBalance),
                modernSum(loans, LoanAccount::getEscrowBalance));
        report.check("payments.total_amount", legacySum(legacyPayments, LegacyPayment::getTotalAmount),
                modernSum(payments, Payment::getTotalAmount));
        report.check("payments.principal_amount", legacySum(legacyPayments, LegacyPayment::getPrincipalAmount),
                modernSum(payments, Payment::getPrincipalAmount));
        report.check("payments.interest_amount", legacySum(legacyPayments, LegacyPayment::getInterestAmount),
                modernSum(payments, Payment::getInterestAmount));
        report.check("payments.escrow_amount", legacySum(legacyPayments, LegacyPayment::getEscrowAmount),
                modernSum(payments, Payment::getEscrowAmount));
        report.check("payments.late_fee", legacySum(legacyPayments, LegacyPayment::getLateFee),
                modernSum(payments, Payment::getLateFee));

        Map<String, LoanAccount> loansByNumber = loans.stream()
                .collect(Collectors.toMap(LoanAccount::getAccountNumber, Function.identity()));
        long loanFkMismatches = legacyLoans.stream().filter(legacy -> {
            LoanAccount modern = loansByNumber.get(trimToNull(legacy.getLoanAccountNumber()));
            return modern == null
                    || !modern.getBorrower().getExternalId().equals(trimToNull(legacy.getBorrowerId()))
                    || !modern.getProduct().getCode().equals(trimToNull(legacy.getProductCode()));
        }).count();
        report.check("loan_accounts FK mismatches", 0L, loanFkMismatches);

        Map<String, Payment> paymentsById = payments.stream()
                .collect(Collectors.toMap(Payment::getExternalId, Function.identity()));
        long paymentFkMismatches = legacyPayments.stream().filter(legacy -> {
            Payment modern = paymentsById.get(trimToNull(legacy.getPaymentSequenceNumber()));
            return modern == null
                    || !modern.getLoanAccount().getAccountNumber().equals(trimToNull(legacy.getLoanAccountNumber()));
        }).count();
        report.check("payments FK mismatches", 0L, paymentFkMismatches);
    }

    /** Sums a legacy amount column; malformed values are skipped here because they are already reported as rejected rows. */
    private static <L> BigDecimal legacySum(List<L> rows, Function<L, String> column) {
        BigDecimal total = BigDecimal.ZERO;
        for (L row : rows) {
            try {
                BigDecimal value = parseDecimal("reconciliation", column.apply(row));
                if (value != null) {
                    total = total.add(value);
                }
            } catch (LegacyDataException ignored) {
                // counted as a rejected row during migration
            }
        }
        return total;
    }

    private static <M> BigDecimal modernSum(List<M> rows, Function<M, BigDecimal> column) {
        return rows.stream().map(column).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
