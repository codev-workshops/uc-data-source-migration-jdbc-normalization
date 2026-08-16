package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.legacy.LegacyBorrower;
import com.workshop.loanservice.entity.legacy.LegacyLoanAccount;
import com.workshop.loanservice.entity.legacy.LegacyLoanProduct;
import com.workshop.loanservice.entity.legacy.LegacyPayment;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.LoanProduct;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.legacy.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.legacy.LegacyPaymentRepository;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Copies the CDW tables into the modern normalized schema: parses the legacy
 * strings into real types, expands the cryptic codes, and replaces the legacy
 * business keys with surrogate foreign keys.
 *
 * <p>The two data sources are independent, so there is no shared transaction to
 * roll back — each table is migrated in its own modern transaction and every row
 * is guarded by an existence check on its business key. A run is therefore
 * idempotent and restartable: rerunning after a partial failure inserts only what
 * is missing. Rows that cannot be migrated (unparseable values, unresolvable
 * foreign keys) are rejected individually and reported rather than aborting the
 * run.
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyLoans;
    private final LegacyPaymentRepository legacyPayments;

    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository loans;
    private final PaymentRepository payments;

    private final LegacyTypeConverter converter;

    /** Writes go to the modern data source only; the legacy reads stay outside it. */
    private final TransactionTemplate modernTx;

    public MigrationService(LegacyBorrowerRepository legacyBorrowers,
                            LegacyLoanProductRepository legacyProducts,
                            LegacyLoanAccountRepository legacyLoans,
                            LegacyPaymentRepository legacyPayments,
                            BorrowerRepository borrowers,
                            LoanProductRepository products,
                            LoanAccountRepository loans,
                            PaymentRepository payments,
                            LegacyTypeConverter converter,
                            @Qualifier("modernTransactionManager") PlatformTransactionManager modernTxManager) {
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyLoans = legacyLoans;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.loans = loans;
        this.payments = payments;
        this.converter = converter;
        this.modernTx = new TransactionTemplate(modernTxManager);
    }

    /** Migrates in dependency order: borrowers, products, loans, then payments. */
    public MigrationReport migrate() {
        MigrationReport report = new MigrationReport();
        modernTx.executeWithoutResult(tx -> migrateBorrowers(report));
        modernTx.executeWithoutResult(tx -> migrateProducts(report));
        modernTx.executeWithoutResult(tx -> migrateLoans(report));
        modernTx.executeWithoutResult(tx -> migratePayments(report));
        log.info("Migration report: {}", report);
        return report;
    }

    void migrateBorrowers(MigrationReport report) {
        MigrationReport.TableResult result = report.table("borrowers");
        for (LegacyBorrower legacy : legacyBorrowers.findAll()) {
            String key = legacy.getBorrowerId();
            if (borrowers.existsByExternalId(key)) {
                result.skipped();
                continue;
            }
            try {
                Borrower borrower = new Borrower();
                borrower.setExternalId(key);
                borrower.setFirstName(legacy.getFirstName());
                borrower.setLastName(legacy.getLastName());
                borrower.setMiddleInitial(legacy.getMiddleInitial());
                borrower.setSsnHash(legacy.getSsnEncrypted());
                borrower.setDateOfBirth(converter.parseDate(legacy.getDateOfBirth()));
                borrower.setAddressLine1(legacy.getAddressLine1());
                borrower.setAddressLine2(legacy.getAddressLine2());
                borrower.setCity(legacy.getCity());
                borrower.setState(legacy.getStateCode());
                borrower.setZipCode(legacy.getZipCode());
                borrower.setPhone(legacy.getPhoneNumber());
                borrower.setEmail(legacy.getEmail());
                borrower.setCreditScore(converter.parseInteger(legacy.getCreditScore()));
                borrower.setEmploymentStatus(legacy.getEmploymentStatus());
                borrower.setAnnualIncome(converter.parseAmount(legacy.getAnnualIncome()));
                borrower.setStatus(converter.canonicalBorrowerStatus(legacy.getStatusCode()));
                borrower.setCreatedAt(timestamp(legacy.getCreatedDate()));
                borrower.setUpdatedAt(timestamp(legacy.getUpdatedDate()));
                borrowers.save(borrower);
                result.migrated();
            } catch (RuntimeException e) {
                result.reject(key, describe(e));
            }
        }
    }

    void migrateProducts(MigrationReport report) {
        MigrationReport.TableResult result = report.table("loan_products");
        for (LegacyLoanProduct legacy : legacyProducts.findAll()) {
            String key = legacy.getProductCode();
            if (products.existsByCode(key)) {
                result.skipped();
                continue;
            }
            try {
                LoanProduct product = new LoanProduct();
                product.setCode(key);
                product.setName(legacy.getDescription());
                product.setType(legacy.getTypeCode());
                product.setTermMonths(converter.parseInteger(legacy.getTermMonths()));
                product.setRateType(legacy.getRateType());
                product.setMinAmount(converter.parseAmount(legacy.getMinAmount()));
                product.setMaxAmount(converter.parseAmount(legacy.getMaxAmount()));
                product.setActive(converter.parseActiveFlag(legacy.getStatusCode()));
                product.setEffectiveDate(converter.parseDate(legacy.getEffectiveDate()));
                product.setExpirationDate(converter.parseDate(legacy.getExpirationDate()));
                products.save(product);
                result.migrated();
            } catch (RuntimeException e) {
                result.reject(key, describe(e));
            }
        }
    }

    void migrateLoans(MigrationReport report) {
        MigrationReport.TableResult result = report.table("loan_accounts");
        for (LegacyLoanAccount legacy : legacyLoans.findAll()) {
            String key = legacy.getLoanAccountNumber();
            if (loans.existsByAccountNumber(key)) {
                result.skipped();
                continue;
            }
            Optional<Borrower> borrower = borrowers.findByExternalId(legacy.getBorrowerId());
            if (borrower.isEmpty()) {
                result.reject(key, "no modern borrower for " + legacy.getBorrowerId());
                continue;
            }
            Optional<LoanProduct> product = products.findByCode(legacy.getProductCode());
            if (product.isEmpty()) {
                result.reject(key, "no modern product for " + legacy.getProductCode());
                continue;
            }
            try {
                LoanAccount loan = new LoanAccount();
                loan.setAccountNumber(key);
                loan.setBorrower(borrower.get());
                loan.setProduct(product.get());
                loan.setOriginalAmount(converter.parseAmount(legacy.getOriginalAmount()));
                loan.setCurrentBalance(converter.parseAmount(legacy.getCurrentBalance()));
                loan.setInterestRate(converter.parseDecimal(legacy.getInterestRate()));
                loan.setTermMonths(converter.parseInteger(legacy.getTermMonths()));
                loan.setMonthlyPayment(converter.parseAmount(legacy.getMonthlyPayment()));
                loan.setOriginationDate(converter.parseDate(legacy.getOriginationDate()));
                loan.setMaturityDate(converter.parseDate(legacy.getMaturityDate()));
                loan.setFirstPaymentDate(converter.parseDate(legacy.getFirstPaymentDate()));
                loan.setNextPaymentDate(converter.parseDate(legacy.getNextPaymentDate()));
                loan.setStatus(converter.canonicalLoanStatus(legacy.getStatusCode()));
                loan.setDelinquencyDays(converter.parseInteger(legacy.getDelinquencyDays()));
                loan.setEscrowBalance(converter.parseAmount(legacy.getEscrowBalance()));
                loan.setLtvPercent(converter.parseDecimal(legacy.getLtvPercent()));
                loan.setPropertyAddress(legacy.getPropertyAddress());
                loan.setPropertyCity(legacy.getPropertyCity());
                loan.setPropertyState(legacy.getPropertyState());
                loan.setPropertyZip(legacy.getPropertyZip());
                loan.setPropertyType(converter.canonicalPropertyType(legacy.getPropertyType()));
                loan.setAppraisedValue(converter.parseAmount(legacy.getAppraisedValue()));
                loan.setCreatedAt(timestamp(legacy.getCreatedDate()));
                loan.setUpdatedAt(timestamp(legacy.getUpdatedDate()));
                loans.save(loan);
                result.migrated();
            } catch (RuntimeException e) {
                result.reject(key, describe(e));
            }
        }
    }

    void migratePayments(MigrationReport report) {
        MigrationReport.TableResult result = report.table("payments");
        for (LegacyPayment legacy : legacyPayments.findAll()) {
            String key = legacy.getPaymentSequenceNumber();
            if (payments.existsByExternalId(key)) {
                result.skipped();
                continue;
            }
            Optional<LoanAccount> loan = loans.findByAccountNumber(legacy.getLoanAccountNumber());
            if (loan.isEmpty()) {
                result.reject(key, "no modern loan for " + legacy.getLoanAccountNumber());
                continue;
            }
            try {
                Payment payment = new Payment();
                payment.setExternalId(key);
                payment.setLoanAccount(loan.get());
                payment.setPaymentDate(converter.parseDate(legacy.getPaymentDate()));
                payment.setTotalAmount(converter.parseAmount(legacy.getTotalAmount()));
                payment.setPrincipalAmount(converter.parseAmount(legacy.getPrincipalAmount()));
                payment.setInterestAmount(converter.parseAmount(legacy.getInterestAmount()));
                payment.setEscrowAmount(converter.parseAmount(legacy.getEscrowAmount()));
                payment.setLateFee(converter.parseAmount(legacy.getLateFee()));
                payment.setType(converter.canonicalPaymentType(legacy.getTypeCode()));
                payment.setStatus(converter.canonicalPaymentStatus(legacy.getStatusCode()));
                payment.setReceivedDate(converter.parseDate(legacy.getReceivedDate()));
                payment.setProcessedDate(converter.parseDate(legacy.getProcessedDate()));
                payment.setCreatedAt(timestamp(legacy.getCreatedDate()));
                payment.setUpdatedAt(timestamp(legacy.getUpdatedDate()));
                payments.save(payment);
                result.migrated();
            } catch (RuntimeException e) {
                result.reject(key, describe(e));
            }
        }
    }

    private LocalDateTime timestamp(String legacyDate) {
        LocalDateTime parsed = converter.parseTimestamp(legacyDate);
        return parsed != null ? parsed : LocalDateTime.now();
    }

    private String describe(RuntimeException e) {
        return e.getClass().getSimpleName() + " " + e.getMessage();
    }
}
