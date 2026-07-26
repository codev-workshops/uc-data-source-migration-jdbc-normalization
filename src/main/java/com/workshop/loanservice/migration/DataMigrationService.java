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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Migrates the legacy CDW tables into the modern normalized schema:
 * parses string-encoded dates and amounts, expands short codes and resolves
 * legacy string keys into generated foreign keys.
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
     * Runs the full migration. Already migrated records are skipped, so the
     * migration is idempotent and can be re-run safely.
     *
     * @return row counts per migrated table
     */
    @Transactional
    public MigrationReport migrate() {
        Map<String, Borrower> borrowers = migrateBorrowers();
        Map<String, LoanProduct> products = migrateProducts();
        Map<String, LoanAccount> accounts = migrateLoanAccounts(borrowers, products);
        int payments = migratePayments(accounts);

        MigrationReport report = new MigrationReport(borrowers.size(), products.size(),
                accounts.size(), payments);
        log.info("Legacy to modern migration complete: {}", report);
        return report;
    }

    private Map<String, Borrower> migrateBorrowers() {
        Map<String, Borrower> byExternalId = new HashMap<>();
        for (LegacyBorrower legacy : legacyBorrowerRepository.findAll()) {
            Borrower borrower = borrowerRepository.findByExternalId(legacy.getBorrowerId())
                    .orElseGet(Borrower::new);
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
            byExternalId.put(borrower.getExternalId(), borrowerRepository.save(borrower));
        }
        return byExternalId;
    }

    private Map<String, LoanProduct> migrateProducts() {
        Map<String, LoanProduct> byCode = new HashMap<>();
        for (LegacyLoanProduct legacy : legacyLoanProductRepository.findAll()) {
            LoanProduct product = loanProductRepository.findByCode(legacy.getProductCode())
                    .orElseGet(LoanProduct::new);
            product.setCode(legacy.getProductCode());
            product.setName(legacy.getDescription());
            product.setType(legacy.getTypeCode());
            product.setTermMonths(parseInteger(legacy.getTermMonths()));
            product.setRateType(legacy.getRateType());
            product.setMinAmount(parseAmount(legacy.getMinAmount()));
            product.setMaxAmount(parseAmount(legacy.getMaxAmount()));
            product.setActive("ACT".equals(legacy.getStatusCode()));
            product.setEffectiveDate(parseDate(legacy.getEffectiveDate()));
            product.setExpirationDate(parseDate(legacy.getExpirationDate()));
            byCode.put(product.getCode(), loanProductRepository.save(product));
        }
        return byCode;
    }

    private Map<String, LoanAccount> migrateLoanAccounts(Map<String, Borrower> borrowers,
                                                         Map<String, LoanProduct> products) {
        Map<String, LoanAccount> byAccountNumber = new HashMap<>();
        for (LegacyLoanAccount legacy : legacyLoanAccountRepository.findAll()) {
            Borrower borrower = borrowers.get(legacy.getBorrowerId());
            LoanProduct product = products.get(legacy.getProductCode());
            if (borrower == null || product == null) {
                log.warn("Skipping loan {}: unresolved borrower {} or product {}",
                        legacy.getLoanAccountNumber(), legacy.getBorrowerId(), legacy.getProductCode());
                continue;
            }

            LoanAccount account = loanAccountRepository
                    .findByAccountNumber(legacy.getLoanAccountNumber())
                    .orElseGet(LoanAccount::new);
            account.setAccountNumber(legacy.getLoanAccountNumber());
            account.setBorrower(borrower);
            account.setProduct(product);
            account.setOriginalAmount(parseAmount(legacy.getOriginalAmount()));
            account.setCurrentBalance(parseAmount(legacy.getCurrentBalance()));
            account.setInterestRate(parseAmount(legacy.getInterestRate()));
            account.setTermMonths(parseInteger(legacy.getTermMonths()));
            account.setMonthlyPayment(parseAmount(legacy.getMonthlyPayment()));
            account.setOriginationDate(parseDate(legacy.getOriginationDate()));
            account.setMaturityDate(parseDate(legacy.getMaturityDate()));
            account.setFirstPaymentDate(parseDate(legacy.getFirstPaymentDate()));
            account.setNextPaymentDate(parseDate(legacy.getNextPaymentDate()));
            account.setStatus(expandLoanStatus(legacy.getStatusCode()));
            account.setDelinquencyDays(parseInteger(legacy.getDelinquencyDays()));
            account.setEscrowBalance(parseAmount(legacy.getEscrowBalance()));
            account.setLtvPercent(parseAmount(legacy.getLtvPercent()));
            account.setPropertyAddress(legacy.getPropertyAddress());
            account.setPropertyCity(legacy.getPropertyCity());
            account.setPropertyState(legacy.getPropertyState());
            account.setPropertyZip(legacy.getPropertyZip());
            account.setPropertyType(expandPropertyType(legacy.getPropertyType()));
            account.setAppraisedValue(parseAmount(legacy.getAppraisedValue()));
            account.setCreatedAt(parseTimestamp(legacy.getCreatedDate()));
            account.setUpdatedAt(parseTimestamp(legacy.getUpdatedDate()));
            byAccountNumber.put(account.getAccountNumber(), loanAccountRepository.save(account));
        }
        return byAccountNumber;
    }

    private int migratePayments(Map<String, LoanAccount> accounts) {
        Map<String, Payment> existing = new HashMap<>();
        paymentRepository.findAll().forEach(p -> existing.put(p.getExternalId(), p));

        int migrated = 0;
        for (LegacyPayment legacy : legacyPaymentRepository.findAll()) {
            LoanAccount account = accounts.get(legacy.getLoanAccountNumber());
            if (account == null) {
                log.warn("Skipping payment {}: unresolved loan account {}",
                        legacy.getPaymentSequenceNumber(), legacy.getLoanAccountNumber());
                continue;
            }

            Payment payment = existing.getOrDefault(legacy.getPaymentSequenceNumber(), new Payment());
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
            migrated++;
        }
        return migrated;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim(), LEGACY_DATE);
        } catch (DateTimeParseException e) {
            log.warn("Unparseable legacy date '{}'", value);
            return null;
        }
    }

    private LocalDateTime parseTimestamp(String value) {
        LocalDate date = parseDate(value);
        return date != null ? date.atStartOfDay() : null;
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            log.warn("Unparseable legacy amount '{}'", value);
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Unparseable legacy integer '{}'", value);
            return null;
        }
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
            case "SFR" -> "Single Family Residence";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family Residence";
            case "TWN" -> "Townhouse";
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

    /**
     * Row counts written into the modern schema, used for reconciliation.
     */
    public record MigrationReport(int borrowers, int loanProducts, int loanAccounts, int payments) {
    }
}
