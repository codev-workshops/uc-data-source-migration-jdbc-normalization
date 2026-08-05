package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Copies data from the legacy CDW_* tables into the modern normalized tables on
 * application startup, applying the transformations documented in
 * {@code data/mappings/column_mappings.md}:
 *
 * <ul>
 *   <li>{@code MM/DD/YYYY} strings &rarr; {@link LocalDate}/{@link LocalDateTime}</li>
 *   <li>comma-formatted amount strings &rarr; {@link BigDecimal}</li>
 *   <li>integer strings &rarr; {@link Integer}</li>
 *   <li>cryptic codes &rarr; UPPERCASE modern enum values (e.g. ACT&rarr;ACTIVE)</li>
 *   <li>legacy string IDs &rarr; foreign keys to modern auto-increment PKs</li>
 *   <li>denormalized borrower fields on loan accounts are dropped in favour of the FK</li>
 * </ul>
 *
 * <p>The runner is guarded by the {@code migration.enabled} property and is
 * idempotent: it does nothing if the modern tables are already populated.
 */
@Component
@ConditionalOnProperty(name = "migration.enabled", havingValue = "true", matchIfMissing = true)
@SuppressWarnings("deprecation") // intentionally reads the deprecated legacy entities as the migration source
public class DataMigrationService implements ApplicationRunner {

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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrate();
    }

    /**
     * Runs the legacy &rarr; modern migration. Safe to call repeatedly: if any
     * modern data already exists, the method returns without changes.
     */
    @Transactional
    public void migrate() {
        if (borrowerRepository.count() > 0
                || loanProductRepository.count() > 0
                || loanAccountRepository.count() > 0
                || paymentRepository.count() > 0) {
            log.info("Modern tables already populated; skipping migration.");
            return;
        }

        Map<String, Borrower> borrowersByExternalId = migrateBorrowers();
        Map<String, LoanProduct> productsByCode = migrateProducts();
        Map<String, LoanAccount> accountsByNumber =
                migrateLoanAccounts(borrowersByExternalId, productsByCode);
        int payments = migratePayments(accountsByNumber);

        log.info("Migration complete: {} borrowers, {} products, {} loan accounts, {} payments.",
                borrowersByExternalId.size(), productsByCode.size(), accountsByNumber.size(), payments);
    }

    private Map<String, Borrower> migrateBorrowers() {
        Map<String, Borrower> byExternalId = new HashMap<>();
        for (LegacyBorrower legacy : legacyBorrowerRepository.findAll()) {
            Borrower b = new Borrower();
            b.setExternalId(legacy.getBorrowerId());
            b.setFirstName(legacy.getFirstName());
            b.setLastName(legacy.getLastName());
            b.setMiddleInitial(blankToNull(legacy.getMiddleInitial()));
            b.setSsnHash(legacy.getSsnEncrypted());
            b.setDateOfBirth(parseDate(legacy.getDateOfBirth()));
            b.setAddressLine1(legacy.getAddressLine1());
            b.setAddressLine2(blankToNull(legacy.getAddressLine2()));
            b.setCity(legacy.getCity());
            b.setState(legacy.getStateCode());
            b.setZipCode(legacy.getZipCode());
            b.setPhone(legacy.getPhoneNumber());
            b.setEmail(legacy.getEmail());
            b.setCreditScore(parseInteger(legacy.getCreditScore()));
            b.setEmploymentStatus(legacy.getEmploymentStatus());
            b.setAnnualIncome(parseAmount(legacy.getAnnualIncome()));
            b.setStatus(expandBorrowerStatus(legacy.getStatusCode()));
            b.setCreatedAt(parseTimestamp(legacy.getCreatedDate()));
            b.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate()));
            b = borrowerRepository.save(b);
            byExternalId.put(b.getExternalId(), b);
        }
        return byExternalId;
    }

    private Map<String, LoanProduct> migrateProducts() {
        Map<String, LoanProduct> byCode = new HashMap<>();
        for (LegacyLoanProduct legacy : legacyLoanProductRepository.findAll()) {
            LoanProduct p = new LoanProduct();
            p.setCode(legacy.getProductCode());
            p.setName(legacy.getDescription());
            p.setType(legacy.getTypeCode());
            p.setTermMonths(parseInteger(legacy.getTermMonths()));
            p.setRateType(legacy.getRateType());
            p.setMinAmount(parseAmount(legacy.getMinAmount()));
            p.setMaxAmount(parseAmount(legacy.getMaxAmount()));
            p.setActive("ACT".equals(legacy.getStatusCode()));
            p.setEffectiveDate(parseDate(legacy.getEffectiveDate()));
            p.setExpirationDate(parseDate(legacy.getExpirationDate()));
            p = loanProductRepository.save(p);
            byCode.put(p.getCode(), p);
        }
        return byCode;
    }

    private Map<String, LoanAccount> migrateLoanAccounts(Map<String, Borrower> borrowersByExternalId,
                                                         Map<String, LoanProduct> productsByCode) {
        Map<String, LoanAccount> byNumber = new HashMap<>();
        for (LegacyLoanAccount legacy : legacyLoanAccountRepository.findAll()) {
            LoanAccount a = new LoanAccount();
            a.setAccountNumber(legacy.getLoanAccountNumber());

            Borrower borrower = borrowersByExternalId.get(legacy.getBorrowerId());
            if (borrower == null) {
                throw new IllegalStateException(
                        "No modern borrower for legacy BORR_ID " + legacy.getBorrowerId());
            }
            a.setBorrower(borrower);

            LoanProduct product = productsByCode.get(legacy.getProductCode());
            if (product == null) {
                throw new IllegalStateException(
                        "No modern product for legacy PROD_CD " + legacy.getProductCode());
            }
            a.setProduct(product);

            a.setOriginalAmount(parseAmount(legacy.getOriginalAmount()));
            a.setCurrentBalance(parseAmount(legacy.getCurrentBalance()));
            a.setInterestRate(parseDecimal(legacy.getInterestRate()));
            a.setTermMonths(parseInteger(legacy.getTermMonths()));
            a.setMonthlyPayment(parseAmount(legacy.getMonthlyPayment()));
            a.setOriginationDate(parseDate(legacy.getOriginationDate()));
            a.setMaturityDate(parseDate(legacy.getMaturityDate()));
            a.setFirstPaymentDate(parseDate(legacy.getFirstPaymentDate()));
            a.setNextPaymentDate(parseDate(legacy.getNextPaymentDate()));
            a.setStatus(expandLoanStatus(legacy.getStatusCode()));
            a.setDelinquencyDays(parseInteger(legacy.getDelinquencyDays()));
            a.setEscrowBalance(parseAmount(legacy.getEscrowBalance()));
            a.setLtvPercent(parseDecimal(legacy.getLtvPercent()));
            a.setPropertyAddress(legacy.getPropertyAddress());
            a.setPropertyCity(legacy.getPropertyCity());
            a.setPropertyState(legacy.getPropertyState());
            a.setPropertyZip(legacy.getPropertyZip());
            a.setPropertyType(expandPropertyType(legacy.getPropertyType()));
            a.setAppraisedValue(parseAmount(legacy.getAppraisedValue()));
            a.setCreatedAt(parseTimestamp(legacy.getCreatedDate()));
            a.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate()));
            a = loanAccountRepository.save(a);
            byNumber.put(a.getAccountNumber(), a);
        }
        return byNumber;
    }

    private int migratePayments(Map<String, LoanAccount> accountsByNumber) {
        int count = 0;
        for (LegacyPayment legacy : legacyPaymentRepository.findAll()) {
            LoanAccount account = accountsByNumber.get(legacy.getLoanAccountNumber());
            if (account == null) {
                throw new IllegalStateException(
                        "No modern loan account for legacy LN_ACCT_NBR " + legacy.getLoanAccountNumber());
            }
            Payment p = new Payment();
            p.setExternalId(legacy.getPaymentSequenceNumber());
            p.setLoanAccount(account);
            p.setPaymentDate(parseDate(legacy.getPaymentDate()));
            p.setTotalAmount(parseAmount(legacy.getTotalAmount()));
            p.setPrincipalAmount(parseAmount(legacy.getPrincipalAmount()));
            p.setInterestAmount(parseAmount(legacy.getInterestAmount()));
            p.setEscrowAmount(parseAmount(legacy.getEscrowAmount()));
            p.setLateFee(parseAmount(legacy.getLateFee()));
            p.setType(expandPaymentType(legacy.getTypeCode()));
            p.setStatus(expandPaymentStatus(legacy.getStatusCode()));
            p.setReceivedDate(parseDate(legacy.getReceivedDate()));
            p.setProcessedDate(parseDate(legacy.getProcessedDate()));
            p.setCreatedAt(parseTimestamp(legacy.getCreatedDate()));
            p.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate()));
            paymentRepository.save(p);
            count++;
        }
        return count;
    }

    // =========================================================================
    // Transformation helpers
    // =========================================================================

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value.trim(), LEGACY_DATE);
    }

    private static LocalDateTime parseTimestamp(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.atStartOfDay();
    }

    /** Strips thousands-separators and parses to a decimal. */
    private static BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) return null;
        return new BigDecimal(value.replace(",", "").trim());
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        return new BigDecimal(value.trim());
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value.trim());
    }

    private static String expandBorrowerStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> code;
        };
    }

    private static String expandLoanStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> code;
        };
    }

    private static String expandPaymentType(String code) {
        if (code == null) return null;
        return switch (code) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> code;
        };
    }

    private static String expandPaymentStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> code;
        };
    }

    /**
     * Property type is stored in the modern schema as its human-readable
     * expansion (the same value the API exposes), rather than a cryptic code.
     */
    private static String expandPropertyType(String code) {
        if (code == null) return null;
        return switch (code) {
            case "SFR" -> "Single Family Residence";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family Residence";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }
}
