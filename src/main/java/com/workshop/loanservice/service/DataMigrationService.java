package com.workshop.loanservice.service;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);
    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final LegacyBorrowerRepository legacyBorrowerRepository;
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    private final LegacyPaymentRepository legacyPaymentRepository;

    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    private final Map<String, Long> borrowerIdMap = new HashMap<>();
    private final Map<String, Long> productIdMap = new HashMap<>();
    private final Map<String, Long> loanAccountIdMap = new HashMap<>();

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

    @Transactional
    public void migrateAll() {
        log.info("=== Starting Data Migration ===");

        int borrowerCount = migrateBorrowers();
        int productCount = migrateLoanProducts();
        int loanAccountCount = migrateLoanAccounts();
        int paymentCount = migratePayments();

        log.info("=== Migration Summary ===");
        log.info("Borrowers migrated: {}", borrowerCount);
        log.info("Loan products migrated: {}", productCount);
        log.info("Loan accounts migrated: {}", loanAccountCount);
        log.info("Payments migrated: {}", paymentCount);
        log.info("=== Data Migration Complete ===");
    }

    private int migrateBorrowers() {
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
        int expected = legacyBorrowers.size();
        int migrated = 0;
        int skipped = 0;

        log.info("Migrating {} borrowers...", expected);

        for (LegacyBorrower legacy : legacyBorrowers) {
            try {
                Borrower borrower = new Borrower();
                borrower.setExternalId(legacy.getBorrowerId());
                borrower.setFirstName(legacy.getFirstName());
                borrower.setLastName(legacy.getLastName());
                borrower.setMiddleInitial(legacy.getMiddleInitial());
                borrower.setSsnHash(legacy.getSsnEncrypted());
                borrower.setDateOfBirth(parseLegacyDate(legacy.getDateOfBirth()));
                borrower.setAddressLine1(legacy.getAddressLine1());
                borrower.setAddressLine2(legacy.getAddressLine2());
                borrower.setCity(legacy.getCity());
                borrower.setState(legacy.getStateCode());
                borrower.setZipCode(legacy.getZipCode());
                borrower.setPhone(legacy.getPhoneNumber());
                borrower.setEmail(legacy.getEmail());
                borrower.setCreditScore(parseLegacyInteger(legacy.getCreditScore()));
                borrower.setEmploymentStatus(legacy.getEmploymentStatus());
                borrower.setAnnualIncome(parseLegacyAmount(legacy.getAnnualIncome()));
                borrower.setStatus(expandBorrowerStatus(legacy.getStatusCode()));
                borrower.setCreatedAt(parseLegacyTimestamp(legacy.getCreatedDate()));
                borrower.setUpdatedAt(parseLegacyTimestamp(legacy.getUpdatedDate()));

                Borrower saved = borrowerRepository.save(borrower);
                borrowerIdMap.put(legacy.getBorrowerId(), saved.getId());
                migrated++;
            } catch (Exception e) {
                log.error("Failed to migrate borrower [{}]: {}", legacy.getBorrowerId(), e.getMessage());
                skipped++;
            }
        }

        log.info("Borrowers: expected={}, migrated={}, skipped={}", expected, migrated, skipped);
        return migrated;
    }

    private int migrateLoanProducts() {
        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
        int expected = legacyProducts.size();
        int migrated = 0;
        int skipped = 0;

        log.info("Migrating {} loan products...", expected);

        for (LegacyLoanProduct legacy : legacyProducts) {
            try {
                LoanProduct product = new LoanProduct();
                product.setCode(legacy.getProductCode());
                product.setName(legacy.getDescription());
                product.setType(legacy.getTypeCode());
                product.setTermMonths(parseLegacyInteger(legacy.getTermMonths()));
                product.setRateType(legacy.getRateType());
                product.setMinAmount(parseLegacyAmount(legacy.getMinAmount()));
                product.setMaxAmount(parseLegacyAmount(legacy.getMaxAmount()));
                product.setIsActive(expandProductStatus(legacy.getStatusCode()));
                product.setEffectiveDate(parseLegacyDate(legacy.getEffectiveDate()));
                product.setExpirationDate(parseLegacyDate(legacy.getExpirationDate()));

                LoanProduct saved = loanProductRepository.save(product);
                productIdMap.put(legacy.getProductCode(), saved.getId());
                migrated++;
            } catch (Exception e) {
                log.error("Failed to migrate loan product [{}]: {}", legacy.getProductCode(), e.getMessage());
                skipped++;
            }
        }

        log.info("Loan products: expected={}, migrated={}, skipped={}", expected, migrated, skipped);
        return migrated;
    }

    private int migrateLoanAccounts() {
        List<LegacyLoanAccount> legacyAccounts = legacyLoanAccountRepository.findAll();
        int expected = legacyAccounts.size();
        int migrated = 0;
        int skipped = 0;

        log.info("Migrating {} loan accounts...", expected);

        for (LegacyLoanAccount legacy : legacyAccounts) {
            try {
                LoanAccount account = new LoanAccount();
                account.setAccountNumber(legacy.getLoanAccountNumber());

                // Resolve borrower FK
                Long borrowerId = borrowerIdMap.get(legacy.getBorrowerId());
                if (borrowerId == null) {
                    log.warn("Borrower not found for loan account [{}], borrower ID [{}]. Skipping.",
                            legacy.getLoanAccountNumber(), legacy.getBorrowerId());
                    skipped++;
                    continue;
                }
                Borrower borrower = borrowerRepository.findById(borrowerId)
                        .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
                account.setBorrower(borrower);

                // Resolve product FK
                Long productId = productIdMap.get(legacy.getProductCode());
                if (productId == null) {
                    log.warn("Product not found for loan account [{}], product code [{}]. Skipping.",
                            legacy.getLoanAccountNumber(), legacy.getProductCode());
                    skipped++;
                    continue;
                }
                LoanProduct product = loanProductRepository.findById(productId)
                        .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
                account.setProduct(product);

                account.setOriginalAmount(parseLegacyAmount(legacy.getOriginalAmount()));
                account.setCurrentBalance(parseLegacyAmount(legacy.getCurrentBalance()));
                account.setInterestRate(parseLegacyDecimal(legacy.getInterestRate()));
                account.setTermMonths(parseLegacyInteger(legacy.getTermMonths()));
                account.setMonthlyPayment(parseLegacyAmount(legacy.getMonthlyPayment()));
                account.setOriginationDate(parseLegacyDate(legacy.getOriginationDate()));
                account.setMaturityDate(parseLegacyDate(legacy.getMaturityDate()));
                account.setFirstPaymentDate(parseLegacyDate(legacy.getFirstPaymentDate()));
                account.setNextPaymentDate(parseLegacyDate(legacy.getNextPaymentDate()));
                account.setStatus(expandLoanStatus(legacy.getStatusCode()));
                account.setDelinquencyDays(parseLegacyInteger(legacy.getDelinquencyDays()));
                account.setEscrowBalance(parseLegacyAmount(legacy.getEscrowBalance()));
                account.setLtvPercent(parseLegacyDecimal(legacy.getLtvPercent()));
                account.setPropertyAddress(legacy.getPropertyAddress());
                account.setPropertyCity(legacy.getPropertyCity());
                account.setPropertyState(legacy.getPropertyState());
                account.setPropertyZip(legacy.getPropertyZip());
                account.setPropertyType(expandPropertyType(legacy.getPropertyType()));
                account.setAppraisedValue(parseLegacyAmount(legacy.getAppraisedValue()));
                account.setCreatedAt(parseLegacyTimestamp(legacy.getCreatedDate()));
                account.setUpdatedAt(parseLegacyTimestamp(legacy.getUpdatedDate()));

                LoanAccount saved = loanAccountRepository.save(account);
                loanAccountIdMap.put(legacy.getLoanAccountNumber(), saved.getId());
                migrated++;
            } catch (Exception e) {
                log.error("Failed to migrate loan account [{}]: {}", legacy.getLoanAccountNumber(), e.getMessage());
                skipped++;
            }
        }

        log.info("Loan accounts: expected={}, migrated={}, skipped={}", expected, migrated, skipped);
        return migrated;
    }

    private int migratePayments() {
        List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();
        int expected = legacyPayments.size();
        int migrated = 0;
        int skipped = 0;

        log.info("Migrating {} payments...", expected);

        for (LegacyPayment legacy : legacyPayments) {
            try {
                Payment payment = new Payment();

                // Resolve loan account FK
                Long loanAccountId = loanAccountIdMap.get(legacy.getLoanAccountNumber());
                if (loanAccountId == null) {
                    log.warn("Loan account not found for payment [{}], account [{}]. Skipping.",
                            legacy.getPaymentSequenceNumber(), legacy.getLoanAccountNumber());
                    skipped++;
                    continue;
                }
                LoanAccount loanAccount = loanAccountRepository.findById(loanAccountId)
                        .orElseThrow(() -> new RuntimeException("Loan account not found: " + loanAccountId));
                payment.setLoanAccount(loanAccount);

                payment.setPaymentDate(parseLegacyDate(legacy.getPaymentDate()));
                payment.setTotalAmount(parseLegacyAmount(legacy.getTotalAmount()));
                payment.setPrincipalAmount(parseLegacyAmount(legacy.getPrincipalAmount()));
                payment.setInterestAmount(parseLegacyAmount(legacy.getInterestAmount()));
                payment.setEscrowAmount(parseLegacyAmount(legacy.getEscrowAmount()));
                payment.setLateFee(parseLegacyAmount(legacy.getLateFee()));
                payment.setType(expandPaymentType(legacy.getTypeCode()));
                payment.setStatus(expandPaymentStatus(legacy.getStatusCode()));
                payment.setReceivedDate(parseLegacyDate(legacy.getReceivedDate()));
                payment.setProcessedDate(parseLegacyDate(legacy.getProcessedDate()));
                payment.setCreatedAt(parseLegacyTimestamp(legacy.getCreatedDate()));
                payment.setUpdatedAt(parseLegacyTimestamp(legacy.getUpdatedDate()));

                paymentRepository.save(payment);
                migrated++;
            } catch (Exception e) {
                log.error("Failed to migrate payment [{}]: {}", legacy.getPaymentSequenceNumber(), e.getMessage());
                skipped++;
            }
        }

        log.info("Payments: expected={}, migrated={}, skipped={}", expected, migrated, skipped);
        return migrated;
    }

    // =========================================================================
    // Transformation helper methods
    // =========================================================================

    private LocalDate parseLegacyDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT);
    }

    private BigDecimal parseLegacyAmount(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(amount.replace(",", "").trim());
    }

    private BigDecimal parseLegacyDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim());
    }

    private Integer parseLegacyInteger(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value.trim());
    }

    private LocalDateTime parseLegacyTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        LocalDate date = LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT);
        return date.atStartOfDay();
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

    private Boolean expandProductStatus(String code) {
        if (code == null) return true;
        return switch (code) {
            case "ACT" -> true;
            case "INA" -> false;
            default -> true;
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
            case "REG" -> "Regular";
            case "EXT" -> "Extra";
            case "PRT" -> "Partial";
            case "PRE" -> "Prepayment";
            default -> code;
        };
    }

    private String expandPaymentStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "PST" -> "Posted";
            case "REV" -> "Reversed";
            case "NSF" -> "Non-Sufficient Funds";
            case "PND" -> "Pending";
            default -> code;
        };
    }
}
