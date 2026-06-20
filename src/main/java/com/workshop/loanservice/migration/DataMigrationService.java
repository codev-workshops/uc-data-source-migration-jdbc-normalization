package com.workshop.loanservice.migration;

import com.workshop.loanservice.legacy.entity.LegacyBorrower;
import com.workshop.loanservice.legacy.entity.LegacyLoanAccount;
import com.workshop.loanservice.legacy.entity.LegacyLoanProduct;
import com.workshop.loanservice.legacy.entity.LegacyPayment;
import com.workshop.loanservice.legacy.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.legacy.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.legacy.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.legacy.repository.LegacyPaymentRepository;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Migrates data from the legacy CDW source into the normalized modern schema.
 *
 * Properties:
 *  - Ordered: borrowers -> products -> loan accounts -> payments, so that
 *    foreign keys can be resolved by stable business keys.
 *  - Idempotent: records already present in the modern schema (matched by
 *    business key) are skipped, so re-running produces no duplicates.
 *  - Transactional: the whole run executes in one modern transaction.
 *  - Resilient + auditable: per-record conversion failures and missing
 *    references are captured in a {@link MigrationReport} with full context
 *    (table, business key, field, invalid value) instead of aborting the run.
 */
@Service
public class DataMigrationService {

    private static final String T_BORROWERS = "borrowers";
    private static final String T_PRODUCTS = "loan_products";
    private static final String T_LOAN_ACCOUNTS = "loan_accounts";
    private static final String T_PAYMENTS = "payments";

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyLoanAccounts;
    private final LegacyPaymentRepository legacyPayments;

    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository loanAccounts;
    private final PaymentRepository payments;

    private final TypeConverter convert;

    public DataMigrationService(LegacyBorrowerRepository legacyBorrowers,
                                LegacyLoanProductRepository legacyProducts,
                                LegacyLoanAccountRepository legacyLoanAccounts,
                                LegacyPaymentRepository legacyPayments,
                                BorrowerRepository borrowers,
                                LoanProductRepository products,
                                LoanAccountRepository loanAccounts,
                                PaymentRepository payments,
                                TypeConverter convert) {
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyLoanAccounts = legacyLoanAccounts;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.loanAccounts = loanAccounts;
        this.payments = payments;
        this.convert = convert;
    }

    @Transactional(transactionManager = "modernTransactionManager")
    public MigrationReport migrate() {
        MigrationReport report = new MigrationReport();
        migrateBorrowers(report);
        migrateProducts(report);
        migrateLoanAccounts(report);
        migratePayments(report);
        return report;
    }

    private void migrateBorrowers(MigrationReport report) {
        for (LegacyBorrower src : legacyBorrowers.findAll()) {
            String key = src.getBorrowerId();
            if (borrowers.findByExternalId(key).isPresent()) {
                report.recordSkipped(T_BORROWERS);
                continue;
            }
            try {
                Borrower b = new Borrower();
                b.setExternalId(key);
                b.setFirstName(src.getFirstName());
                b.setLastName(src.getLastName());
                b.setMiddleInitial(src.getMiddleInitial());
                b.setSsnHash(src.getSsnEncrypted());
                b.setDateOfBirth(convert.parseDate("date_of_birth", src.getDateOfBirth()));
                b.setAddressLine1(src.getAddressLine1());
                b.setAddressLine2(src.getAddressLine2());
                b.setCity(src.getCity());
                b.setState(src.getStateCode());
                b.setZipCode(src.getZipCode());
                b.setPhone(src.getPhoneNumber());
                b.setEmail(src.getEmail());
                b.setCreditScore(convert.parseInteger("credit_score", src.getCreditScore()));
                b.setEmploymentStatus(src.getEmploymentStatus());
                b.setAnnualIncome(convert.parseDecimal("annual_income", src.getAnnualIncome()));
                b.setStatus(convert.borrowerStatus("status", src.getStatusCode()));
                b.setCreatedAt(convert.parseTimestamp("created_at", src.getCreatedDate()));
                b.setUpdatedAt(convert.parseTimestamp("updated_at", src.getUpdatedDate()));
                borrowers.save(b);
                report.recordInserted(T_BORROWERS);
            } catch (ConversionException e) {
                report.recordFailure(T_BORROWERS, key, e.getField(), e.getInvalidValue(), e.getMessage());
            }
        }
    }

    private void migrateProducts(MigrationReport report) {
        for (LegacyLoanProduct src : legacyProducts.findAll()) {
            String key = src.getProductCode();
            if (products.findByCode(key).isPresent()) {
                report.recordSkipped(T_PRODUCTS);
                continue;
            }
            try {
                LoanProduct p = new LoanProduct();
                p.setCode(key);
                p.setName(src.getDescription());
                p.setType(src.getTypeCode());
                p.setTermMonths(convert.parseInteger("term_months", src.getTermMonths()));
                p.setRateType(src.getRateType());
                p.setMinAmount(convert.parseDecimal("min_amount", src.getMinAmount()));
                p.setMaxAmount(convert.parseDecimal("max_amount", src.getMaxAmount()));
                p.setActive(convert.productActive("is_active", src.getStatusCode()));
                p.setEffectiveDate(convert.parseDate("effective_date", src.getEffectiveDate()));
                p.setExpirationDate(convert.parseDate("expiration_date", src.getExpirationDate()));
                products.save(p);
                report.recordInserted(T_PRODUCTS);
            } catch (ConversionException e) {
                report.recordFailure(T_PRODUCTS, key, e.getField(), e.getInvalidValue(), e.getMessage());
            }
        }
    }

    private void migrateLoanAccounts(MigrationReport report) {
        for (LegacyLoanAccount src : legacyLoanAccounts.findAll()) {
            String key = src.getLoanAccountNumber();
            if (loanAccounts.findByAccountNumber(key).isPresent()) {
                report.recordSkipped(T_LOAN_ACCOUNTS);
                continue;
            }

            Optional<Borrower> borrower = borrowers.findByExternalId(src.getBorrowerId());
            if (borrower.isEmpty()) {
                report.recordFailure(T_LOAN_ACCOUNTS, key, "borrower_id", src.getBorrowerId(),
                        "Borrower not found for external_id");
                continue;
            }
            Optional<LoanProduct> product = products.findByCode(src.getProductCode());
            if (product.isEmpty()) {
                report.recordFailure(T_LOAN_ACCOUNTS, key, "product_id", src.getProductCode(),
                        "Loan product not found for code");
                continue;
            }

            try {
                LoanAccount a = new LoanAccount();
                a.setAccountNumber(key);
                a.setBorrower(borrower.get());
                a.setProduct(product.get());
                a.setOriginalAmount(convert.parseDecimal("original_amount", src.getOriginalAmount()));
                a.setCurrentBalance(convert.parseDecimal("current_balance", src.getCurrentBalance()));
                a.setInterestRate(convert.parseDecimal("interest_rate", src.getInterestRate()));
                a.setTermMonths(convert.parseInteger("term_months", src.getTermMonths()));
                a.setMonthlyPayment(convert.parseDecimal("monthly_payment", src.getMonthlyPayment()));
                a.setOriginationDate(convert.parseDate("origination_date", src.getOriginationDate()));
                a.setMaturityDate(convert.parseDate("maturity_date", src.getMaturityDate()));
                a.setFirstPaymentDate(convert.parseDate("first_payment_date", src.getFirstPaymentDate()));
                a.setNextPaymentDate(convert.parseDate("next_payment_date", src.getNextPaymentDate()));
                a.setStatus(convert.loanStatus("status", src.getStatusCode()));
                a.setDelinquencyDays(convert.parseInteger("delinquency_days", src.getDelinquencyDays()));
                a.setEscrowBalance(convert.parseDecimal("escrow_balance", src.getEscrowBalance()));
                a.setLtvPercent(convert.parseDecimal("ltv_percent", src.getLtvPercent()));
                a.setPropertyAddress(src.getPropertyAddress());
                a.setPropertyCity(src.getPropertyCity());
                a.setPropertyState(src.getPropertyState());
                a.setPropertyZip(src.getPropertyZip());
                a.setPropertyType(convert.propertyType("property_type", src.getPropertyType()));
                a.setAppraisedValue(convert.parseDecimal("appraised_value", src.getAppraisedValue()));
                a.setCreatedAt(convert.parseTimestamp("created_at", src.getCreatedDate()));
                a.setUpdatedAt(convert.parseTimestamp("updated_at", src.getUpdatedDate()));
                loanAccounts.save(a);
                report.recordInserted(T_LOAN_ACCOUNTS);
            } catch (ConversionException e) {
                report.recordFailure(T_LOAN_ACCOUNTS, key, e.getField(), e.getInvalidValue(), e.getMessage());
            }
        }
    }

    private void migratePayments(MigrationReport report) {
        for (LegacyPayment src : legacyPayments.findAll()) {
            String key = src.getPaymentSequenceNumber();
            if (payments.findByExternalId(key).isPresent()) {
                report.recordSkipped(T_PAYMENTS);
                continue;
            }

            Optional<LoanAccount> loanAccount = loanAccounts.findByAccountNumber(src.getLoanAccountNumber());
            if (loanAccount.isEmpty()) {
                report.recordFailure(T_PAYMENTS, key, "loan_account_id", src.getLoanAccountNumber(),
                        "Loan account not found for account_number");
                continue;
            }

            try {
                Payment p = new Payment();
                p.setExternalId(key);
                p.setLoanAccount(loanAccount.get());
                p.setPaymentDate(convert.parseDate("payment_date", src.getPaymentDate()));
                p.setTotalAmount(convert.parseDecimal("total_amount", src.getTotalAmount()));
                p.setPrincipalAmount(convert.parseDecimal("principal_amount", src.getPrincipalAmount()));
                p.setInterestAmount(convert.parseDecimal("interest_amount", src.getInterestAmount()));
                p.setEscrowAmount(convert.parseDecimal("escrow_amount", src.getEscrowAmount()));
                p.setLateFee(convert.parseDecimal("late_fee", src.getLateFee()));
                p.setType(convert.paymentType("type", src.getTypeCode()));
                p.setStatus(convert.paymentStatus("status", src.getStatusCode()));
                p.setReceivedDate(convert.parseDate("received_date", src.getReceivedDate()));
                p.setProcessedDate(convert.parseDate("processed_date", src.getProcessedDate()));
                p.setCreatedAt(convert.parseTimestamp("created_at", src.getCreatedDate()));
                p.setUpdatedAt(convert.parseTimestamp("updated_at", src.getUpdatedDate()));
                payments.save(p);
                report.recordInserted(T_PAYMENTS);
            } catch (ConversionException e) {
                report.recordFailure(T_PAYMENTS, key, e.getField(), e.getInvalidValue(), e.getMessage());
            }
        }
    }
}
