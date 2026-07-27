package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.migration.MigrationReport.TableReport;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Copies the legacy CDW tables into the modern normalized schema.
 *
 * <p>Everything happens in one {@code modernTransactionManager} transaction, so a thrown failure
 * rolls back the migrated rows and their {@code migration_id_map} entries together. Individual
 * records that cannot be transformed are skipped (never thrown) and reported.
 *
 * <p>Transformations follow {@code data/mappings/column_mappings.md} exactly. A code the mapping
 * document does not expand is a gap in that document rather than bad data, so the record migrates
 * with the raw legacy code and the gap is reported.
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private static final Map<String, String> BORROWER_STATUS =
            Map.of("ACT", "ACTIVE", "INA", "INACTIVE");
    private static final Map<String, Boolean> PRODUCT_ACTIVE =
            Map.of("ACT", Boolean.TRUE, "INA", Boolean.FALSE);
    private static final Map<String, String> LOAN_STATUS =
            Map.of("ACT", "ACTIVE", "CLO", "CLOSED", "DFT", "DEFAULT", "FRB", "FORBEARANCE");
    /** Only the expansions spelled out in column_mappings.md; e.g. TWN and MFR are not. */
    private static final Map<String, String> PROPERTY_TYPE =
            Map.of("SFR", "Single Family", "CND", "Condominium");
    private static final Map<String, String> PAYMENT_TYPE =
            Map.of("REG", "REGULAR", "EXT", "EXTRA", "PRT", "PARTIAL", "PRE", "PREPAYMENT");
    private static final Map<String, String> PAYMENT_STATUS =
            Map.of("PST", "POSTED", "REV", "REVERSED", "NSF", "NSF", "PND", "PENDING");

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyAccounts;
    private final LegacyPaymentRepository legacyPayments;
    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository accounts;
    private final PaymentRepository payments;
    private final MigrationIdMap idMap;

    public MigrationService(LegacyBorrowerRepository legacyBorrowers,
                            LegacyLoanProductRepository legacyProducts,
                            LegacyLoanAccountRepository legacyAccounts,
                            LegacyPaymentRepository legacyPayments,
                            BorrowerRepository borrowers,
                            LoanProductRepository products,
                            LoanAccountRepository accounts,
                            PaymentRepository payments,
                            MigrationIdMap idMap) {
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyAccounts = legacyAccounts;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.accounts = accounts;
        this.payments = payments;
        this.idMap = idMap;
    }

    /**
     * Creates {@code migration_id_map} if it is missing. Runs outside the migration transaction so
     * the DDL cannot interfere with its rollback.
     */
    public void initializeTracking() {
        idMap.createTableIfMissing();
    }

    /**
     * Migrates borrowers, loan products, loan accounts and payments in that (foreign key driven)
     * order, then validates the result.
     */
    @Transactional("modernTransactionManager")
    public MigrationReport migrate() {
        MigrationReport report = new MigrationReport();
        LocalDateTime migratedAt = LocalDateTime.now();

        migrateBorrowers(report.table("CDW_BORR_MSTR", "borrowers"), migratedAt);
        migrateProducts(report.table("CDW_LN_PROD", "loan_products"), migratedAt);
        migrateAccounts(report.table("CDW_LN_ACCT", "loan_accounts"), migratedAt);
        migratePayments(report.table("CDW_PMT_HIST", "payments"), migratedAt);

        validate(report);
        return report;
    }

    private void migrateBorrowers(TableReport table, LocalDateTime migratedAt) {
        List<LegacyBorrower> source = legacyBorrowers.findAll();
        table.setLegacyCount(source.size());
        for (LegacyBorrower legacy : source) {
            String legacyId = legacy.getBorrowerId();
            try {
                if (skipAlreadyMigrated(table, MigrationIdMap.BORROWER, legacyId)) {
                    continue;
                }
                Borrower borrower = new Borrower();
                borrower.setExternalId(LegacyValues.requiredText(legacyId, "BORR_ID"));
                borrower.setFirstName(LegacyValues.requiredText(legacy.getFirstName(), "BORR_FST_NM"));
                borrower.setLastName(LegacyValues.requiredText(legacy.getLastName(), "BORR_LST_NM"));
                borrower.setMiddleInitial(LegacyValues.optionalText(legacy.getMiddleInitial()));
                borrower.setSsnHash(LegacyValues.optionalText(legacy.getSsnEncrypted()));
                borrower.setDateOfBirth(LegacyValues.optionalDate(legacy.getDateOfBirth(), "BORR_DOB_DT"));
                borrower.setAddressLine1(LegacyValues.optionalText(legacy.getAddressLine1()));
                borrower.setAddressLine2(LegacyValues.optionalText(legacy.getAddressLine2()));
                borrower.setCity(LegacyValues.optionalText(legacy.getCity()));
                borrower.setState(LegacyValues.optionalText(legacy.getStateCode()));
                borrower.setZipCode(LegacyValues.optionalText(legacy.getZipCode()));
                borrower.setPhone(LegacyValues.optionalText(legacy.getPhoneNumber()));
                borrower.setEmail(LegacyValues.optionalText(legacy.getEmail()));
                borrower.setCreditScore(LegacyValues.optionalInteger(legacy.getCreditScore(), "BORR_CRDT_SCR"));
                borrower.setEmploymentStatus(LegacyValues.optionalText(legacy.getEmploymentStatus()));
                borrower.setAnnualIncome(LegacyValues.optionalAmount(legacy.getAnnualIncome(), "BORR_ANN_INCM"));
                borrower.setStatus(expand(legacy.getStatusCode(), "BORR_STAT_CD", BORROWER_STATUS, legacyId));
                borrower.setCreatedAt(LegacyValues.optionalTimestamp(legacy.getCreatedDate(), "BORR_CRET_DT"));
                borrower.setUpdatedAt(LegacyValues.optionalTimestamp(legacy.getUpdatedDate(), "BORR_UPDT_DT"));

                borrowers.saveAndFlush(borrower);
                idMap.record(MigrationIdMap.BORROWER, borrower.getExternalId(), borrower.getId(), migratedAt);
                table.incrementMigrated();
            } catch (MalformedRecordException e) {
                skip(table, "CDW_BORR_MSTR", legacyId, e);
            }
        }
    }

    private void migrateProducts(TableReport table, LocalDateTime migratedAt) {
        List<LegacyLoanProduct> source = legacyProducts.findAll();
        table.setLegacyCount(source.size());
        for (LegacyLoanProduct legacy : source) {
            String legacyId = legacy.getProductCode();
            try {
                if (skipAlreadyMigrated(table, MigrationIdMap.LOAN_PRODUCT, legacyId)) {
                    continue;
                }
                LoanProduct product = new LoanProduct();
                product.setCode(LegacyValues.requiredText(legacyId, "PROD_CD"));
                product.setName(LegacyValues.requiredText(legacy.getDescription(), "PROD_DESC_TXT"));
                product.setType(LegacyValues.requiredText(legacy.getTypeCode(), "PROD_TYP_CD"));
                product.setTermMonths(LegacyValues.requiredInteger(legacy.getTermMonths(), "PROD_TERM_MOS"));
                product.setRateType(LegacyValues.requiredText(legacy.getRateType(), "PROD_RT_TYP"));
                product.setMinAmount(LegacyValues.optionalAmount(legacy.getMinAmount(), "PROD_MIN_AMT"));
                product.setMaxAmount(LegacyValues.optionalAmount(legacy.getMaxAmount(), "PROD_MAX_AMT"));
                product.setActive(expandFlag(legacy.getStatusCode(), "PROD_STAT_CD", PRODUCT_ACTIVE, legacyId));
                product.setEffectiveDate(LegacyValues.optionalDate(legacy.getEffectiveDate(), "PROD_EFF_DT"));
                product.setExpirationDate(LegacyValues.optionalDate(legacy.getExpirationDate(), "PROD_EXP_DT"));

                products.saveAndFlush(product);
                idMap.record(MigrationIdMap.LOAN_PRODUCT, product.getCode(), product.getId(), migratedAt);
                table.incrementMigrated();
            } catch (MalformedRecordException e) {
                skip(table, "CDW_LN_PROD", legacyId, e);
            }
        }
    }

    private void migrateAccounts(TableReport table, LocalDateTime migratedAt) {
        List<LegacyLoanAccount> source = legacyAccounts.findAll();
        table.setLegacyCount(source.size());
        for (LegacyLoanAccount legacy : source) {
            String legacyId = legacy.getLoanAccountNumber();
            try {
                if (skipAlreadyMigrated(table, MigrationIdMap.LOAN_ACCOUNT, legacyId)) {
                    continue;
                }
                LoanAccount account = new LoanAccount();
                account.setAccountNumber(LegacyValues.requiredText(legacyId, "LN_ACCT_NBR"));
                // Denormalized BORR_FST_NM / BORR_LST_NM / BORR_SSN_LST4 are dropped: the borrower
                // is resolved by preserved legacy id only, never by name.
                account.setBorrower(resolveParent(MigrationIdMap.BORROWER,
                        LegacyValues.requiredText(legacy.getBorrowerId(), "BORR_ID"), "BORR_ID",
                        borrowers::findById, borrowers::findByExternalId, Borrower::getId));
                account.setProduct(resolveParent(MigrationIdMap.LOAN_PRODUCT,
                        LegacyValues.requiredText(legacy.getProductCode(), "PROD_CD"), "PROD_CD",
                        products::findById, products::findByCode, LoanProduct::getId));
                account.setOriginalAmount(LegacyValues.requiredAmount(legacy.getOriginalAmount(), "LN_ORIG_AMT"));
                account.setCurrentBalance(LegacyValues.requiredAmount(legacy.getCurrentBalance(), "LN_CURR_BAL"));
                account.setInterestRate(LegacyValues.requiredAmount(legacy.getInterestRate(), "LN_INT_RT"));
                account.setTermMonths(LegacyValues.requiredInteger(legacy.getTermMonths(), "LN_TERM_MOS"));
                account.setMonthlyPayment(LegacyValues.requiredAmount(legacy.getMonthlyPayment(), "LN_PMT_AMT"));
                account.setOriginationDate(LegacyValues.requiredDate(legacy.getOriginationDate(), "LN_ORIG_DT"));
                account.setMaturityDate(LegacyValues.requiredDate(legacy.getMaturityDate(), "LN_MAT_DT"));
                account.setFirstPaymentDate(LegacyValues.optionalDate(legacy.getFirstPaymentDate(), "LN_1ST_PMT_DT"));
                account.setNextPaymentDate(LegacyValues.optionalDate(legacy.getNextPaymentDate(), "LN_NXT_PMT_DT"));
                account.setStatus(expand(legacy.getStatusCode(), "LN_STAT_CD", LOAN_STATUS, legacyId));
                account.setDelinquencyDays(LegacyValues.optionalInteger(legacy.getDelinquencyDays(), "LN_DLQ_DAYS"));
                account.setEscrowBalance(LegacyValues.optionalAmount(legacy.getEscrowBalance(), "LN_ESCROW_BAL"));
                account.setLtvPercent(LegacyValues.optionalAmount(legacy.getLtvPercent(), "LN_LTV_PCT"));
                account.setPropertyAddress(LegacyValues.optionalText(legacy.getPropertyAddress()));
                account.setPropertyCity(LegacyValues.optionalText(legacy.getPropertyCity()));
                account.setPropertyState(LegacyValues.optionalText(legacy.getPropertyState()));
                account.setPropertyZip(LegacyValues.optionalText(legacy.getPropertyZip()));
                account.setPropertyType(expand(legacy.getPropertyType(), "PROP_TYP_CD", PROPERTY_TYPE, legacyId));
                account.setAppraisedValue(LegacyValues.optionalAmount(legacy.getAppraisedValue(), "PROP_APRS_VAL"));
                account.setCreatedAt(LegacyValues.optionalTimestamp(legacy.getCreatedDate(), "LN_CRET_DT"));
                account.setUpdatedAt(LegacyValues.optionalTimestamp(legacy.getUpdatedDate(), "LN_UPDT_DT"));

                accounts.saveAndFlush(account);
                idMap.record(MigrationIdMap.LOAN_ACCOUNT, account.getAccountNumber(), account.getId(), migratedAt);
                table.incrementMigrated();
            } catch (MalformedRecordException e) {
                skip(table, "CDW_LN_ACCT", legacyId, e);
            }
        }
    }

    private void migratePayments(TableReport table, LocalDateTime migratedAt) {
        List<LegacyPayment> source = legacyPayments.findAll();
        table.setLegacyCount(source.size());
        for (LegacyPayment legacy : source) {
            String legacyId = legacy.getPaymentSequenceNumber();
            try {
                if (skipAlreadyMigrated(table, MigrationIdMap.PAYMENT, legacyId)) {
                    continue;
                }
                Payment payment = new Payment();
                LegacyValues.requiredText(legacyId, "PMT_SEQ_NBR");
                payment.setLoanAccount(resolveParent(MigrationIdMap.LOAN_ACCOUNT,
                        LegacyValues.requiredText(legacy.getLoanAccountNumber(), "LN_ACCT_NBR"), "LN_ACCT_NBR",
                        accounts::findById, accounts::findByAccountNumber, LoanAccount::getId));
                payment.setPaymentDate(LegacyValues.requiredDate(legacy.getPaymentDate(), "PMT_DT"));
                payment.setTotalAmount(LegacyValues.requiredAmount(legacy.getTotalAmount(), "PMT_AMT"));
                payment.setPrincipalAmount(LegacyValues.optionalAmount(legacy.getPrincipalAmount(), "PMT_PRIN_AMT"));
                payment.setInterestAmount(LegacyValues.optionalAmount(legacy.getInterestAmount(), "PMT_INT_AMT"));
                payment.setEscrowAmount(LegacyValues.optionalAmount(legacy.getEscrowAmount(), "PMT_ESCROW_AMT"));
                payment.setLateFee(LegacyValues.optionalAmount(legacy.getLateFee(), "PMT_LATE_FEE"));
                payment.setType(expand(legacy.getTypeCode(), "PMT_TYP_CD", PAYMENT_TYPE, legacyId));
                payment.setStatus(expand(legacy.getStatusCode(), "PMT_STAT_CD", PAYMENT_STATUS, legacyId));
                payment.setReceivedDate(LegacyValues.optionalDate(legacy.getReceivedDate(), "PMT_RECV_DT"));
                payment.setProcessedDate(LegacyValues.optionalDate(legacy.getProcessedDate(), "PMT_PROC_DT"));
                payment.setCreatedAt(LegacyValues.optionalTimestamp(legacy.getCreatedDate(), "PMT_CRET_DT"));
                payment.setUpdatedAt(LegacyValues.optionalTimestamp(legacy.getUpdatedDate(), "PMT_UPDT_DT"));

                payments.saveAndFlush(payment);
                idMap.record(MigrationIdMap.PAYMENT, legacyId.trim(), payment.getId(), migratedAt);
                table.incrementMigrated();
            } catch (MalformedRecordException e) {
                skip(table, "CDW_PMT_HIST", legacyId, e);
            }
        }
    }

    /**
     * Expands a legacy code, or keeps it as-is when column_mappings.md defines no expansion for it:
     * a missing expansion is a gap in the mapping document, not a reason to lose the record.
     */
    private String expand(String value, String field, Map<String, String> expansions, String legacyId) {
        String code = LegacyValues.requiredText(value, field);
        String expanded = expansions.get(code);
        if (expanded == null) {
            log.warn("{} code '{}' on {} has no expansion in column_mappings.md; migrating as-is",
                    field, code, legacyId);
            return code;
        }
        return expanded;
    }

    /**
     * Boolean counterpart of {@link #expand}. An unexpanded code cannot be represented as a
     * boolean, so the column is left null rather than guessing; the record still migrates.
     */
    private Boolean expandFlag(String value, String field, Map<String, Boolean> expansions, String legacyId) {
        String code = LegacyValues.requiredText(value, field);
        Boolean expanded = expansions.get(code);
        if (expanded == null) {
            log.warn("{} code '{}' on {} has no expansion in column_mappings.md; leaving the column null",
                    field, code, legacyId);
        }
        return expanded;
    }

    private boolean skipAlreadyMigrated(TableReport table, String entityType, String legacyId) {
        if (legacyId != null && idMap.exists(entityType, legacyId.trim())) {
            table.incrementAlreadyMigrated();
            return true;
        }
        return false;
    }

    private void skip(TableReport table, String legacyTable, String legacyId, MalformedRecordException e) {
        log.warn("Skipping malformed {} record {}: {}", legacyTable, legacyId, e.getMessage());
        table.addSkip(legacyId, e.getMessage());
    }

    /**
     * Resolves an already migrated parent through {@code migration_id_map}, falling back to (and
     * cross-checking against) the natural key lookup on the modern repository.
     */
    private <T> T resolveParent(String entityType, String legacyId, String field,
                                Function<Long, Optional<T>> byModernId,
                                Function<String, Optional<T>> byNaturalKey,
                                Function<T, Long> idOf) {
        Optional<T> mapped = idMap.findModernId(entityType, legacyId).flatMap(byModernId);
        Optional<T> found = byNaturalKey.apply(legacyId);
        if (mapped.isPresent() && found.isPresent()
                && !idOf.apply(mapped.get()).equals(idOf.apply(found.get()))) {
            throw new MalformedRecordException(field + " '" + legacyId + "' resolves to "
                    + entityType + " id " + idOf.apply(mapped.get()) + " in migration_id_map but "
                    + idOf.apply(found.get()) + " by natural key");
        }
        return mapped.or(() -> found).orElseThrow(() -> new MalformedRecordException(
                field + " '" + legacyId + "' has no migrated " + entityType + " to reference"));
    }

    private void validate(MigrationReport report) {
        validateBorrowers(report.getTables().get(0), report);
        validateProducts(report.getTables().get(1), report);
        validateAccounts(report.getTables().get(2), report);
        validatePayments(report.getTables().get(3), report);
        scanForMappingGaps(report);
    }

    /**
     * Reports every legacy code, in any of the four tables, that column_mappings.md does not
     * expand. Scans the legacy source rather than this run's inserts, so the list is complete even
     * when the records were migrated by an earlier run.
     */
    private void scanForMappingGaps(MigrationReport report) {
        TableReport borrowerTable = report.getTables().get(0);
        for (LegacyBorrower legacy : legacyBorrowers.findAll()) {
            checkCode(borrowerTable, legacy.getBorrowerId(), "BORR_STAT_CD", legacy.getStatusCode(), BORROWER_STATUS);
        }
        TableReport productTable = report.getTables().get(1);
        for (LegacyLoanProduct legacy : legacyProducts.findAll()) {
            checkCode(productTable, legacy.getProductCode(), "PROD_STAT_CD", legacy.getStatusCode(), PRODUCT_ACTIVE);
        }
        TableReport accountTable = report.getTables().get(2);
        for (LegacyLoanAccount legacy : legacyAccounts.findAll()) {
            checkCode(accountTable, legacy.getLoanAccountNumber(), "LN_STAT_CD", legacy.getStatusCode(), LOAN_STATUS);
            checkCode(accountTable, legacy.getLoanAccountNumber(), "PROP_TYP_CD", legacy.getPropertyType(), PROPERTY_TYPE);
        }
        TableReport paymentTable = report.getTables().get(3);
        for (LegacyPayment legacy : legacyPayments.findAll()) {
            checkCode(paymentTable, legacy.getPaymentSequenceNumber(), "PMT_TYP_CD", legacy.getTypeCode(), PAYMENT_TYPE);
            checkCode(paymentTable, legacy.getPaymentSequenceNumber(), "PMT_STAT_CD", legacy.getStatusCode(), PAYMENT_STATUS);
        }
    }

    private static void checkCode(TableReport table, String legacyId, String field, String value,
                                  Map<String, ?> expansions) {
        String code = LegacyValues.optionalText(value);
        if (code != null && !expansions.containsKey(code)) {
            table.addMappingGap(legacyId, field, code);
        }
    }

    private void validateBorrowers(TableReport table, MigrationReport report) {
        table.setMapped(idMap.count(MigrationIdMap.BORROWER));
        List<BigDecimal> legacyIncome = new ArrayList<>();
        List<BigDecimal> modernIncome = new ArrayList<>();
        for (LegacyBorrower legacy : legacyBorrowers.findAll()) {
            Optional<Borrower> migrated = idMap.findModernId(MigrationIdMap.BORROWER, legacy.getBorrowerId())
                    .flatMap(borrowers::findById);
            if (migrated.isEmpty()) {
                continue;
            }
            legacyIncome.add(LegacyValues.optionalAmount(legacy.getAnnualIncome(), "BORR_ANN_INCM"));
            modernIncome.add(migrated.get().getAnnualIncome());
        }
        table.addLegacySum("annual_income", sum(legacyIncome));
        table.addModernSum("annual_income", sum(modernIncome));
        report.addCriterion("5 borrowers migrated", table.getMapped() == 5,
                "borrowers mapped=" + table.getMapped() + " of legacy=" + table.getLegacyCount()
                        + skippedDetail(table));
    }

    private void validateProducts(TableReport table, MigrationReport report) {
        table.setMapped(idMap.count(MigrationIdMap.LOAN_PRODUCT));
        List<BigDecimal> legacyMin = new ArrayList<>();
        List<BigDecimal> legacyMax = new ArrayList<>();
        List<BigDecimal> modernMin = new ArrayList<>();
        List<BigDecimal> modernMax = new ArrayList<>();
        for (LegacyLoanProduct legacy : legacyProducts.findAll()) {
            Optional<LoanProduct> migrated = idMap.findModernId(MigrationIdMap.LOAN_PRODUCT, legacy.getProductCode())
                    .flatMap(products::findById);
            if (migrated.isEmpty()) {
                continue;
            }
            legacyMin.add(LegacyValues.optionalAmount(legacy.getMinAmount(), "PROD_MIN_AMT"));
            legacyMax.add(LegacyValues.optionalAmount(legacy.getMaxAmount(), "PROD_MAX_AMT"));
            modernMin.add(migrated.get().getMinAmount());
            modernMax.add(migrated.get().getMaxAmount());
        }
        table.addLegacySum("min_amount", sum(legacyMin));
        table.addModernSum("min_amount", sum(modernMin));
        table.addLegacySum("max_amount", sum(legacyMax));
        table.addModernSum("max_amount", sum(modernMax));
        report.addCriterion("5 loan products migrated", table.getMapped() == 5,
                "loan_products mapped=" + table.getMapped() + " of legacy=" + table.getLegacyCount()
                        + skippedDetail(table));
    }

    private void validateAccounts(TableReport table, MigrationReport report) {
        table.setMapped(idMap.count(MigrationIdMap.LOAN_ACCOUNT));
        Map<String, List<BigDecimal>> legacySums = newSumBuckets("original_amount", "current_balance",
                "interest_rate", "monthly_payment", "escrow_balance", "ltv_percent", "appraised_value");
        Map<String, List<BigDecimal>> modernSums = newSumBuckets("original_amount", "current_balance",
                "interest_rate", "monthly_payment", "escrow_balance", "ltv_percent", "appraised_value");
        List<String> fkProblems = new ArrayList<>();
        for (LegacyLoanAccount legacy : legacyAccounts.findAll()) {
            Optional<LoanAccount> found = idMap.findModernId(MigrationIdMap.LOAN_ACCOUNT, legacy.getLoanAccountNumber())
                    .flatMap(accounts::findById);
            if (found.isEmpty()) {
                continue;
            }
            LoanAccount account = found.get();
            if (!account.getBorrower().getExternalId().equals(legacy.getBorrowerId())) {
                fkProblems.add(legacy.getLoanAccountNumber() + " borrower_id -> "
                        + account.getBorrower().getExternalId() + " expected " + legacy.getBorrowerId());
            }
            if (!account.getProduct().getCode().equals(legacy.getProductCode())) {
                fkProblems.add(legacy.getLoanAccountNumber() + " product_id -> "
                        + account.getProduct().getCode() + " expected " + legacy.getProductCode());
            }
            legacySums.get("original_amount").add(LegacyValues.optionalAmount(legacy.getOriginalAmount(), "LN_ORIG_AMT"));
            legacySums.get("current_balance").add(LegacyValues.optionalAmount(legacy.getCurrentBalance(), "LN_CURR_BAL"));
            legacySums.get("interest_rate").add(LegacyValues.optionalAmount(legacy.getInterestRate(), "LN_INT_RT"));
            legacySums.get("monthly_payment").add(LegacyValues.optionalAmount(legacy.getMonthlyPayment(), "LN_PMT_AMT"));
            legacySums.get("escrow_balance").add(LegacyValues.optionalAmount(legacy.getEscrowBalance(), "LN_ESCROW_BAL"));
            legacySums.get("ltv_percent").add(LegacyValues.optionalAmount(legacy.getLtvPercent(), "LN_LTV_PCT"));
            legacySums.get("appraised_value").add(LegacyValues.optionalAmount(legacy.getAppraisedValue(), "PROP_APRS_VAL"));
            modernSums.get("original_amount").add(account.getOriginalAmount());
            modernSums.get("current_balance").add(account.getCurrentBalance());
            modernSums.get("interest_rate").add(account.getInterestRate());
            modernSums.get("monthly_payment").add(account.getMonthlyPayment());
            modernSums.get("escrow_balance").add(account.getEscrowBalance());
            modernSums.get("ltv_percent").add(account.getLtvPercent());
            modernSums.get("appraised_value").add(account.getAppraisedValue());
        }
        copySums(table, legacySums, modernSums);
        report.addCriterion("5 loan accounts migrated with correct borrower_id and product_id",
                table.getMapped() == 5 && fkProblems.isEmpty(),
                "loan_accounts mapped=" + table.getMapped() + " of legacy=" + table.getLegacyCount()
                        + skippedDetail(table)
                        + (fkProblems.isEmpty() ? "" : "; FK mismatches: " + fkProblems));
    }

    private void validatePayments(TableReport table, MigrationReport report) {
        table.setMapped(idMap.count(MigrationIdMap.PAYMENT));
        Map<String, List<BigDecimal>> legacySums = newSumBuckets("total_amount", "principal_amount",
                "interest_amount", "escrow_amount", "late_fee");
        Map<String, List<BigDecimal>> modernSums = newSumBuckets("total_amount", "principal_amount",
                "interest_amount", "escrow_amount", "late_fee");
        List<String> fkProblems = new ArrayList<>();
        for (LegacyPayment legacy : legacyPayments.findAll()) {
            Optional<Payment> found = idMap.findModernId(MigrationIdMap.PAYMENT, legacy.getPaymentSequenceNumber())
                    .flatMap(payments::findById);
            if (found.isEmpty()) {
                continue;
            }
            Payment payment = found.get();
            if (!payment.getLoanAccount().getAccountNumber().equals(legacy.getLoanAccountNumber())) {
                fkProblems.add(legacy.getPaymentSequenceNumber() + " loan_account_id -> "
                        + payment.getLoanAccount().getAccountNumber()
                        + " expected " + legacy.getLoanAccountNumber());
            }
            legacySums.get("total_amount").add(LegacyValues.optionalAmount(legacy.getTotalAmount(), "PMT_AMT"));
            legacySums.get("principal_amount").add(LegacyValues.optionalAmount(legacy.getPrincipalAmount(), "PMT_PRIN_AMT"));
            legacySums.get("interest_amount").add(LegacyValues.optionalAmount(legacy.getInterestAmount(), "PMT_INT_AMT"));
            legacySums.get("escrow_amount").add(LegacyValues.optionalAmount(legacy.getEscrowAmount(), "PMT_ESCROW_AMT"));
            legacySums.get("late_fee").add(LegacyValues.optionalAmount(legacy.getLateFee(), "PMT_LATE_FEE"));
            modernSums.get("total_amount").add(payment.getTotalAmount());
            modernSums.get("principal_amount").add(payment.getPrincipalAmount());
            modernSums.get("interest_amount").add(payment.getInterestAmount());
            modernSums.get("escrow_amount").add(payment.getEscrowAmount());
            modernSums.get("late_fee").add(payment.getLateFee());
        }
        copySums(table, legacySums, modernSums);
        report.addCriterion("10 payments migrated with correct loan_account_id",
                table.getMapped() == 10 && fkProblems.isEmpty(),
                "payments mapped=" + table.getMapped() + " of legacy=" + table.getLegacyCount()
                        + skippedDetail(table)
                        + (fkProblems.isEmpty() ? "" : "; FK mismatches: " + fkProblems));
    }

    private static Map<String, List<BigDecimal>> newSumBuckets(String... columns) {
        Map<String, List<BigDecimal>> buckets = new LinkedHashMap<>();
        for (String column : columns) {
            buckets.put(column, new ArrayList<>());
        }
        return buckets;
    }

    private static void copySums(TableReport table, Map<String, List<BigDecimal>> legacySums,
                                 Map<String, List<BigDecimal>> modernSums) {
        legacySums.forEach((column, values) -> {
            table.addLegacySum(column, sum(values));
            table.addModernSum(column, sum(modernSums.get(column)));
        });
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String skippedDetail(TableReport table) {
        if (table.getSkipped().isEmpty()) {
            return "";
        }
        return "; skipped as malformed: " + table.getSkipped().stream()
                .map(skip -> skip.legacyId() + " (" + skip.reason() + ")")
                .toList();
    }
}
