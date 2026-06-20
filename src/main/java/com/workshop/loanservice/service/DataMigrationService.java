package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.*;
import com.workshop.loanservice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates programmatic migration from legacy CDW tables to modern schema.
 * This service is NOT auto-run; the seed SQL (data-modern.sql) handles actual data population.
 * Call migrate() manually for demonstration or if programmatic migration is needed.
 */
@Service
public class DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);
    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    @SuppressWarnings("deprecation")
    private final LegacyBorrowerRepository legacyBorrowerRepository;
    @SuppressWarnings("deprecation")
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    @SuppressWarnings("deprecation")
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    @SuppressWarnings("deprecation")
    private final LegacyPaymentRepository legacyPaymentRepository;

    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    public void migrate() {
        log.info("Starting programmatic migration from legacy to modern schema...");

        Map<String, Long> borrowerIdMap = new HashMap<>();
        Map<String, Long> productIdMap = new HashMap<>();
        Map<String, Long> loanAccountIdMap = new HashMap<>();

        // 1. Migrate borrowers
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
        for (LegacyBorrower lb : legacyBorrowers) {
            Borrower b = new Borrower();
            b.setExternalId(lb.getBorrowerId());
            b.setFirstName(lb.getFirstName());
            b.setLastName(lb.getLastName());
            b.setMiddleInitial(lb.getMiddleInitial());
            b.setSsnHash(lb.getSsnEncrypted());
            b.setDateOfBirth(parseDate(lb.getDateOfBirth()));
            b.setAddressLine1(lb.getAddressLine1());
            b.setAddressLine2(lb.getAddressLine2());
            b.setCity(lb.getCity());
            b.setState(lb.getStateCode());
            b.setZipCode(lb.getZipCode());
            b.setPhone(lb.getPhoneNumber());
            b.setEmail(lb.getEmail());
            b.setCreditScore(parseInteger(lb.getCreditScore()));
            b.setEmploymentStatus(lb.getEmploymentStatus());
            b.setAnnualIncome(parseAmount(lb.getAnnualIncome()));
            b.setStatus(expandStatusCode(lb.getStatusCode()));
            Borrower saved = borrowerRepository.save(b);
            borrowerIdMap.put(lb.getBorrowerId(), saved.getId());
        }
        log.info("Migrated {} borrowers", legacyBorrowers.size());

        // 2. Migrate loan products
        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
        for (LegacyLoanProduct lp : legacyProducts) {
            LoanProduct p = new LoanProduct();
            p.setCode(lp.getProductCode());
            p.setName(lp.getDescription());
            p.setType(lp.getTypeCode());
            p.setTermMonths(parseInteger(lp.getTermMonths()));
            p.setRateType(lp.getRateType());
            p.setMinAmount(parseAmount(lp.getMinAmount()));
            p.setMaxAmount(parseAmount(lp.getMaxAmount()));
            p.setIsActive("ACT".equals(lp.getStatusCode()));
            p.setEffectiveDate(parseDate(lp.getEffectiveDate()));
            p.setExpirationDate(parseDate(lp.getExpirationDate()));
            LoanProduct saved = loanProductRepository.save(p);
            productIdMap.put(lp.getProductCode(), saved.getId());
        }
        log.info("Migrated {} loan products", legacyProducts.size());

        // 3. Migrate loan accounts
        List<LegacyLoanAccount> legacyAccounts = legacyLoanAccountRepository.findAll();
        for (LegacyLoanAccount la : legacyAccounts) {
            LoanAccount a = new LoanAccount();
            a.setAccountNumber(la.getLoanAccountNumber());
            Long borrowerId = borrowerIdMap.get(la.getBorrowerId());
            a.setBorrower(borrowerRepository.findById(borrowerId).orElseThrow());
            Long productId = productIdMap.get(la.getProductCode());
            a.setProduct(loanProductRepository.findById(productId).orElseThrow());
            a.setOriginalAmount(parseAmount(la.getOriginalAmount()));
            a.setCurrentBalance(parseAmount(la.getCurrentBalance()));
            a.setInterestRate(new BigDecimal(la.getInterestRate().trim()));
            a.setTermMonths(parseInteger(la.getTermMonths()));
            a.setMonthlyPayment(parseAmount(la.getMonthlyPayment()));
            a.setOriginationDate(parseDate(la.getOriginationDate()));
            a.setMaturityDate(parseDate(la.getMaturityDate()));
            a.setFirstPaymentDate(parseDate(la.getFirstPaymentDate()));
            a.setNextPaymentDate(parseDate(la.getNextPaymentDate()));
            a.setStatus(expandStatusCode(la.getStatusCode()));
            a.setDelinquencyDays(parseInteger(la.getDelinquencyDays()));
            a.setEscrowBalance(parseAmount(la.getEscrowBalance()));
            a.setLtvPercent(new BigDecimal(la.getLtvPercent().trim()));
            a.setPropertyAddress(la.getPropertyAddress());
            a.setPropertyCity(la.getPropertyCity());
            a.setPropertyState(la.getPropertyState());
            a.setPropertyZip(la.getPropertyZip());
            a.setPropertyType(expandPropertyType(la.getPropertyType()));
            a.setAppraisedValue(parseAmount(la.getAppraisedValue()));
            LoanAccount saved = loanAccountRepository.save(a);
            loanAccountIdMap.put(la.getLoanAccountNumber(), saved.getId());
        }
        log.info("Migrated {} loan accounts", legacyAccounts.size());

        // 4. Migrate payments
        List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();
        for (LegacyPayment lp : legacyPayments) {
            Payment p = new Payment();
            Long loanAccountId = loanAccountIdMap.get(lp.getLoanAccountNumber());
            p.setLoanAccount(loanAccountRepository.findById(loanAccountId).orElseThrow());
            p.setPaymentDate(parseDate(lp.getPaymentDate()));
            p.setTotalAmount(parseAmount(lp.getTotalAmount()));
            p.setPrincipalAmount(parseAmount(lp.getPrincipalAmount()));
            p.setInterestAmount(parseAmount(lp.getInterestAmount()));
            p.setEscrowAmount(parseAmount(lp.getEscrowAmount()));
            p.setLateFee(parseAmount(lp.getLateFee()));
            p.setType(expandPaymentType(lp.getTypeCode()));
            p.setStatus(expandPaymentStatus(lp.getStatusCode()));
            p.setReceivedDate(parseDate(lp.getReceivedDate()));
            p.setProcessedDate(parseDate(lp.getProcessedDate()));
            paymentRepository.save(p);
        }
        log.info("Migrated {} payments", legacyPayments.size());

        log.info("Migration complete.");
    }

    public void validate() {
        long borrowerCount = borrowerRepository.count();
        long productCount = loanProductRepository.count();
        long accountCount = loanAccountRepository.count();
        long paymentCount = paymentRepository.count();

        log.info("=== Migration Validation ===");
        log.info("Borrowers: {}", borrowerCount);
        log.info("Loan Products: {}", productCount);
        log.info("Loan Accounts: {}", accountCount);
        log.info("Payments: {}", paymentCount);

        boolean valid = borrowerCount == 5 && productCount == 5 && accountCount == 5 && paymentCount == 10;
        if (valid) {
            log.info("Validation PASSED: All row counts match expected values.");
        } else {
            log.warn("Validation FAILED: Row counts do not match expected values.");
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT);
    }

    private BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(amount.replace(",", ""));
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value.trim());
    }

    private String expandStatusCode(String code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case "ACT" -> "Active";
            case "CLO" -> "Closed";
            case "DFT" -> "Default";
            case "FRB" -> "Forbearance";
            default -> code;
        };
    }

    private String expandPropertyType(String code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case "SFR" -> "Single Family Residence";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family Residence";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }

    private String expandPaymentType(String code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case "REG" -> "Regular";
            case "EXT" -> "Extra";
            case "PRT" -> "Partial";
            case "PRE" -> "Prepayment";
            default -> code;
        };
    }

    private String expandPaymentStatus(String code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case "PST" -> "Posted";
            case "REV" -> "Reversed";
            case "NSF" -> "Non-Sufficient Funds";
            case "PND" -> "Pending";
            default -> code;
        };
    }
}
