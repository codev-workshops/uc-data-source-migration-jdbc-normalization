package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * ETL that copies the legacy CDW records into the modern normalized schema,
 * applying the transformations described in {@code data/mappings/column_mappings.md}.
 *
 * <p>The whole migration runs inside a single modern-data-source transaction, so a
 * failure anywhere leaves the modern schema untouched. Each record is keyed by its
 * legacy business key (borrower external id, product code, loan account number,
 * payment sequence number), which makes repeated runs idempotent.
 */
@Service
public class DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);
    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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
     * Migrates every legacy record in dependency order:
     * borrowers → loan products → loan accounts → payments.
     */
    @Transactional("modernTransactionManager")
    public MigrationReport migrateAll() {
        MigrationReport report = new MigrationReport();
        migrateBorrowers(report);
        migrateLoanProducts(report);
        migrateLoanAccounts(report);
        migratePayments(report);
        log.info("Legacy → modern migration finished: {}", report);
        return report;
    }

    private void migrateBorrowers(MigrationReport report) {
        for (LegacyBorrower legacy : legacyBorrowerRepository.findAll()) {
            if (borrowerRepository.findByExternalId(legacy.getBorrowerId()).isPresent()) {
                report.skippedBorrowers++;
                continue;
            }
            Borrower borrower = new Borrower();
            borrower.setExternalId(legacy.getBorrowerId());
            borrower.setFirstName(legacy.getFirstName());
            borrower.setLastName(legacy.getLastName());
            borrower.setMiddleInitial(legacy.getMiddleInitial());
            borrower.setSsnHash(legacy.getSsnEncrypted());
            borrower.setDateOfBirth(parseDate(legacy.getDateOfBirth()));
            borrower.setAddressLine1(legacy.getAddressLine1());
            borrower.setAddressLine2(legacy.getAddressLine2());
            borrower.setCity(legacy.getCity());
            borrower.setState(legacy.getStateCode());
            borrower.setZipCode(legacy.getZipCode());
            borrower.setPhone(legacy.getPhoneNumber());
            borrower.setEmail(legacy.getEmail());
            borrower.setCreditScore(parseInteger(legacy.getCreditScore()));
            borrower.setEmploymentStatus(legacy.getEmploymentStatus());
            borrower.setAnnualIncome(parseAmount(legacy.getAnnualIncome()));
            borrower.setStatus(expandBorrowerStatus(legacy.getStatusCode()));
            borrower.setCreatedAt(parseTimestamp(legacy.getCreatedDate()));
            borrower.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate()));
            borrowerRepository.save(borrower);
            report.migratedBorrowers++;
        }
    }

    private void migrateLoanProducts(MigrationReport report) {
        for (LegacyLoanProduct legacy : legacyLoanProductRepository.findAll()) {
            if (loanProductRepository.findByCode(legacy.getProductCode()).isPresent()) {
                report.skippedProducts++;
                continue;
            }
            LoanProduct product = new LoanProduct();
            product.setCode(legacy.getProductCode());
            product.setName(legacy.getDescription());
            product.setType(legacy.getTypeCode());
            product.setTermMonths(parseInteger(legacy.getTermMonths()));
            product.setRateType(legacy.getRateType());
            product.setMinAmount(parseAmount(legacy.getMinAmount()));
            product.setMaxAmount(parseAmount(legacy.getMaxAmount()));
            product.setIsActive("ACT".equals(legacy.getStatusCode()));
            product.setEffectiveDate(parseDate(legacy.getEffectiveDate()));
            product.setExpirationDate(parseDate(legacy.getExpirationDate()));
            loanProductRepository.save(product);
            report.migratedProducts++;
        }
    }

    private void migrateLoanAccounts(MigrationReport report) {
        for (LegacyLoanAccount legacy : legacyLoanAccountRepository.findAll()) {
            if (loanAccountRepository.findByAccountNumber(legacy.getLoanAccountNumber()).isPresent()) {
                report.skippedLoanAccounts++;
                continue;
            }
            Borrower borrower = borrowerRepository.findByExternalId(legacy.getBorrowerId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No modern borrower for legacy BORR_ID " + legacy.getBorrowerId()));
            LoanProduct product = loanProductRepository.findByCode(legacy.getProductCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "No modern loan product for legacy PROD_CD " + legacy.getProductCode()));

            LoanAccount account = new LoanAccount();
            account.setAccountNumber(legacy.getLoanAccountNumber());
            account.setBorrower(borrower);
            account.setProduct(product);
            account.setOriginalAmount(parseAmount(legacy.getOriginalAmount()));
            account.setCurrentBalance(parseAmount(legacy.getCurrentBalance()));
            account.setInterestRate(parseDecimal(legacy.getInterestRate()));
            account.setTermMonths(parseInteger(legacy.getTermMonths()));
            account.setMonthlyPayment(parseAmount(legacy.getMonthlyPayment()));
            account.setOriginationDate(parseDate(legacy.getOriginationDate()));
            account.setMaturityDate(parseDate(legacy.getMaturityDate()));
            account.setFirstPaymentDate(parseDate(legacy.getFirstPaymentDate()));
            account.setNextPaymentDate(parseDate(legacy.getNextPaymentDate()));
            account.setStatus(expandLoanStatus(legacy.getStatusCode()));
            account.setDelinquencyDays(parseInteger(legacy.getDelinquencyDays()));
            account.setEscrowBalance(parseAmount(legacy.getEscrowBalance()));
            account.setLtvPercent(parseDecimal(legacy.getLtvPercent()));
            account.setPropertyAddress(legacy.getPropertyAddress());
            account.setPropertyCity(legacy.getPropertyCity());
            account.setPropertyState(legacy.getPropertyState());
            account.setPropertyZip(legacy.getPropertyZip());
            account.setPropertyType(expandPropertyType(legacy.getPropertyType()));
            account.setAppraisedValue(parseAmount(legacy.getAppraisedValue()));
            account.setCreatedAt(parseTimestamp(legacy.getCreatedDate()));
            account.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate()));
            loanAccountRepository.save(account);
            report.migratedLoanAccounts++;
        }
    }

    private void migratePayments(MigrationReport report) {
        for (LegacyPayment legacy : legacyPaymentRepository.findAll()) {
            LoanAccount account = loanAccountRepository.findByAccountNumber(legacy.getLoanAccountNumber())
                    .orElseThrow(() -> new IllegalStateException(
                            "No modern loan account for legacy LN_ACCT_NBR " + legacy.getLoanAccountNumber()));
            boolean alreadyMigrated = paymentRepository
                    .findByLoanAccount_AccountNumberOrderByPaymentDateDesc(legacy.getLoanAccountNumber())
                    .stream()
                    .anyMatch(p -> legacy.getPaymentSequenceNumber().equals(p.getExternalId()));
            if (alreadyMigrated) {
                report.skippedPayments++;
                continue;
            }

            Payment payment = new Payment();
            payment.setExternalId(legacy.getPaymentSequenceNumber());
            payment.setLoanAccount(account);
            payment.setPaymentDate(parseDate(legacy.getPaymentDate()));
            payment.setTotalAmount(parseAmount(legacy.getTotalAmount()));
            payment.setPrincipalAmount(parseAmount(legacy.getPrincipalAmount()));
            payment.setInterestAmount(parseAmount(legacy.getInterestAmount()));
            payment.setEscrowAmount(parseAmount(legacy.getEscrowAmount()));
            payment.setLateFee(parseAmount(legacy.getLateFee()));
            payment.setType(expandPaymentType(legacy.getTypeCode()));
            payment.setStatus(expandPaymentStatus(legacy.getStatusCode()));
            payment.setReceivedDate(parseDate(legacy.getReceivedDate()));
            payment.setProcessedDate(parseDate(legacy.getProcessedDate()));
            payment.setCreatedAt(parseTimestamp(legacy.getCreatedDate()));
            payment.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate()));
            paymentRepository.save(payment);
            report.migratedPayments++;
        }
    }

    // =========================================================================
    // Transformation helpers
    // =========================================================================

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value.trim(), LEGACY_DATE);
    }

    private LocalDateTime parseTimestamp(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : LocalDateTime.of(date, LocalTime.MIDNIGHT);
    }

    /** Legacy amounts are strings such as "285,000" or "1,487.02"; blanks become ZERO. */
    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.replace(",", "").trim());
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim());
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value.trim());
    }

    private String expandBorrowerStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> code;
        };
    }

    private String expandLoanStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> code;
        };
    }

    private String expandPropertyType(String code) {
        if (code == null) return null;
        return switch (code) {
            case "SFR" -> "SINGLE_FAMILY_RESIDENCE";
            case "CND" -> "CONDOMINIUM";
            case "MFR" -> "MULTI_FAMILY_RESIDENCE";
            case "TWN" -> "TOWNHOUSE";
            default -> code;
        };
    }

    private String expandPaymentType(String code) {
        if (code == null) return null;
        return switch (code) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> code;
        };
    }

    private String expandPaymentStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> code;
        };
    }

    /** Counters returned by {@link #migrateAll()} and by the admin endpoint. */
    public static class MigrationReport {
        private int migratedBorrowers;
        private int migratedProducts;
        private int migratedLoanAccounts;
        private int migratedPayments;
        private int skippedBorrowers;
        private int skippedProducts;
        private int skippedLoanAccounts;
        private int skippedPayments;

        public int getMigratedBorrowers() { return migratedBorrowers; }
        public int getMigratedProducts() { return migratedProducts; }
        public int getMigratedLoanAccounts() { return migratedLoanAccounts; }
        public int getMigratedPayments() { return migratedPayments; }
        public int getSkippedBorrowers() { return skippedBorrowers; }
        public int getSkippedProducts() { return skippedProducts; }
        public int getSkippedLoanAccounts() { return skippedLoanAccounts; }
        public int getSkippedPayments() { return skippedPayments; }

        @Override
        public String toString() {
            return "migrated[borrowers=" + migratedBorrowers + ", products=" + migratedProducts
                    + ", loanAccounts=" + migratedLoanAccounts + ", payments=" + migratedPayments
                    + "], skipped[borrowers=" + skippedBorrowers + ", products=" + skippedProducts
                    + ", loanAccounts=" + skippedLoanAccounts + ", payments=" + skippedPayments + "]";
        }
    }
}
