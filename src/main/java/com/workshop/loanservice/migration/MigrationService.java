package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Migrates data from the legacy CDW tables into the modern normalized schema.
 *
 * Process (in foreign-key dependency order):
 *   1. borrowers      — typed copy of CDW_BORR_MSTR
 *   2. loan_products  — typed copy of CDW_LN_PROD
 *   3. loan_accounts  — typed copy of CDW_LN_ACCT, with borrower/product foreign
 *                       keys resolved (denormalized borrower fields dropped)
 *   4. payments       — typed copy of CDW_PMT_HIST, with loan-account FK resolved
 *
 * The run is idempotent: existing modern rows are cleared first so it can be
 * re-executed safely. Foreign-key lookups that cannot be resolved fail fast, and
 * the run verifies that modern row counts match legacy row counts before
 * returning.
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private final LegacyBorrowerRepository legacyBorrowerRepository;
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    private final LegacyPaymentRepository legacyPaymentRepository;

    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    public MigrationService(LegacyBorrowerRepository legacyBorrowerRepository,
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
     * Run the full migration. Modern tables are cleared first, so this method is
     * safe to call repeatedly.
     *
     * @return the number of rows written for each entity
     */
    @Transactional("modernTransactionManager")
    public MigrationResult migrate() {
        log.info("Starting legacy → modern data migration");
        clearModernTables();

        Map<String, Borrower> borrowersByExternalId = migrateBorrowers();
        Map<String, LoanProduct> productsByCode = migrateLoanProducts();
        Map<String, LoanAccount> accountsByNumber =
                migrateLoanAccounts(borrowersByExternalId, productsByCode);
        migratePayments(accountsByNumber);

        MigrationResult result = new MigrationResult(
                borrowerRepository.count(),
                loanProductRepository.count(),
                loanAccountRepository.count(),
                paymentRepository.count());

        verifyRowCounts(result);
        log.info("Migration complete: {} borrowers, {} products, {} loan accounts, {} payments",
                result.borrowers(), result.loanProducts(), result.loanAccounts(), result.payments());
        return result;
    }

    private void clearModernTables() {
        // Reverse foreign-key order so child rows are removed before parents.
        paymentRepository.deleteAllInBatch();
        loanAccountRepository.deleteAllInBatch();
        loanProductRepository.deleteAllInBatch();
        borrowerRepository.deleteAllInBatch();
    }

    private Map<String, Borrower> migrateBorrowers() {
        Map<String, Borrower> byExternalId = new LinkedHashMap<>();
        for (LegacyBorrower legacy : legacyBorrowerRepository.findAll()) {
            Borrower borrower = new Borrower();
            borrower.setExternalId(legacy.getBorrowerId());
            borrower.setFirstName(legacy.getFirstName());
            borrower.setLastName(legacy.getLastName());
            borrower.setMiddleInitial(legacy.getMiddleInitial());
            borrower.setSsnHash(legacy.getSsnEncrypted());
            borrower.setDateOfBirth(LegacyValueConverters.parseDate(legacy.getDateOfBirth()));
            borrower.setAddressLine1(legacy.getAddressLine1());
            borrower.setAddressLine2(legacy.getAddressLine2());
            borrower.setCity(legacy.getCity());
            borrower.setState(legacy.getStateCode());
            borrower.setZipCode(legacy.getZipCode());
            borrower.setPhone(legacy.getPhoneNumber());
            borrower.setEmail(legacy.getEmail());
            borrower.setCreditScore(LegacyValueConverters.parseInteger(legacy.getCreditScore()));
            borrower.setEmploymentStatus(legacy.getEmploymentStatus());
            borrower.setAnnualIncome(LegacyValueConverters.parseAmount(legacy.getAnnualIncome()));
            borrower.setStatus(LegacyValueConverters.expandBorrowerStatus(legacy.getStatusCode()));
            borrower.setCreatedAt(LegacyValueConverters.parseTimestamp(legacy.getCreatedDate()));
            borrower.setUpdatedAt(LegacyValueConverters.parseTimestamp(legacy.getUpdatedDate()));
            // BORR_REC_TYP intentionally dropped — not part of the modern schema.

            borrower = borrowerRepository.save(borrower);
            byExternalId.put(borrower.getExternalId(), borrower);
        }
        return byExternalId;
    }

    private Map<String, LoanProduct> migrateLoanProducts() {
        Map<String, LoanProduct> byCode = new LinkedHashMap<>();
        for (LegacyLoanProduct legacy : legacyLoanProductRepository.findAll()) {
            LoanProduct product = new LoanProduct();
            product.setCode(legacy.getProductCode());
            product.setName(legacy.getDescription());
            product.setType(legacy.getTypeCode());
            product.setTermMonths(LegacyValueConverters.parseInteger(legacy.getTermMonths()));
            product.setRateType(legacy.getRateType());
            product.setMinAmount(LegacyValueConverters.parseAmount(legacy.getMinAmount()));
            product.setMaxAmount(LegacyValueConverters.parseAmount(legacy.getMaxAmount()));
            product.setActive(LegacyValueConverters.parseProductActive(legacy.getStatusCode()));
            product.setEffectiveDate(LegacyValueConverters.parseDate(legacy.getEffectiveDate()));
            product.setExpirationDate(LegacyValueConverters.parseDate(legacy.getExpirationDate()));

            product = loanProductRepository.save(product);
            byCode.put(product.getCode(), product);
        }
        return byCode;
    }

    private Map<String, LoanAccount> migrateLoanAccounts(Map<String, Borrower> borrowersByExternalId,
                                                         Map<String, LoanProduct> productsByCode) {
        Map<String, LoanAccount> byNumber = new LinkedHashMap<>();
        for (LegacyLoanAccount legacy : legacyLoanAccountRepository.findAll()) {
            Borrower borrower = borrowersByExternalId.get(legacy.getBorrowerId());
            if (borrower == null) {
                throw new IllegalStateException("Cannot resolve borrower foreign key for loan account "
                        + legacy.getLoanAccountNumber() + ": no borrower with external id "
                        + legacy.getBorrowerId());
            }
            LoanProduct product = productsByCode.get(legacy.getProductCode());
            if (product == null) {
                throw new IllegalStateException("Cannot resolve product foreign key for loan account "
                        + legacy.getLoanAccountNumber() + ": no product with code "
                        + legacy.getProductCode());
            }

            LoanAccount account = new LoanAccount();
            account.setAccountNumber(legacy.getLoanAccountNumber());
            account.setBorrower(borrower);
            account.setProduct(product);
            // Denormalized borrower fields (BORR_FST_NM, BORR_LST_NM, BORR_SSN_LST4)
            // are intentionally dropped — they are reachable via the borrower FK.
            account.setOriginalAmount(LegacyValueConverters.parseAmount(legacy.getOriginalAmount()));
            account.setCurrentBalance(LegacyValueConverters.parseAmount(legacy.getCurrentBalance()));
            account.setInterestRate(LegacyValueConverters.parseDecimal(legacy.getInterestRate()));
            account.setTermMonths(LegacyValueConverters.parseInteger(legacy.getTermMonths()));
            account.setMonthlyPayment(LegacyValueConverters.parseAmount(legacy.getMonthlyPayment()));
            account.setOriginationDate(LegacyValueConverters.parseDate(legacy.getOriginationDate()));
            account.setMaturityDate(LegacyValueConverters.parseDate(legacy.getMaturityDate()));
            account.setFirstPaymentDate(LegacyValueConverters.parseDate(legacy.getFirstPaymentDate()));
            account.setNextPaymentDate(LegacyValueConverters.parseDate(legacy.getNextPaymentDate()));
            account.setStatus(LegacyValueConverters.expandLoanStatus(legacy.getStatusCode()));
            account.setDelinquencyDays(LegacyValueConverters.parseInteger(legacy.getDelinquencyDays()));
            account.setEscrowBalance(LegacyValueConverters.parseAmount(legacy.getEscrowBalance()));
            account.setLtvPercent(LegacyValueConverters.parseDecimal(legacy.getLtvPercent()));
            account.setPropertyAddress(legacy.getPropertyAddress());
            account.setPropertyCity(legacy.getPropertyCity());
            account.setPropertyState(legacy.getPropertyState());
            account.setPropertyZip(legacy.getPropertyZip());
            account.setPropertyType(LegacyValueConverters.expandPropertyType(legacy.getPropertyType()));
            account.setAppraisedValue(LegacyValueConverters.parseAmount(legacy.getAppraisedValue()));
            account.setCreatedAt(LegacyValueConverters.parseTimestamp(legacy.getCreatedDate()));
            account.setUpdatedAt(LegacyValueConverters.parseTimestamp(legacy.getUpdatedDate()));

            account = loanAccountRepository.save(account);
            byNumber.put(account.getAccountNumber(), account);
        }
        return byNumber;
    }

    private void migratePayments(Map<String, LoanAccount> accountsByNumber) {
        for (LegacyPayment legacy : legacyPaymentRepository.findAll()) {
            LoanAccount account = accountsByNumber.get(legacy.getLoanAccountNumber());
            if (account == null) {
                throw new IllegalStateException("Cannot resolve loan-account foreign key for payment "
                        + legacy.getPaymentSequenceNumber() + ": no loan account with number "
                        + legacy.getLoanAccountNumber());
            }

            Payment payment = new Payment();
            payment.setExternalId(legacy.getPaymentSequenceNumber());
            payment.setLoanAccount(account);
            payment.setPaymentDate(LegacyValueConverters.parseDate(legacy.getPaymentDate()));
            payment.setTotalAmount(LegacyValueConverters.parseAmount(legacy.getTotalAmount()));
            payment.setPrincipalAmount(LegacyValueConverters.parseAmount(legacy.getPrincipalAmount()));
            payment.setInterestAmount(LegacyValueConverters.parseAmount(legacy.getInterestAmount()));
            payment.setEscrowAmount(LegacyValueConverters.parseAmount(legacy.getEscrowAmount()));
            payment.setLateFee(LegacyValueConverters.parseAmount(legacy.getLateFee()));
            payment.setType(LegacyValueConverters.expandPaymentType(legacy.getTypeCode()));
            payment.setStatus(LegacyValueConverters.expandPaymentStatus(legacy.getStatusCode()));
            payment.setReceivedDate(LegacyValueConverters.parseDate(legacy.getReceivedDate()));
            payment.setProcessedDate(LegacyValueConverters.parseDate(legacy.getProcessedDate()));
            payment.setCreatedAt(LegacyValueConverters.parseTimestamp(legacy.getCreatedDate()));
            payment.setUpdatedAt(LegacyValueConverters.parseTimestamp(legacy.getUpdatedDate()));

            paymentRepository.save(payment);
        }
    }

    private void verifyRowCounts(MigrationResult result) {
        assertCount("borrowers", legacyBorrowerRepository.count(), result.borrowers());
        assertCount("loan_products", legacyLoanProductRepository.count(), result.loanProducts());
        assertCount("loan_accounts", legacyLoanAccountRepository.count(), result.loanAccounts());
        assertCount("payments", legacyPaymentRepository.count(), result.payments());
    }

    private void assertCount(String table, long expected, long actual) {
        if (expected != actual) {
            throw new IllegalStateException("Row count mismatch for " + table
                    + ": legacy=" + expected + " modern=" + actual);
        }
    }
}
