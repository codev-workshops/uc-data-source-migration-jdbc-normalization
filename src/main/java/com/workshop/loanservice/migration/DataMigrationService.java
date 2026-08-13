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
import java.util.Optional;

/**
 * Copies the legacy CDW rows into the modern normalized tables, applying the
 * type conversions and code expansions from {@code data/mappings/column_mappings.md}.
 *
 * <p>Migration runs in dependency order: borrowers and products, then loan accounts
 * (which resolve borrower/product foreign keys), then payments.</p>
 */
@Service
public class DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyLoanAccounts;
    private final LegacyPaymentRepository legacyPayments;
    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository loanAccounts;
    private final PaymentRepository payments;

    public DataMigrationService(LegacyBorrowerRepository legacyBorrowers,
                                LegacyLoanProductRepository legacyProducts,
                                LegacyLoanAccountRepository legacyLoanAccounts,
                                LegacyPaymentRepository legacyPayments,
                                BorrowerRepository borrowers,
                                LoanProductRepository products,
                                LoanAccountRepository loanAccounts,
                                PaymentRepository payments) {
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyLoanAccounts = legacyLoanAccounts;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.loanAccounts = loanAccounts;
        this.payments = payments;
    }

    @Transactional
    public MigrationReport migrate() {
        MigrationReport report = new MigrationReport();
        migrateBorrowers(report);
        migrateProducts(report);
        migrateLoanAccounts(report);
        migratePayments(report);
        validate(report);
        return report;
    }

    private void migrateBorrowers(MigrationReport report) {
        for (LegacyBorrower legacy : legacyBorrowers.findAll()) {
            String externalId = LegacyValueParser.text(legacy.getBorrowerId());
            if (externalId == null) {
                report.skip("borrower with blank BORR_ID");
                continue;
            }
            if (borrowers.existsByExternalId(externalId)) {
                report.skip("duplicate borrower " + externalId);
                continue;
            }
            try {
                Borrower borrower = new Borrower();
                borrower.setExternalId(externalId);
                borrower.setFirstName(LegacyValueParser.text(legacy.getFirstName()));
                borrower.setLastName(LegacyValueParser.text(legacy.getLastName()));
                borrower.setMiddleInitial(LegacyValueParser.text(legacy.getMiddleInitial()));
                borrower.setSsnHash(LegacyValueParser.text(legacy.getSsnEncrypted()));
                borrower.setDateOfBirth(LegacyValueParser.date(legacy.getDateOfBirth()));
                borrower.setAddressLine1(LegacyValueParser.text(legacy.getAddressLine1()));
                borrower.setAddressLine2(LegacyValueParser.text(legacy.getAddressLine2()));
                borrower.setCity(LegacyValueParser.text(legacy.getCity()));
                borrower.setState(LegacyValueParser.text(legacy.getStateCode()));
                borrower.setZipCode(LegacyValueParser.text(legacy.getZipCode()));
                borrower.setPhone(LegacyValueParser.text(legacy.getPhoneNumber()));
                borrower.setEmail(LegacyValueParser.text(legacy.getEmail()));
                borrower.setCreditScore(LegacyValueParser.integer(legacy.getCreditScore()));
                borrower.setEmploymentStatus(LegacyValueParser.text(legacy.getEmploymentStatus()));
                borrower.setAnnualIncome(LegacyValueParser.amount(legacy.getAnnualIncome()));
                borrower.setStatus(LegacyValueParser.borrowerStatus(legacy.getStatusCode()));
                borrower.setCreatedAt(LegacyValueParser.timestamp(legacy.getCreatedDate()));
                borrower.setUpdatedAt(LegacyValueParser.timestamp(legacy.getUpdatedDate()));
                borrowers.save(borrower);
                report.borrowerMigrated();
            } catch (MigrationDataException e) {
                report.skip("borrower " + externalId + ": " + e.getMessage());
                log.warn("Skipping borrower {}: {}", externalId, e.getMessage());
            }
        }
    }

    private void migrateProducts(MigrationReport report) {
        for (LegacyLoanProduct legacy : legacyProducts.findAll()) {
            String code = LegacyValueParser.text(legacy.getProductCode());
            if (code == null) {
                report.skip("product with blank PROD_CD");
                continue;
            }
            if (products.existsByCode(code)) {
                report.skip("duplicate product " + code);
                continue;
            }
            try {
                LoanProduct product = new LoanProduct();
                product.setCode(code);
                product.setName(LegacyValueParser.text(legacy.getDescription()));
                product.setType(LegacyValueParser.text(legacy.getTypeCode()));
                product.setTermMonths(LegacyValueParser.integer(legacy.getTermMonths()));
                product.setRateType(LegacyValueParser.text(legacy.getRateType()));
                product.setMinAmount(LegacyValueParser.amount(legacy.getMinAmount()));
                product.setMaxAmount(LegacyValueParser.amount(legacy.getMaxAmount()));
                product.setActive(LegacyValueParser.activeFlag(legacy.getStatusCode()));
                product.setEffectiveDate(LegacyValueParser.date(legacy.getEffectiveDate()));
                product.setExpirationDate(LegacyValueParser.date(legacy.getExpirationDate()));
                products.save(product);
                report.productMigrated();
            } catch (MigrationDataException e) {
                report.skip("product " + code + ": " + e.getMessage());
                log.warn("Skipping product {}: {}", code, e.getMessage());
            }
        }
    }

    private void migrateLoanAccounts(MigrationReport report) {
        for (LegacyLoanAccount legacy : legacyLoanAccounts.findAll()) {
            String accountNumber = LegacyValueParser.text(legacy.getLoanAccountNumber());
            if (accountNumber == null) {
                report.skip("loan account with blank LN_ACCT_NBR");
                continue;
            }
            if (loanAccounts.existsByAccountNumber(accountNumber)) {
                report.skip("duplicate loan account " + accountNumber);
                continue;
            }
            Optional<Borrower> borrower = borrowers.findByExternalId(LegacyValueParser.text(legacy.getBorrowerId()));
            Optional<LoanProduct> product = products.findByCode(LegacyValueParser.text(legacy.getProductCode()));
            if (borrower.isEmpty() || product.isEmpty()) {
                report.skip("loan account " + accountNumber + ": unresolved borrower/product FK");
                log.warn("Skipping loan account {}: unresolved borrower {} or product {}",
                        accountNumber, legacy.getBorrowerId(), legacy.getProductCode());
                continue;
            }
            try {
                LoanAccount account = new LoanAccount();
                account.setAccountNumber(accountNumber);
                account.setBorrower(borrower.get());
                account.setProduct(product.get());
                account.setOriginalAmount(LegacyValueParser.amount(legacy.getOriginalAmount()));
                account.setCurrentBalance(LegacyValueParser.amount(legacy.getCurrentBalance()));
                account.setInterestRate(LegacyValueParser.decimal(legacy.getInterestRate()));
                account.setTermMonths(LegacyValueParser.integer(legacy.getTermMonths()));
                account.setMonthlyPayment(LegacyValueParser.amount(legacy.getMonthlyPayment()));
                account.setOriginationDate(LegacyValueParser.date(legacy.getOriginationDate()));
                account.setMaturityDate(LegacyValueParser.date(legacy.getMaturityDate()));
                account.setFirstPaymentDate(LegacyValueParser.date(legacy.getFirstPaymentDate()));
                account.setNextPaymentDate(LegacyValueParser.date(legacy.getNextPaymentDate()));
                account.setStatus(LegacyValueParser.loanStatus(legacy.getStatusCode()));
                account.setDelinquencyDays(LegacyValueParser.integer(legacy.getDelinquencyDays()));
                account.setEscrowBalance(LegacyValueParser.amount(legacy.getEscrowBalance()));
                account.setLtvPercent(LegacyValueParser.decimal(legacy.getLtvPercent()));
                account.setPropertyAddress(LegacyValueParser.text(legacy.getPropertyAddress()));
                account.setPropertyCity(LegacyValueParser.text(legacy.getPropertyCity()));
                account.setPropertyState(LegacyValueParser.text(legacy.getPropertyState()));
                account.setPropertyZip(LegacyValueParser.text(legacy.getPropertyZip()));
                account.setPropertyType(LegacyValueParser.propertyType(legacy.getPropertyType()));
                account.setAppraisedValue(LegacyValueParser.amount(legacy.getAppraisedValue()));
                account.setCreatedAt(LegacyValueParser.timestamp(legacy.getCreatedDate()));
                account.setUpdatedAt(LegacyValueParser.timestamp(legacy.getUpdatedDate()));
                loanAccounts.save(account);
                report.loanAccountMigrated();
            } catch (MigrationDataException e) {
                report.skip("loan account " + accountNumber + ": " + e.getMessage());
                log.warn("Skipping loan account {}: {}", accountNumber, e.getMessage());
            }
        }
    }

    private void migratePayments(MigrationReport report) {
        for (LegacyPayment legacy : legacyPayments.findAll()) {
            String externalId = LegacyValueParser.text(legacy.getPaymentSequenceNumber());
            if (externalId == null) {
                report.skip("payment with blank PMT_SEQ_NBR");
                continue;
            }
            if (payments.existsByExternalId(externalId)) {
                report.skip("duplicate payment " + externalId);
                continue;
            }
            Optional<LoanAccount> account =
                    loanAccounts.findByAccountNumber(LegacyValueParser.text(legacy.getLoanAccountNumber()));
            if (account.isEmpty()) {
                report.skip("payment " + externalId + ": unresolved loan account FK");
                log.warn("Skipping payment {}: unresolved loan account {}", externalId, legacy.getLoanAccountNumber());
                continue;
            }
            try {
                Payment payment = new Payment();
                payment.setExternalId(externalId);
                payment.setLoanAccount(account.get());
                payment.setPaymentDate(LegacyValueParser.date(legacy.getPaymentDate()));
                payment.setTotalAmount(LegacyValueParser.amount(legacy.getTotalAmount()));
                payment.setPrincipalAmount(LegacyValueParser.amount(legacy.getPrincipalAmount()));
                payment.setInterestAmount(LegacyValueParser.amount(legacy.getInterestAmount()));
                payment.setEscrowAmount(LegacyValueParser.amount(legacy.getEscrowAmount()));
                payment.setLateFee(LegacyValueParser.amount(legacy.getLateFee()));
                payment.setType(LegacyValueParser.paymentType(legacy.getTypeCode()));
                payment.setStatus(LegacyValueParser.paymentStatus(legacy.getStatusCode()));
                payment.setReceivedDate(LegacyValueParser.date(legacy.getReceivedDate()));
                payment.setProcessedDate(LegacyValueParser.date(legacy.getProcessedDate()));
                payment.setCreatedAt(LegacyValueParser.timestamp(legacy.getCreatedDate()));
                payment.setUpdatedAt(LegacyValueParser.timestamp(legacy.getUpdatedDate()));
                payments.save(payment);
                report.paymentMigrated();
            } catch (MigrationDataException e) {
                report.skip("payment " + externalId + ": " + e.getMessage());
                log.warn("Skipping payment {}: {}", externalId, e.getMessage());
            }
        }
    }

    /**
     * Compares modern row counts and converted amounts against the legacy source.
     */
    private void validate(MigrationReport report) {
        compareCounts(report, "borrowers", legacyBorrowers.count(), borrowers.count());
        compareCounts(report, "loan_products", legacyProducts.count(), products.count());
        compareCounts(report, "loan_accounts", legacyLoanAccounts.count(), loanAccounts.count());
        compareCounts(report, "payments", legacyPayments.count(), payments.count());

        for (LegacyLoanAccount legacy : legacyLoanAccounts.findAll()) {
            loanAccounts.findByAccountNumber(legacy.getLoanAccountNumber()).ifPresent(account -> {
                compareAmount(report, "loan_accounts." + legacy.getLoanAccountNumber() + ".original_amount",
                        LegacyValueParser.amount(legacy.getOriginalAmount()), account.getOriginalAmount());
                compareAmount(report, "loan_accounts." + legacy.getLoanAccountNumber() + ".current_balance",
                        LegacyValueParser.amount(legacy.getCurrentBalance()), account.getCurrentBalance());
            });
        }

        for (LegacyPayment legacy : legacyPayments.findAll()) {
            payments.findByExternalId(legacy.getPaymentSequenceNumber()).ifPresent(payment ->
                    compareAmount(report, "payments." + legacy.getPaymentSequenceNumber() + ".total_amount",
                            LegacyValueParser.amount(legacy.getTotalAmount()), payment.getTotalAmount()));
        }
    }

    private void compareCounts(MigrationReport report, String table, long legacyCount, long modernCount) {
        if (legacyCount != modernCount) {
            report.validationFailure(table + ": legacy=" + legacyCount + " modern=" + modernCount);
        }
    }

    private void compareAmount(MigrationReport report, String field, BigDecimal legacy, BigDecimal modern) {
        boolean equal = legacy == null ? modern == null : modern != null && legacy.compareTo(modern) == 0;
        if (!equal) {
            report.validationFailure(field + ": legacy=" + legacy + " modern=" + modern);
        }
    }
}
