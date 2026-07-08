package com.workshop.loanservice.modern.migration;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.ModernBorrowerRepository;
import com.workshop.loanservice.modern.repository.ModernLoanAccountRepository;
import com.workshop.loanservice.modern.repository.ModernLoanProductRepository;
import com.workshop.loanservice.modern.repository.ModernPaymentRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * Migrates all legacy CDW records into the modern normalized schema.
 *
 * <p>Migration order respects foreign keys: borrowers and loan products
 * first, then loan accounts (FKs resolved by borrower external_id and
 * product code), then payments (FK resolved by loan account number).
 *
 * <p>Idempotent: records already present in the modern tables (matched by
 * natural key) are skipped, so running the migration multiple times does
 * not create duplicates. Payments, which have no unique natural key column,
 * are matched on (loan account, payment date, total amount, type).
 *
 * <p>Null or malformed legacy values are logged as warnings and mapped to
 * null; records are never silently dropped. Records that cannot satisfy a
 * mandatory foreign key are logged as errors and counted as failures.
 */
@Service
public class LegacyToModernMigrationService {

    private static final Logger log = LoggerFactory.getLogger(LegacyToModernMigrationService.class);

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private static final Map<String, String> BORROWER_STATUS = Map.of(
            "ACT", "ACTIVE",
            "INA", "INACTIVE");

    private static final Map<String, String> LOAN_STATUS = Map.of(
            "ACT", "ACTIVE",
            "CLO", "CLOSED",
            "DFT", "DEFAULT",
            "FRB", "FORBEARANCE");

    private static final Map<String, String> PROPERTY_TYPE = Map.of(
            "SFR", "Single Family",
            "CND", "Condominium",
            "TWN", "Townhouse",
            "MFR", "Multi Family",
            "MAN", "Manufactured");

    private static final Map<String, String> PAYMENT_TYPE = Map.of(
            "REG", "REGULAR",
            "EXT", "EXTRA",
            "PRT", "PARTIAL",
            "PRE", "PREPAYMENT");

    private static final Map<String, String> PAYMENT_STATUS = Map.of(
            "PST", "POSTED",
            "REV", "REVERSED",
            "NSF", "NSF",
            "PND", "PENDING");

    private final LegacyBorrowerRepository legacyBorrowerRepository;
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    private final LegacyPaymentRepository legacyPaymentRepository;
    private final ModernBorrowerRepository modernBorrowerRepository;
    private final ModernLoanProductRepository modernLoanProductRepository;
    private final ModernLoanAccountRepository modernLoanAccountRepository;
    private final ModernPaymentRepository modernPaymentRepository;

    public LegacyToModernMigrationService(
            LegacyBorrowerRepository legacyBorrowerRepository,
            LegacyLoanProductRepository legacyLoanProductRepository,
            LegacyLoanAccountRepository legacyLoanAccountRepository,
            LegacyPaymentRepository legacyPaymentRepository,
            ModernBorrowerRepository modernBorrowerRepository,
            ModernLoanProductRepository modernLoanProductRepository,
            ModernLoanAccountRepository modernLoanAccountRepository,
            ModernPaymentRepository modernPaymentRepository) {
        this.legacyBorrowerRepository = legacyBorrowerRepository;
        this.legacyLoanProductRepository = legacyLoanProductRepository;
        this.legacyLoanAccountRepository = legacyLoanAccountRepository;
        this.legacyPaymentRepository = legacyPaymentRepository;
        this.modernBorrowerRepository = modernBorrowerRepository;
        this.modernLoanProductRepository = modernLoanProductRepository;
        this.modernLoanAccountRepository = modernLoanAccountRepository;
        this.modernPaymentRepository = modernPaymentRepository;
    }

    /**
     * Runs the full migration and returns per-table counts of migrated,
     * skipped (already present), and failed records.
     */
    @Transactional("modernTransactionManager")
    public MigrationResult migrateAll() {
        MigrationResult result = new MigrationResult();
        migrateBorrowers(result);
        migrateLoanProducts(result);
        migrateLoanAccounts(result);
        migratePayments(result);
        log.info("Migration complete: {}", result);
        return result;
    }

    private void migrateBorrowers(MigrationResult result) {
        for (LegacyBorrower legacy : legacyBorrowerRepository.findAll()) {
            String id = legacy.getBorrowerId();
            if (id == null || id.isBlank()) {
                log.error("Skipping legacy borrower with missing BORR_ID");
                result.borrowersFailed++;
                continue;
            }
            if (modernBorrowerRepository.findByExternalId(id).isPresent()) {
                log.info("Borrower {} already migrated; skipping", id);
                result.borrowersSkipped++;
                continue;
            }
            Borrower modern = new Borrower();
            modern.setExternalId(id);
            modern.setFirstName(legacy.getFirstName());
            modern.setLastName(legacy.getLastName());
            modern.setMiddleInitial(legacy.getMiddleInitial());
            modern.setSsnHash(legacy.getSsnEncrypted());
            modern.setDateOfBirth(parseDate(legacy.getDateOfBirth(), id, "BORR_DOB_DT"));
            modern.setAddressLine1(legacy.getAddressLine1());
            modern.setAddressLine2(legacy.getAddressLine2());
            modern.setCity(legacy.getCity());
            modern.setState(legacy.getStateCode());
            modern.setZipCode(legacy.getZipCode());
            modern.setPhone(legacy.getPhoneNumber());
            modern.setEmail(legacy.getEmail());
            modern.setCreditScore(parseInteger(legacy.getCreditScore(), id, "BORR_CRDT_SCR"));
            modern.setEmploymentStatus(legacy.getEmploymentStatus());
            modern.setAnnualIncome(parseDecimal(legacy.getAnnualIncome(), id, "BORR_ANN_INCM"));
            modern.setStatus(expandCode(BORROWER_STATUS, legacy.getStatusCode(), id, "BORR_STAT_CD"));
            modern.setCreatedAt(parseTimestamp(legacy.getCreatedDate(), id, "BORR_CRET_DT"));
            modern.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate(), id, "BORR_UPDT_DT"));
            modernBorrowerRepository.save(modern);
            result.borrowersMigrated++;
        }
    }

    private void migrateLoanProducts(MigrationResult result) {
        for (LegacyLoanProduct legacy : legacyLoanProductRepository.findAll()) {
            String code = legacy.getProductCode();
            if (code == null || code.isBlank()) {
                log.error("Skipping legacy loan product with missing PROD_CD");
                result.productsFailed++;
                continue;
            }
            if (modernLoanProductRepository.findByCode(code).isPresent()) {
                log.info("Loan product {} already migrated; skipping", code);
                result.productsSkipped++;
                continue;
            }
            LoanProduct modern = new LoanProduct();
            modern.setCode(code);
            modern.setName(legacy.getDescription());
            modern.setType(legacy.getTypeCode());
            modern.setTermMonths(parseInteger(legacy.getTermMonths(), code, "PROD_TERM_MOS"));
            modern.setRateType(legacy.getRateType());
            modern.setMinAmount(parseDecimal(legacy.getMinAmount(), code, "PROD_MIN_AMT"));
            modern.setMaxAmount(parseDecimal(legacy.getMaxAmount(), code, "PROD_MAX_AMT"));
            modern.setIsActive("ACT".equals(legacy.getStatusCode()));
            modern.setEffectiveDate(parseDate(legacy.getEffectiveDate(), code, "PROD_EFF_DT"));
            modern.setExpirationDate(parseDate(legacy.getExpirationDate(), code, "PROD_EXP_DT"));
            modernLoanProductRepository.save(modern);
            result.productsMigrated++;
        }
    }

    private void migrateLoanAccounts(MigrationResult result) {
        for (LegacyLoanAccount legacy : legacyLoanAccountRepository.findAll()) {
            String acctNbr = legacy.getLoanAccountNumber();
            if (acctNbr == null || acctNbr.isBlank()) {
                log.error("Skipping legacy loan account with missing LN_ACCT_NBR");
                result.accountsFailed++;
                continue;
            }
            if (modernLoanAccountRepository.findByAccountNumber(acctNbr).isPresent()) {
                log.info("Loan account {} already migrated; skipping", acctNbr);
                result.accountsSkipped++;
                continue;
            }
            Optional<Borrower> borrower = Optional.ofNullable(legacy.getBorrowerId())
                    .flatMap(modernBorrowerRepository::findByExternalId);
            if (borrower.isEmpty()) {
                log.error("Loan account {}: cannot resolve borrower '{}'; record not migrated",
                        acctNbr, legacy.getBorrowerId());
                result.accountsFailed++;
                continue;
            }
            Optional<LoanProduct> product = Optional.ofNullable(legacy.getProductCode())
                    .flatMap(modernLoanProductRepository::findByCode);
            if (product.isEmpty()) {
                log.error("Loan account {}: cannot resolve product '{}'; record not migrated",
                        acctNbr, legacy.getProductCode());
                result.accountsFailed++;
                continue;
            }
            LoanAccount modern = new LoanAccount();
            modern.setAccountNumber(acctNbr);
            modern.setBorrower(borrower.get());
            modern.setProduct(product.get());
            modern.setOriginalAmount(parseDecimal(legacy.getOriginalAmount(), acctNbr, "LN_ORIG_AMT"));
            modern.setCurrentBalance(parseDecimal(legacy.getCurrentBalance(), acctNbr, "LN_CURR_BAL"));
            modern.setInterestRate(parseDecimal(legacy.getInterestRate(), acctNbr, "LN_INT_RT"));
            modern.setTermMonths(parseInteger(legacy.getTermMonths(), acctNbr, "LN_TERM_MOS"));
            modern.setMonthlyPayment(parseDecimal(legacy.getMonthlyPayment(), acctNbr, "LN_PMT_AMT"));
            modern.setOriginationDate(parseDate(legacy.getOriginationDate(), acctNbr, "LN_ORIG_DT"));
            modern.setMaturityDate(parseDate(legacy.getMaturityDate(), acctNbr, "LN_MAT_DT"));
            modern.setFirstPaymentDate(parseDate(legacy.getFirstPaymentDate(), acctNbr, "LN_1ST_PMT_DT"));
            modern.setNextPaymentDate(parseDate(legacy.getNextPaymentDate(), acctNbr, "LN_NXT_PMT_DT"));
            modern.setStatus(expandCode(LOAN_STATUS, legacy.getStatusCode(), acctNbr, "LN_STAT_CD"));
            modern.setDelinquencyDays(parseInteger(legacy.getDelinquencyDays(), acctNbr, "LN_DLQ_DAYS"));
            modern.setEscrowBalance(parseDecimal(legacy.getEscrowBalance(), acctNbr, "LN_ESCROW_BAL"));
            modern.setLtvPercent(parseDecimal(legacy.getLtvPercent(), acctNbr, "LN_LTV_PCT"));
            modern.setPropertyAddress(legacy.getPropertyAddress());
            modern.setPropertyCity(legacy.getPropertyCity());
            modern.setPropertyState(legacy.getPropertyState());
            modern.setPropertyZip(legacy.getPropertyZip());
            modern.setPropertyType(expandCode(PROPERTY_TYPE, legacy.getPropertyType(), acctNbr, "PROP_TYP_CD"));
            modern.setAppraisedValue(parseDecimal(legacy.getAppraisedValue(), acctNbr, "PROP_APRS_VAL"));
            modern.setCreatedAt(parseTimestamp(legacy.getCreatedDate(), acctNbr, "LN_CRET_DT"));
            modern.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate(), acctNbr, "LN_UPDT_DT"));
            modernLoanAccountRepository.save(modern);
            result.accountsMigrated++;
        }
    }

    private void migratePayments(MigrationResult result) {
        for (LegacyPayment legacy : legacyPaymentRepository.findAll()) {
            String seqNbr = legacy.getPaymentSequenceNumber();
            Optional<LoanAccount> account = Optional.ofNullable(legacy.getLoanAccountNumber())
                    .flatMap(modernLoanAccountRepository::findByAccountNumber);
            if (account.isEmpty()) {
                log.error("Payment {}: cannot resolve loan account '{}'; record not migrated",
                        seqNbr, legacy.getLoanAccountNumber());
                result.paymentsFailed++;
                continue;
            }
            LocalDate paymentDate = parseDate(legacy.getPaymentDate(), seqNbr, "PMT_DT");
            BigDecimal totalAmount = parseDecimal(legacy.getTotalAmount(), seqNbr, "PMT_AMT");
            String type = expandCode(PAYMENT_TYPE, legacy.getTypeCode(), seqNbr, "PMT_TYP_CD");
            if (modernPaymentRepository.existsByLoanAccountIdAndPaymentDateAndTotalAmountAndType(
                    account.get().getId(), paymentDate, totalAmount, type)) {
                log.info("Payment {} already migrated; skipping", seqNbr);
                result.paymentsSkipped++;
                continue;
            }
            Payment modern = new Payment();
            modern.setLoanAccount(account.get());
            modern.setPaymentDate(paymentDate);
            modern.setTotalAmount(totalAmount);
            modern.setPrincipalAmount(parseDecimal(legacy.getPrincipalAmount(), seqNbr, "PMT_PRIN_AMT"));
            modern.setInterestAmount(parseDecimal(legacy.getInterestAmount(), seqNbr, "PMT_INT_AMT"));
            modern.setEscrowAmount(parseDecimal(legacy.getEscrowAmount(), seqNbr, "PMT_ESCROW_AMT"));
            modern.setLateFee(parseDecimal(legacy.getLateFee(), seqNbr, "PMT_LATE_FEE"));
            modern.setType(type);
            modern.setStatus(expandCode(PAYMENT_STATUS, legacy.getStatusCode(), seqNbr, "PMT_STAT_CD"));
            modern.setReceivedDate(parseDate(legacy.getReceivedDate(), seqNbr, "PMT_RECV_DT"));
            modern.setProcessedDate(parseDate(legacy.getProcessedDate(), seqNbr, "PMT_PROC_DT"));
            modern.setCreatedAt(parseTimestamp(legacy.getCreatedDate(), seqNbr, "PMT_CRET_DT"));
            modern.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate(), seqNbr, "PMT_UPDT_DT"));
            modernPaymentRepository.save(modern);
            result.paymentsMigrated++;
        }
    }

    private LocalDate parseDate(String value, String recordId, String field) {
        if (value == null || value.isBlank()) {
            log.warn("Record {}: {} is null/blank; mapping to null", recordId, field);
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), LEGACY_DATE);
        } catch (DateTimeParseException e) {
            log.warn("Record {}: {} value '{}' is not a valid MM/dd/yyyy date; mapping to null",
                    recordId, field, value);
            return null;
        }
    }

    private LocalDateTime parseTimestamp(String value, String recordId, String field) {
        LocalDate date = parseDate(value, recordId, field);
        return date == null ? null : date.atStartOfDay();
    }

    private BigDecimal parseDecimal(String value, String recordId, String field) {
        if (value == null || value.isBlank()) {
            log.warn("Record {}: {} is null/blank; mapping to null", recordId, field);
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            log.warn("Record {}: {} value '{}' is not a valid amount; mapping to null",
                    recordId, field, value);
            return null;
        }
    }

    private Integer parseInteger(String value, String recordId, String field) {
        if (value == null || value.isBlank()) {
            log.warn("Record {}: {} is null/blank; mapping to null", recordId, field);
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Record {}: {} value '{}' is not a valid integer; mapping to null",
                    recordId, field, value);
            return null;
        }
    }

    private String expandCode(Map<String, String> mapping, String code, String recordId, String field) {
        if (code == null || code.isBlank()) {
            log.warn("Record {}: {} is null/blank; mapping to null", recordId, field);
            return null;
        }
        String expanded = mapping.get(code.trim());
        if (expanded == null) {
            log.warn("Record {}: {} code '{}' has no known expansion; keeping raw value",
                    recordId, field, code);
            return code.trim();
        }
        return expanded;
    }

    /**
     * Per-table migration outcome counts.
     */
    public static class MigrationResult {
        private int borrowersMigrated;
        private int borrowersSkipped;
        private int borrowersFailed;
        private int productsMigrated;
        private int productsSkipped;
        private int productsFailed;
        private int accountsMigrated;
        private int accountsSkipped;
        private int accountsFailed;
        private int paymentsMigrated;
        private int paymentsSkipped;
        private int paymentsFailed;

        public int getBorrowersMigrated() { return borrowersMigrated; }
        public int getBorrowersSkipped() { return borrowersSkipped; }
        public int getBorrowersFailed() { return borrowersFailed; }
        public int getProductsMigrated() { return productsMigrated; }
        public int getProductsSkipped() { return productsSkipped; }
        public int getProductsFailed() { return productsFailed; }
        public int getAccountsMigrated() { return accountsMigrated; }
        public int getAccountsSkipped() { return accountsSkipped; }
        public int getAccountsFailed() { return accountsFailed; }
        public int getPaymentsMigrated() { return paymentsMigrated; }
        public int getPaymentsSkipped() { return paymentsSkipped; }
        public int getPaymentsFailed() { return paymentsFailed; }

        @Override
        public String toString() {
            return "MigrationResult{borrowers=" + borrowersMigrated + "/" + borrowersSkipped + "/" + borrowersFailed
                    + ", products=" + productsMigrated + "/" + productsSkipped + "/" + productsFailed
                    + ", accounts=" + accountsMigrated + "/" + accountsSkipped + "/" + accountsFailed
                    + ", payments=" + paymentsMigrated + "/" + paymentsSkipped + "/" + paymentsFailed
                    + " (migrated/skipped/failed)}";
        }
    }
}
