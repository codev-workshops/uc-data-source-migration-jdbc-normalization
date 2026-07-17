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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy → modern ETL. Reads every legacy CDW row, converts the all-VARCHAR
 * legacy fields to proper types (dates, decimals, integers, booleans), expands
 * status/type/property codes, resolves the denormalized borrower/product/account
 * references into real foreign keys, and writes the modern normalized tables.
 *
 * <p>Runs once at startup so the modern data source is populated for dual-read
 * (disable with {@code loanservice.migrate-on-startup=false} to run it on
 * demand instead). It is idempotent: if the modern tables already hold data it
 * is skipped, so restarts don't duplicate rows.
 *
 * <p>The legacy {@code PMT_SEQ_NBR} is preserved in
 * {@code payments.legacy_sequence_number} (column_mappings.md, CDW_PMT_HIST row:
 * "Auto-generated; legacy ID stored if needed") so the API contract's
 * {@code paymentId} is unchanged.
 */
@Service
public class DataMigrationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);
    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyAccounts;
    private final LegacyPaymentRepository legacyPayments;

    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository accounts;
    private final PaymentRepository payments;

    private final TransactionTemplate modernTx;
    private final boolean migrateOnStartup;

    public DataMigrationService(LegacyBorrowerRepository legacyBorrowers,
                                LegacyLoanProductRepository legacyProducts,
                                LegacyLoanAccountRepository legacyAccounts,
                                LegacyPaymentRepository legacyPayments,
                                BorrowerRepository borrowers,
                                LoanProductRepository products,
                                LoanAccountRepository accounts,
                                PaymentRepository payments,
                                @Qualifier("modernTransactionManager") PlatformTransactionManager modernTxManager,
                                @Value("${loanservice.migrate-on-startup:true}") boolean migrateOnStartup) {
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyAccounts = legacyAccounts;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.accounts = accounts;
        this.payments = payments;
        this.modernTx = new TransactionTemplate(modernTxManager);
        this.migrateOnStartup = migrateOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (migrateOnStartup) {
            migrate();
        }
    }

    /** Runs the ETL inside a single modern-data-source transaction. */
    public void migrate() {
        if (borrowers.count() > 0 || accounts.count() > 0 || payments.count() > 0) {
            log.info("Modern tables already populated; skipping migration (idempotent).");
            return;
        }
        modernTx.executeWithoutResult(status -> doMigrate());
        validate();
    }

    private void doMigrate() {
        Map<String, Borrower> borrowerByExternalId = new HashMap<>();
        for (LegacyBorrower lb : legacyBorrowers.findAll()) {
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
            b.setStatus(expandBorrowerStatus(lb.getStatusCode()));
            b.setCreatedAt(parseTimestamp(lb.getCreatedDate()));
            b.setUpdatedAt(parseTimestamp(lb.getUpdatedDate()));
            if (borrowerByExternalId.putIfAbsent(b.getExternalId(), b) != null) {
                throw new IllegalStateException("Duplicate legacy borrower id: " + b.getExternalId());
            }
            borrowers.save(b);
        }

        Map<String, LoanProduct> productByCode = new HashMap<>();
        for (LegacyLoanProduct lp : legacyProducts.findAll()) {
            LoanProduct p = new LoanProduct();
            p.setCode(lp.getProductCode());
            p.setName(lp.getDescription());
            p.setType(lp.getTypeCode());
            p.setTermMonths(parseInteger(lp.getTermMonths()));
            p.setRateType(lp.getRateType());
            p.setMinAmount(parseAmount(lp.getMinAmount()));
            p.setMaxAmount(parseAmount(lp.getMaxAmount()));
            p.setActive(parseActive(lp.getStatusCode()));
            p.setEffectiveDate(parseDate(lp.getEffectiveDate()));
            p.setExpirationDate(parseDate(lp.getExpirationDate()));
            if (productByCode.putIfAbsent(p.getCode(), p) != null) {
                throw new IllegalStateException("Duplicate legacy product code: " + p.getCode());
            }
            products.save(p);
        }

        Map<String, LoanAccount> accountByNumber = new HashMap<>();
        for (LegacyLoanAccount la : legacyAccounts.findAll()) {
            Borrower borrower = borrowerByExternalId.get(la.getBorrowerId());
            if (borrower == null) {
                throw new IllegalStateException("Loan " + la.getLoanAccountNumber()
                        + " references unknown borrower " + la.getBorrowerId());
            }
            LoanProduct product = productByCode.get(la.getProductCode());
            if (product == null) {
                throw new IllegalStateException("Loan " + la.getLoanAccountNumber()
                        + " references unknown product " + la.getProductCode());
            }
            LoanAccount a = new LoanAccount();
            a.setAccountNumber(la.getLoanAccountNumber());
            a.setBorrower(borrower);
            a.setProduct(product);
            a.setOriginalAmount(parseAmount(la.getOriginalAmount()));
            a.setCurrentBalance(parseAmount(la.getCurrentBalance()));
            a.setInterestRate(parseDecimal(la.getInterestRate()));
            a.setTermMonths(parseInteger(la.getTermMonths()));
            a.setMonthlyPayment(parseAmount(la.getMonthlyPayment()));
            a.setOriginationDate(parseDate(la.getOriginationDate()));
            a.setMaturityDate(parseDate(la.getMaturityDate()));
            a.setFirstPaymentDate(parseDate(la.getFirstPaymentDate()));
            a.setNextPaymentDate(parseDate(la.getNextPaymentDate()));
            a.setStatus(expandLoanStatus(la.getStatusCode()));
            a.setDelinquencyDays(parseInteger(la.getDelinquencyDays()));
            a.setEscrowBalance(parseAmount(la.getEscrowBalance()));
            a.setLtvPercent(parseDecimal(la.getLtvPercent()));
            a.setPropertyAddress(la.getPropertyAddress());
            a.setPropertyCity(la.getPropertyCity());
            a.setPropertyState(la.getPropertyState());
            a.setPropertyZip(la.getPropertyZip());
            a.setPropertyType(expandPropertyType(la.getPropertyType()));
            a.setAppraisedValue(parseAmount(la.getAppraisedValue()));
            a.setCreatedAt(parseTimestamp(la.getCreatedDate()));
            a.setUpdatedAt(parseTimestamp(la.getUpdatedDate()));
            if (accountByNumber.putIfAbsent(a.getAccountNumber(), a) != null) {
                throw new IllegalStateException("Duplicate legacy loan account: " + a.getAccountNumber());
            }
            accounts.save(a);
        }

        for (LegacyPayment lp : legacyPayments.findAll()) {
            LoanAccount account = accountByNumber.get(lp.getLoanAccountNumber());
            if (account == null) {
                throw new IllegalStateException("Payment " + lp.getPaymentSequenceNumber()
                        + " references unknown loan account " + lp.getLoanAccountNumber());
            }
            Payment p = new Payment();
            p.setLegacySequenceNumber(lp.getPaymentSequenceNumber());
            p.setLoanAccount(account);
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
            p.setCreatedAt(parseTimestamp(lp.getCreatedDate()));
            p.setUpdatedAt(parseTimestamp(lp.getUpdatedDate()));
            payments.save(p);
        }
    }

    /** Row-count parity check between legacy and modern after the ETL. */
    private void validate() {
        assertCount("borrowers", legacyBorrowers.count(), borrowers.count());
        assertCount("loan_products", legacyProducts.count(), products.count());
        assertCount("loan_accounts", legacyAccounts.count(), accounts.count());
        assertCount("payments", legacyPayments.count(), payments.count());
        log.info("Migration complete: {} borrowers, {} products, {} accounts, {} payments migrated.",
                borrowers.count(), products.count(), accounts.count(), payments.count());
    }

    private void assertCount(String table, long legacy, long modern) {
        if (legacy != modern) {
            throw new IllegalStateException(
                    "Migration row-count mismatch for " + table + ": legacy=" + legacy + " modern=" + modern);
        }
    }

    // =========================================================================
    // TYPE CONVERSIONS (legacy VARCHAR -> proper types) + CODE EXPANSION
    // =========================================================================

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value.trim(), LEGACY_DATE);
    }

    private static LocalDateTime parseTimestamp(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.atStartOfDay();
    }

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

    private static Boolean parseActive(String code) {
        return "ACT".equals(code);
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

    /**
     * Property codes expand to the full human-readable label (column_mappings.md:
     * "SFR→Single Family, CND→Condominium, etc."). The full label — not an
     * abbreviation — is stored so the read path is a pass-through that reproduces
     * the existing API contract ("Single Family Residence", ...).
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
}
