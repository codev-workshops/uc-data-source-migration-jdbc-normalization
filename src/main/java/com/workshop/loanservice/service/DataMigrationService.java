package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.*;
import com.workshop.loanservice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Runs on startup to ETL data from legacy CDW tables into the modern normalized schema.
 * Migration order: borrowers -> loan products -> loan accounts -> payments (respecting FK deps).
 */
@Service
@Order(1)
public class DataMigrationService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);
    private static final DateTimeFormatter LEGACY_DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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
    public void run(String... args) {
        if (borrowerRepository.count() > 0) {
            log.info("Modern tables already populated, skipping migration.");
            return;
        }
        log.info("Starting legacy-to-modern data migration...");
        migrateBorrowers();
        migrateLoanProducts();
        migrateLoanAccounts();
        migratePayments();
        log.info("Migration complete: {} borrowers, {} products, {} accounts, {} payments",
                borrowerRepository.count(), loanProductRepository.count(),
                loanAccountRepository.count(), paymentRepository.count());
    }

    private void migrateBorrowers() {
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
        for (LegacyBorrower lb : legacyBorrowers) {
            Borrower b = new Borrower();
            b.setExternalId(lb.getBorrowerId());
            b.setFirstName(lb.getFirstName());
            b.setLastName(lb.getLastName());
            b.setMiddleInitial(blankToNull(lb.getMiddleInitial()));
            b.setSsnHash(lb.getSsnEncrypted());
            b.setDateOfBirth(parseLegacyDate(lb.getDateOfBirth()));
            b.setAddressLine1(lb.getAddressLine1());
            b.setAddressLine2(blankToNull(lb.getAddressLine2()));
            b.setCity(lb.getCity());
            b.setState(lb.getStateCode());
            b.setZipCode(lb.getZipCode());
            b.setPhone(lb.getPhoneNumber());
            b.setEmail(lb.getEmail());
            b.setCreditScore(parseLegacyInteger(lb.getCreditScore()));
            b.setEmploymentStatus(lb.getEmploymentStatus());
            b.setAnnualIncome(parseLegacyAmount(lb.getAnnualIncome()));
            b.setStatus(expandBorrowerStatus(lb.getStatusCode()));
            b.setCreatedAt(parseLegacyTimestamp(lb.getCreatedDate()));
            b.setUpdatedAt(parseLegacyTimestamp(lb.getUpdatedDate()));
            borrowerRepository.save(b);
        }
        log.info("Migrated {} borrowers", legacyBorrowers.size());
    }

    private void migrateLoanProducts() {
        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
        for (LegacyLoanProduct lp : legacyProducts) {
            LoanProduct p = new LoanProduct();
            p.setCode(lp.getProductCode());
            p.setName(lp.getDescription());
            p.setType(lp.getTypeCode());
            p.setTermMonths(parseLegacyInteger(lp.getTermMonths()));
            p.setRateType(lp.getRateType());
            p.setMinAmount(parseLegacyAmount(lp.getMinAmount()));
            p.setMaxAmount(parseLegacyAmount(lp.getMaxAmount()));
            p.setIsActive("ACT".equals(lp.getStatusCode()));
            p.setEffectiveDate(parseLegacyDate(lp.getEffectiveDate()));
            p.setExpirationDate(parseLegacyDate(lp.getExpirationDate()));
            loanProductRepository.save(p);
        }
        log.info("Migrated {} loan products", legacyProducts.size());
    }

    private void migrateLoanAccounts() {
        List<LegacyLoanAccount> legacyAccounts = legacyLoanAccountRepository.findAll();
        for (LegacyLoanAccount la : legacyAccounts) {
            LoanAccount a = new LoanAccount();
            a.setAccountNumber(la.getLoanAccountNumber());

            Borrower borrower = borrowerRepository.findByExternalId(la.getBorrowerId())
                    .orElseThrow(() -> new RuntimeException("Borrower not found: " + la.getBorrowerId()));
            a.setBorrower(borrower);

            LoanProduct product = loanProductRepository.findByCode(la.getProductCode())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + la.getProductCode()));
            a.setProduct(product);

            a.setOriginalAmount(parseLegacyAmount(la.getOriginalAmount()));
            a.setCurrentBalance(parseLegacyAmount(la.getCurrentBalance()));
            a.setInterestRate(parseLegacyDecimal(la.getInterestRate()));
            a.setTermMonths(parseLegacyInteger(la.getTermMonths()));
            a.setMonthlyPayment(parseLegacyAmount(la.getMonthlyPayment()));
            a.setOriginationDate(parseLegacyDate(la.getOriginationDate()));
            a.setMaturityDate(parseLegacyDate(la.getMaturityDate()));
            a.setFirstPaymentDate(parseLegacyDate(la.getFirstPaymentDate()));
            a.setNextPaymentDate(parseLegacyDate(la.getNextPaymentDate()));
            a.setStatus(expandLoanStatus(la.getStatusCode()));
            a.setDelinquencyDays(parseLegacyInteger(la.getDelinquencyDays()));
            a.setEscrowBalance(parseLegacyAmount(la.getEscrowBalance()));
            a.setLtvPercent(parseLegacyDecimal(la.getLtvPercent()));
            a.setPropertyAddress(la.getPropertyAddress());
            a.setPropertyCity(la.getPropertyCity());
            a.setPropertyState(la.getPropertyState());
            a.setPropertyZip(la.getPropertyZip());
            a.setPropertyType(expandPropertyType(la.getPropertyType()));
            a.setAppraisedValue(parseLegacyAmount(la.getAppraisedValue()));
            a.setCreatedAt(parseLegacyTimestamp(la.getCreatedDate()));
            a.setUpdatedAt(parseLegacyTimestamp(la.getUpdatedDate()));
            loanAccountRepository.save(a);
        }
        log.info("Migrated {} loan accounts", legacyAccounts.size());
    }

    private void migratePayments() {
        List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();
        for (LegacyPayment lp : legacyPayments) {
            Payment p = new Payment();

            LoanAccount loanAccount = loanAccountRepository.findByAccountNumber(lp.getLoanAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Loan account not found: " + lp.getLoanAccountNumber()));
            p.setLoanAccount(loanAccount);

            p.setPaymentDate(parseLegacyDate(lp.getPaymentDate()));
            p.setTotalAmount(parseLegacyAmount(lp.getTotalAmount()));
            p.setPrincipalAmount(parseLegacyAmount(lp.getPrincipalAmount()));
            p.setInterestAmount(parseLegacyAmount(lp.getInterestAmount()));
            p.setEscrowAmount(parseLegacyAmount(lp.getEscrowAmount()));
            p.setLateFee(parseLegacyAmount(lp.getLateFee()));
            p.setType(expandPaymentType(lp.getTypeCode()));
            p.setStatus(expandPaymentStatus(lp.getStatusCode()));
            p.setReceivedDate(parseLegacyDate(lp.getReceivedDate()));
            p.setProcessedDate(parseLegacyDate(lp.getProcessedDate()));
            p.setCreatedAt(parseLegacyTimestamp(lp.getCreatedDate()));
            p.setUpdatedAt(parseLegacyTimestamp(lp.getUpdatedDate()));
            p.setLegacySequenceNumber(lp.getPaymentSequenceNumber());
            paymentRepository.save(p);
        }
        log.info("Migrated {} payments", legacyPayments.size());
    }

    // ---- parsing helpers (match legacy LoanService logic) ----

    private BigDecimal parseLegacyAmount(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(amount.replace(",", ""));
    }

    private BigDecimal parseLegacyDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim());
    }

    private Integer parseLegacyInteger(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value.trim());
    }

    private LocalDate parseLegacyDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr.trim(), LEGACY_DATE_FMT);
    }

    private Timestamp parseLegacyTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        LocalDate date = LocalDate.parse(dateStr.trim(), LEGACY_DATE_FMT);
        return Timestamp.valueOf(date.atStartOfDay());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String expandBorrowerStatus(String code) {
        if (code == null) return "ACTIVE";
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> code;
        };
    }

    private String expandLoanStatus(String code) {
        if (code == null) return "Active";
        return switch (code) {
            case "ACT" -> "Active";
            case "CLO" -> "Closed";
            case "DFT" -> "Default";
            case "FRB" -> "Forbearance";
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
        if (code == null) return code;
        return switch (code) {
            case "REG" -> "Regular";
            case "EXT" -> "Extra";
            case "PRT" -> "Partial";
            case "PRE" -> "Prepayment";
            default -> code;
        };
    }

    private String expandPaymentStatus(String code) {
        if (code == null) return code;
        return switch (code) {
            case "PST" -> "Posted";
            case "REV" -> "Reversed";
            case "NSF" -> "Non-Sufficient Funds";
            case "PND" -> "Pending";
            default -> code;
        };
    }
}
