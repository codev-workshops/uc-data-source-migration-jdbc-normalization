package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.*;
import com.workshop.loanservice.repository.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Migration service that reads from legacy CDW tables and writes
 * to the modern normalized schema with proper types and FK relationships.
 *
 * Runs automatically on startup via @PostConstruct.
 * Idempotent: skips migration if modern tables already contain data.
 */
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

    public DataMigrationService(
            LegacyBorrowerRepository legacyBorrowerRepository,
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

    @PostConstruct
    @Transactional
    public void migrate() {
        if (borrowerRepository.count() > 0) {
            log.info("Migration skipped — modern tables already contain data.");
            return;
        }

        log.info("=== Starting Legacy → Modern Data Migration ===");

        int borrowerCount = migrateBorrowers();
        int productCount = migrateLoanProducts();
        int accountCount = migrateLoanAccounts();
        int paymentCount = migratePayments();

        log.info("=== Migration Complete ===");
        log.info("  Borrowers migrated:     {}", borrowerCount);
        log.info("  Loan products migrated: {}", productCount);
        log.info("  Loan accounts migrated: {}", accountCount);
        log.info("  Payments migrated:      {}", paymentCount);

        validateMigration(borrowerCount, productCount, accountCount, paymentCount);
    }

    // =========================================================================
    // MIGRATION METHODS (one per table, in FK-dependency order)
    // =========================================================================

    private int migrateBorrowers() {
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
        int count = 0;

        for (LegacyBorrower legacy : legacyBorrowers) {
            try {
                Borrower modern = new Borrower();
                modern.setExternalId(legacy.getBorrowerId());
                modern.setFirstName(legacy.getFirstName());
                modern.setLastName(legacy.getLastName());
                modern.setMiddleInitial(legacy.getMiddleInitial());
                modern.setSsnHash(legacy.getSsnEncrypted());
                modern.setDateOfBirth(parseLegacyDate(legacy.getDateOfBirth()));
                modern.setAddressLine1(legacy.getAddressLine1());
                modern.setAddressLine2(legacy.getAddressLine2());
                modern.setCity(legacy.getCity());
                modern.setState(legacy.getStateCode());
                modern.setZipCode(legacy.getZipCode());
                modern.setPhone(legacy.getPhoneNumber());
                modern.setEmail(legacy.getEmail());
                modern.setCreditScore(parseLegacyInteger(legacy.getCreditScore()));
                modern.setEmploymentStatus(legacy.getEmploymentStatus());
                modern.setAnnualIncome(parseLegacyAmount(legacy.getAnnualIncome()));
                modern.setStatus(expandBorrowerStatus(legacy.getStatusCode()));
                modern.setCreatedAt(parseLegacyTimestamp(legacy.getCreatedDate()));
                modern.setUpdatedAt(parseLegacyTimestamp(legacy.getUpdatedDate()));

                borrowerRepository.save(modern);
                count++;
                log.info("  Migrated borrower: {} ({} {})",
                        legacy.getBorrowerId(), legacy.getFirstName(), legacy.getLastName());
            } catch (Exception e) {
                log.error("  Failed to migrate borrower {}: {}", legacy.getBorrowerId(), e.getMessage());
            }
        }
        return count;
    }

    private int migrateLoanProducts() {
        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
        int count = 0;

        for (LegacyLoanProduct legacy : legacyProducts) {
            try {
                LoanProduct modern = new LoanProduct();
                modern.setCode(legacy.getProductCode());
                modern.setName(legacy.getDescription());
                modern.setType(legacy.getTypeCode());
                modern.setTermMonths(parseLegacyInteger(legacy.getTermMonths()));
                modern.setRateType(legacy.getRateType());
                modern.setMinAmount(parseLegacyAmount(legacy.getMinAmount()));
                modern.setMaxAmount(parseLegacyAmount(legacy.getMaxAmount()));
                modern.setIsActive("ACT".equals(legacy.getStatusCode()));
                modern.setEffectiveDate(parseLegacyDate(legacy.getEffectiveDate()));
                modern.setExpirationDate(parseLegacyDate(legacy.getExpirationDate()));

                loanProductRepository.save(modern);
                count++;
                log.info("  Migrated loan product: {} ({})", legacy.getProductCode(), legacy.getDescription());
            } catch (Exception e) {
                log.error("  Failed to migrate loan product {}: {}", legacy.getProductCode(), e.getMessage());
            }
        }
        return count;
    }

    private int migrateLoanAccounts() {
        List<LegacyLoanAccount> legacyAccounts = legacyLoanAccountRepository.findAll();

        // Build lookup maps for FK resolution
        Map<String, Borrower> borrowersByExternalId = borrowerRepository.findAll().stream()
                .collect(Collectors.toMap(Borrower::getExternalId, b -> b));
        Map<String, LoanProduct> productsByCode = loanProductRepository.findAll().stream()
                .collect(Collectors.toMap(LoanProduct::getCode, p -> p));

        int count = 0;

        for (LegacyLoanAccount legacy : legacyAccounts) {
            try {
                // Resolve FKs
                Borrower borrower = borrowersByExternalId.get(legacy.getBorrowerId());
                if (borrower == null) {
                    log.error("  Skipping loan {} — borrower {} not found in modern table",
                            legacy.getLoanAccountNumber(), legacy.getBorrowerId());
                    continue;
                }

                LoanProduct product = productsByCode.get(legacy.getProductCode());
                if (product == null) {
                    log.error("  Skipping loan {} — product {} not found in modern table",
                            legacy.getLoanAccountNumber(), legacy.getProductCode());
                    continue;
                }

                LoanAccount modern = new LoanAccount();
                modern.setAccountNumber(legacy.getLoanAccountNumber());
                modern.setBorrower(borrower);
                modern.setProduct(product);
                modern.setOriginalAmount(parseLegacyAmount(legacy.getOriginalAmount()));
                modern.setCurrentBalance(parseLegacyAmount(legacy.getCurrentBalance()));
                modern.setInterestRate(parseLegacyDecimal(legacy.getInterestRate()));
                modern.setTermMonths(parseLegacyInteger(legacy.getTermMonths()));
                modern.setMonthlyPayment(parseLegacyAmount(legacy.getMonthlyPayment()));
                modern.setOriginationDate(parseLegacyDate(legacy.getOriginationDate()));
                modern.setMaturityDate(parseLegacyDate(legacy.getMaturityDate()));
                modern.setFirstPaymentDate(parseLegacyDate(legacy.getFirstPaymentDate()));
                modern.setNextPaymentDate(parseLegacyDate(legacy.getNextPaymentDate()));
                modern.setStatus(expandLoanStatus(legacy.getStatusCode()));
                modern.setDelinquencyDays(parseLegacyInteger(legacy.getDelinquencyDays()));
                modern.setEscrowBalance(parseLegacyAmount(legacy.getEscrowBalance()));
                modern.setLtvPercent(parseLegacyDecimal(legacy.getLtvPercent()));
                modern.setPropertyAddress(legacy.getPropertyAddress());
                modern.setPropertyCity(legacy.getPropertyCity());
                modern.setPropertyState(legacy.getPropertyState());
                modern.setPropertyZip(legacy.getPropertyZip());
                modern.setPropertyType(expandPropertyType(legacy.getPropertyType()));
                modern.setAppraisedValue(parseLegacyAmount(legacy.getAppraisedValue()));
                modern.setCreatedAt(parseLegacyTimestamp(legacy.getCreatedDate()));
                modern.setUpdatedAt(parseLegacyTimestamp(legacy.getUpdatedDate()));

                loanAccountRepository.save(modern);
                count++;
                log.info("  Migrated loan account: {} (borrower: {}, product: {})",
                        legacy.getLoanAccountNumber(), legacy.getBorrowerId(), legacy.getProductCode());
            } catch (Exception e) {
                log.error("  Failed to migrate loan account {}: {}",
                        legacy.getLoanAccountNumber(), e.getMessage());
            }
        }
        return count;
    }

    private int migratePayments() {
        List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();

        // Build lookup map for FK resolution
        Map<String, LoanAccount> accountsByNumber = loanAccountRepository.findAll().stream()
                .collect(Collectors.toMap(LoanAccount::getAccountNumber, a -> a));

        int count = 0;

        for (LegacyPayment legacy : legacyPayments) {
            try {
                LoanAccount loanAccount = accountsByNumber.get(legacy.getLoanAccountNumber());
                if (loanAccount == null) {
                    log.error("  Skipping payment {} — loan account {} not found in modern table",
                            legacy.getPaymentSequenceNumber(), legacy.getLoanAccountNumber());
                    continue;
                }

                Payment modern = new Payment();
                modern.setExternalId(legacy.getPaymentSequenceNumber());
                modern.setLoanAccount(loanAccount);
                modern.setPaymentDate(parseLegacyDate(legacy.getPaymentDate()));
                modern.setTotalAmount(parseLegacyAmount(legacy.getTotalAmount()));
                modern.setPrincipalAmount(parseLegacyAmount(legacy.getPrincipalAmount()));
                modern.setInterestAmount(parseLegacyAmount(legacy.getInterestAmount()));
                modern.setEscrowAmount(parseLegacyAmount(legacy.getEscrowAmount()));
                modern.setLateFee(parseLegacyAmount(legacy.getLateFee()));
                modern.setType(expandPaymentType(legacy.getTypeCode()));
                modern.setStatus(expandPaymentStatus(legacy.getStatusCode()));
                modern.setReceivedDate(parseLegacyDate(legacy.getReceivedDate()));
                modern.setProcessedDate(parseLegacyDate(legacy.getProcessedDate()));
                modern.setCreatedAt(parseLegacyTimestamp(legacy.getCreatedDate()));
                modern.setUpdatedAt(parseLegacyTimestamp(legacy.getUpdatedDate()));

                paymentRepository.save(modern);
                count++;
                log.info("  Migrated payment: {} → loan {}",
                        legacy.getPaymentSequenceNumber(), legacy.getLoanAccountNumber());
            } catch (Exception e) {
                log.error("  Failed to migrate payment {}: {}",
                        legacy.getPaymentSequenceNumber(), e.getMessage());
            }
        }
        return count;
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    private void validateMigration(int borrowerCount, int productCount, int accountCount, int paymentCount) {
        boolean valid = true;

        if (borrowerCount != 5) {
            log.warn("  VALIDATION FAILED: Expected 5 borrowers, migrated {}", borrowerCount);
            valid = false;
        }
        if (productCount != 5) {
            log.warn("  VALIDATION FAILED: Expected 5 loan products, migrated {}", productCount);
            valid = false;
        }
        if (accountCount != 5) {
            log.warn("  VALIDATION FAILED: Expected 5 loan accounts, migrated {}", accountCount);
            valid = false;
        }
        if (paymentCount != 10) {
            log.warn("  VALIDATION FAILED: Expected 10 payments, migrated {}", paymentCount);
            valid = false;
        }

        if (valid) {
            log.info("  VALIDATION PASSED: All row counts match expected values.");
        }
    }

    // =========================================================================
    // PARSING UTILITIES
    // =========================================================================

    private LocalDate parseLegacyDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT);
    }

    private LocalDateTime parseLegacyTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT).atStartOfDay();
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

    // =========================================================================
    // STATUS / CODE EXPANSION
    // =========================================================================

    private String expandBorrowerStatus(String code) {
        if (code == null) return "ACTIVE";
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> code;
        };
    }

    private String expandLoanStatus(String code) {
        if (code == null) return "ACTIVE";
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
            case "SFR" -> "Single Family";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }

    private String expandPaymentType(String code) {
        if (code == null) return "REGULAR";
        return switch (code) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> code;
        };
    }

    private String expandPaymentStatus(String code) {
        if (code == null) return "POSTED";
        return switch (code) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> code;
        };
    }
}
