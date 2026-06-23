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
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Migrates data from the legacy CDW tables into the modern normalized tables
 * at application startup. Reads legacy string-typed rows, transforms them into
 * proper Java types, resolves the legacy string identifiers into modern BIGINT
 * foreign keys, and writes the result via the modern repositories.
 *
 * <p>The modern columns store canonical values (e.g. {@code ACTIVE},
 * {@code REGULAR}); the service layer is responsible for any presentation
 * formatting needed by the API contract.</p>
 */
@Component
@Order(1)
public class DataMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("M/d/yyyy");

    private final LegacyBorrowerRepository legacyBorrowerRepository;
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    private final LegacyPaymentRepository legacyPaymentRepository;

    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    public DataMigrationRunner(LegacyBorrowerRepository legacyBorrowerRepository,
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
    public void run(String... args) {
        if (borrowerRepository.count() > 0) {
            log.info("Modern tables already populated; skipping data migration.");
            return;
        }

        log.info("Starting legacy -> modern data migration...");

        Map<String, Borrower> borrowersByExternalId = migrateBorrowers();
        Map<String, LoanProduct> productsByCode = migrateLoanProducts();
        Map<String, LoanAccount> accountsByNumber =
                migrateLoanAccounts(borrowersByExternalId, productsByCode);
        migratePayments(accountsByNumber);

        validate();
    }

    private Map<String, Borrower> migrateBorrowers() {
        Map<String, Borrower> result = new HashMap<>();
        for (LegacyBorrower legacy : legacyBorrowerRepository.findAll()) {
            Borrower b = new Borrower();
            b.setExternalId(legacy.getBorrowerId());
            b.setFirstName(legacy.getFirstName());
            b.setLastName(legacy.getLastName());
            b.setMiddleInitial(legacy.getMiddleInitial());
            b.setSsnHash(legacy.getSsnEncrypted());
            b.setDateOfBirth(parseDate(legacy.getDateOfBirth()));
            b.setAddressLine1(legacy.getAddressLine1());
            b.setAddressLine2(legacy.getAddressLine2());
            b.setCity(legacy.getCity());
            b.setState(legacy.getStateCode());
            b.setZipCode(legacy.getZipCode());
            b.setPhone(legacy.getPhoneNumber());
            b.setEmail(legacy.getEmail());
            b.setCreditScore(parseInteger(legacy.getCreditScore()));
            b.setEmploymentStatus(legacy.getEmploymentStatus());
            b.setAnnualIncome(parseAmount(legacy.getAnnualIncome()));
            b.setStatus(expandBorrowerStatus(legacy.getStatusCode()));
            b.setCreatedAt(parseDateTime(legacy.getCreatedDate()));
            b.setUpdatedAt(parseDateTime(legacy.getUpdatedDate()));
            result.put(b.getExternalId(), borrowerRepository.save(b));
        }
        log.info("Migrated {} borrowers", result.size());
        return result;
    }

    private Map<String, LoanProduct> migrateLoanProducts() {
        Map<String, LoanProduct> result = new HashMap<>();
        for (LegacyLoanProduct legacy : legacyLoanProductRepository.findAll()) {
            LoanProduct p = new LoanProduct();
            p.setCode(legacy.getProductCode());
            p.setName(legacy.getDescription());
            p.setType(legacy.getTypeCode());
            p.setTermMonths(parseInteger(legacy.getTermMonths()));
            p.setRateType(legacy.getRateType());
            p.setMinAmount(parseAmount(legacy.getMinAmount()));
            p.setMaxAmount(parseAmount(legacy.getMaxAmount()));
            p.setIsActive("ACT".equals(legacy.getStatusCode()));
            p.setEffectiveDate(parseDate(legacy.getEffectiveDate()));
            p.setExpirationDate(parseDate(legacy.getExpirationDate()));
            result.put(p.getCode(), loanProductRepository.save(p));
        }
        log.info("Migrated {} loan products", result.size());
        return result;
    }

    private Map<String, LoanAccount> migrateLoanAccounts(Map<String, Borrower> borrowers,
                                                         Map<String, LoanProduct> products) {
        Map<String, LoanAccount> result = new HashMap<>();
        for (LegacyLoanAccount legacy : legacyLoanAccountRepository.findAll()) {
            Borrower borrower = borrowers.get(legacy.getBorrowerId());
            if (borrower == null) {
                throw new IllegalStateException(
                        "No modern borrower for legacy BORR_ID=" + legacy.getBorrowerId());
            }
            LoanProduct product = products.get(legacy.getProductCode());
            if (product == null) {
                throw new IllegalStateException(
                        "No modern product for legacy PROD_CD=" + legacy.getProductCode());
            }

            LoanAccount a = new LoanAccount();
            a.setAccountNumber(legacy.getLoanAccountNumber());
            a.setBorrower(borrower);
            a.setProduct(product);
            a.setOriginalAmount(parseAmount(legacy.getOriginalAmount()));
            a.setCurrentBalance(parseAmount(legacy.getCurrentBalance()));
            a.setInterestRate(parseAmount(legacy.getInterestRate()));
            a.setTermMonths(parseInteger(legacy.getTermMonths()));
            a.setMonthlyPayment(parseAmount(legacy.getMonthlyPayment()));
            a.setOriginationDate(parseDate(legacy.getOriginationDate()));
            a.setMaturityDate(parseDate(legacy.getMaturityDate()));
            a.setFirstPaymentDate(parseDate(legacy.getFirstPaymentDate()));
            a.setNextPaymentDate(parseDate(legacy.getNextPaymentDate()));
            a.setStatus(expandLoanStatus(legacy.getStatusCode()));
            a.setDelinquencyDays(parseInteger(legacy.getDelinquencyDays()));
            a.setEscrowBalance(parseAmount(legacy.getEscrowBalance()));
            a.setLtvPercent(parseAmount(legacy.getLtvPercent()));
            a.setPropertyAddress(legacy.getPropertyAddress());
            a.setPropertyCity(legacy.getPropertyCity());
            a.setPropertyState(legacy.getPropertyState());
            a.setPropertyZip(legacy.getPropertyZip());
            a.setPropertyType(expandPropertyType(legacy.getPropertyType()));
            a.setAppraisedValue(parseAmount(legacy.getAppraisedValue()));
            a.setCreatedAt(parseDateTime(legacy.getCreatedDate()));
            a.setUpdatedAt(parseDateTime(legacy.getUpdatedDate()));
            result.put(a.getAccountNumber(), loanAccountRepository.save(a));
        }
        log.info("Migrated {} loan accounts", result.size());
        return result;
    }

    private void migratePayments(Map<String, LoanAccount> accounts) {
        int count = 0;
        for (LegacyPayment legacy : legacyPaymentRepository.findAll()) {
            LoanAccount account = accounts.get(legacy.getLoanAccountNumber());
            if (account == null) {
                throw new IllegalStateException(
                        "No modern loan account for legacy LN_ACCT_NBR=" + legacy.getLoanAccountNumber());
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
            p.setCreatedAt(parseDateTime(legacy.getCreatedDate()));
            p.setUpdatedAt(parseDateTime(legacy.getUpdatedDate()));
            paymentRepository.save(p);
            count++;
        }
        log.info("Migrated {} payments", count);
    }

    private void validate() {
        long borrowers = borrowerRepository.count();
        long products = loanProductRepository.count();
        long accounts = loanAccountRepository.count();
        long payments = paymentRepository.count();

        long legacyBorrowers = legacyBorrowerRepository.count();
        long legacyProducts = legacyLoanProductRepository.count();
        long legacyAccounts = legacyLoanAccountRepository.count();
        long legacyPayments = legacyPaymentRepository.count();

        if (borrowers != legacyBorrowers || products != legacyProducts
                || accounts != legacyAccounts || payments != legacyPayments) {
            throw new IllegalStateException(String.format(
                    "Migration row-count mismatch: borrowers %d/%d, products %d/%d, "
                            + "accounts %d/%d, payments %d/%d",
                    borrowers, legacyBorrowers, products, legacyProducts,
                    accounts, legacyAccounts, payments, legacyPayments));
        }

        log.info("Migration validated: {} borrowers, {} products, {} loan accounts, {} payments",
                borrowers, products, accounts, payments);
    }

    // ------------------------------------------------------------------
    // Type conversion helpers
    // ------------------------------------------------------------------

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim(), LEGACY_DATE);
    }

    private static LocalDateTime parseDateTime(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.atStartOfDay();
    }

    private static BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.replace(",", "").trim());
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    // ------------------------------------------------------------------
    // Code expansion (legacy code -> modern canonical value)
    // ------------------------------------------------------------------

    private static String expandBorrowerStatus(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> code;
        };
    }

    private static String expandLoanStatus(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> code;
        };
    }

    private static String expandPropertyType(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "SFR" -> "Single Family";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }

    private static String expandPaymentType(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> code;
        };
    }

    private static String expandPaymentStatus(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> code;
        };
    }
}
