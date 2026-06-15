package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.*;
import com.workshop.loanservice.repository.*;
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
import java.util.List;
import java.util.Map;

/**
 * Reads legacy CDW records, transforms them into the modern normalized schema,
 * and persists them. Runs once after Flyway has created both schemas (V1-V3).
 */
@Component
@Order(1)
public class DataMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);
    private static final DateTimeFormatter LEGACY_DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final LegacyBorrowerRepository legacyBorrowerRepo;
    private final LegacyLoanProductRepository legacyProductRepo;
    private final LegacyLoanAccountRepository legacyLoanRepo;
    private final LegacyPaymentRepository legacyPaymentRepo;

    private final BorrowerRepository borrowerRepo;
    private final LoanProductRepository productRepo;
    private final LoanAccountRepository loanAccountRepo;
    private final PaymentRepository paymentRepo;

    public DataMigrationRunner(LegacyBorrowerRepository legacyBorrowerRepo,
                               LegacyLoanProductRepository legacyProductRepo,
                               LegacyLoanAccountRepository legacyLoanRepo,
                               LegacyPaymentRepository legacyPaymentRepo,
                               BorrowerRepository borrowerRepo,
                               LoanProductRepository productRepo,
                               LoanAccountRepository loanAccountRepo,
                               PaymentRepository paymentRepo) {
        this.legacyBorrowerRepo = legacyBorrowerRepo;
        this.legacyProductRepo = legacyProductRepo;
        this.legacyLoanRepo = legacyLoanRepo;
        this.legacyPaymentRepo = legacyPaymentRepo;
        this.borrowerRepo = borrowerRepo;
        this.productRepo = productRepo;
        this.loanAccountRepo = loanAccountRepo;
        this.paymentRepo = paymentRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (borrowerRepo.count() > 0) {
            log.info("Modern tables already populated — skipping migration");
            return;
        }

        log.info("Starting legacy \u2192 modern data migration...");

        Map<String, Borrower> borrowerMap = migrateBorrowers();
        Map<String, LoanProduct> productMap = migrateLoanProducts();
        Map<String, LoanAccount> loanMap = migrateLoanAccounts(borrowerMap, productMap);
        migratePayments(loanMap);

        log.info("Migration complete! Migrated {} borrowers, {} products, {} loans, {} payments",
                borrowerMap.size(), productMap.size(), loanMap.size(), paymentRepo.count());
    }

    private Map<String, Borrower> migrateBorrowers() {
        Map<String, Borrower> map = new HashMap<>();
        for (LegacyBorrower legacy : legacyBorrowerRepo.findAll()) {
            Borrower b = new Borrower();
            b.setExternalId(legacy.getBorrowerId());
            b.setFirstName(legacy.getFirstName());
            b.setLastName(legacy.getLastName());
            b.setMiddleInitial(legacy.getMiddleInitial());
            b.setSsnHash(legacy.getSsnEncrypted());
            b.setDateOfBirth(parseLegacyDate(legacy.getDateOfBirth()));
            b.setAddressLine1(legacy.getAddressLine1());
            b.setAddressLine2(legacy.getAddressLine2());
            b.setCity(legacy.getCity());
            b.setState(legacy.getStateCode());
            b.setZipCode(legacy.getZipCode());
            b.setPhone(legacy.getPhoneNumber());
            b.setEmail(legacy.getEmail());
            b.setCreditScore(parseLegacyInt(legacy.getCreditScore()));
            b.setEmploymentStatus(legacy.getEmploymentStatus());
            b.setAnnualIncome(parseLegacyAmount(legacy.getAnnualIncome()));
            b.setStatus(expandBorrowerStatus(legacy.getStatusCode()));
            b.setCreatedAt(parseLegacyTimestamp(legacy.getCreatedDate()));
            b.setUpdatedAt(parseLegacyTimestamp(legacy.getUpdatedDate()));
            borrowerRepo.save(b);
            map.put(b.getExternalId(), b);
        }
        return map;
    }

    private Map<String, LoanProduct> migrateLoanProducts() {
        Map<String, LoanProduct> map = new HashMap<>();
        for (LegacyLoanProduct legacy : legacyProductRepo.findAll()) {
            LoanProduct p = new LoanProduct();
            p.setCode(legacy.getProductCode());
            p.setName(legacy.getDescription());
            p.setType(legacy.getTypeCode());
            p.setTermMonths(parseLegacyInt(legacy.getTermMonths()));
            p.setRateType(legacy.getRateType());
            p.setMinAmount(parseLegacyAmount(legacy.getMinAmount()));
            p.setMaxAmount(parseLegacyAmount(legacy.getMaxAmount()));
            p.setIsActive("ACT".equals(legacy.getStatusCode()));
            p.setEffectiveDate(parseLegacyDate(legacy.getEffectiveDate()));
            p.setExpirationDate(parseLegacyDate(legacy.getExpirationDate()));
            productRepo.save(p);
            map.put(p.getCode(), p);
        }
        return map;
    }

    private Map<String, LoanAccount> migrateLoanAccounts(Map<String, Borrower> borrowerMap,
                                                         Map<String, LoanProduct> productMap) {
        Map<String, LoanAccount> map = new HashMap<>();
        for (LegacyLoanAccount legacy : legacyLoanRepo.findAll()) {
            LoanAccount a = new LoanAccount();
            a.setAccountNumber(legacy.getLoanAccountNumber());
            a.setBorrower(borrowerMap.get(legacy.getBorrowerId()));
            a.setProduct(productMap.get(legacy.getProductCode()));
            a.setOriginalAmount(parseLegacyAmount(legacy.getOriginalAmount()));
            a.setCurrentBalance(parseLegacyAmount(legacy.getCurrentBalance()));
            a.setInterestRate(parseLegacyDecimal(legacy.getInterestRate()));
            a.setTermMonths(parseLegacyInt(legacy.getTermMonths()));
            a.setMonthlyPayment(parseLegacyAmount(legacy.getMonthlyPayment()));
            a.setOriginationDate(parseLegacyDate(legacy.getOriginationDate()));
            a.setMaturityDate(parseLegacyDate(legacy.getMaturityDate()));
            a.setFirstPaymentDate(parseLegacyDate(legacy.getFirstPaymentDate()));
            a.setNextPaymentDate(parseLegacyDate(legacy.getNextPaymentDate()));
            a.setStatus(expandLoanStatus(legacy.getStatusCode()));
            a.setDelinquencyDays(parseLegacyInt(legacy.getDelinquencyDays()));
            a.setEscrowBalance(parseLegacyAmount(legacy.getEscrowBalance()));
            a.setLtvPercent(parseLegacyDecimal(legacy.getLtvPercent()));
            a.setPropertyAddress(legacy.getPropertyAddress());
            a.setPropertyCity(legacy.getPropertyCity());
            a.setPropertyState(legacy.getPropertyState());
            a.setPropertyZip(legacy.getPropertyZip());
            a.setPropertyType(expandPropertyType(legacy.getPropertyType()));
            a.setAppraisedValue(parseLegacyAmount(legacy.getAppraisedValue()));
            a.setCreatedAt(parseLegacyTimestamp(legacy.getCreatedDate()));
            a.setUpdatedAt(parseLegacyTimestamp(legacy.getUpdatedDate()));
            loanAccountRepo.save(a);
            map.put(a.getAccountNumber(), a);
        }
        return map;
    }

    private void migratePayments(Map<String, LoanAccount> loanMap) {
        for (LegacyPayment legacy : legacyPaymentRepo.findAll()) {
            Payment p = new Payment();
            p.setExternalId(legacy.getPaymentSequenceNumber());
            p.setLoanAccount(loanMap.get(legacy.getLoanAccountNumber()));
            p.setPaymentDate(parseLegacyDate(legacy.getPaymentDate()));
            p.setTotalAmount(parseLegacyAmount(legacy.getTotalAmount()));
            p.setPrincipalAmount(parseLegacyAmount(legacy.getPrincipalAmount()));
            p.setInterestAmount(parseLegacyAmount(legacy.getInterestAmount()));
            p.setEscrowAmount(parseLegacyAmount(legacy.getEscrowAmount()));
            p.setLateFee(parseLegacyAmount(legacy.getLateFee()));
            p.setType(expandPaymentType(legacy.getTypeCode()));
            p.setStatus(expandPaymentStatus(legacy.getStatusCode()));
            p.setReceivedDate(parseLegacyDate(legacy.getReceivedDate()));
            p.setProcessedDate(parseLegacyDate(legacy.getProcessedDate()));
            p.setCreatedAt(parseLegacyTimestamp(legacy.getCreatedDate()));
            p.setUpdatedAt(parseLegacyTimestamp(legacy.getUpdatedDate()));
            paymentRepo.save(p);
        }
    }

    // =========================================================================
    // Parsing helpers
    // =========================================================================

    private LocalDate parseLegacyDate(String val) {
        if (val == null || val.isBlank()) return null;
        return LocalDate.parse(val.trim(), LEGACY_DATE_FMT);
    }

    private LocalDateTime parseLegacyTimestamp(String val) {
        LocalDate date = parseLegacyDate(val);
        return date == null ? null : date.atStartOfDay();
    }

    private BigDecimal parseLegacyAmount(String val) {
        if (val == null || val.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(val.replace(",", "").trim());
    }

    private BigDecimal parseLegacyDecimal(String val) {
        if (val == null || val.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(val.trim());
    }

    private Integer parseLegacyInt(String val) {
        if (val == null || val.isBlank()) return null;
        return Integer.parseInt(val.trim());
    }

    // =========================================================================
    // Code expansion helpers
    // =========================================================================

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
}
