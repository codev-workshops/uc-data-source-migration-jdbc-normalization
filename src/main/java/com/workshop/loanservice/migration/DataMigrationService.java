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

import java.util.List;

/**
 * Migrates the legacy CDW tables into the modern normalized tables at startup.
 *
 * <p>The migration is idempotent: modern tables are cleared and repopulated.
 * Legacy rows are validated and transformed by {@link LegacyValueParser}, and
 * foreign keys are resolved via natural keys (borrower {@code external_id},
 * product {@code code}, loan {@code account_number}). Any unresolvable
 * reference or missing mandatory value aborts the migration.
 */
@Service
@SuppressWarnings("deprecation") // the legacy types are deprecated but are this class's input
public class DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);

    private final LegacyBorrowerRepository legacyBorrowerRepository;
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    private final LegacyPaymentRepository legacyPaymentRepository;
    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;
    private final LegacyValueParser parser;

    public DataMigrationService(LegacyBorrowerRepository legacyBorrowerRepository,
                                LegacyLoanProductRepository legacyLoanProductRepository,
                                LegacyLoanAccountRepository legacyLoanAccountRepository,
                                LegacyPaymentRepository legacyPaymentRepository,
                                BorrowerRepository borrowerRepository,
                                LoanProductRepository loanProductRepository,
                                LoanAccountRepository loanAccountRepository,
                                PaymentRepository paymentRepository,
                                LegacyValueParser parser) {
        this.legacyBorrowerRepository = legacyBorrowerRepository;
        this.legacyLoanProductRepository = legacyLoanProductRepository;
        this.legacyLoanAccountRepository = legacyLoanAccountRepository;
        this.legacyPaymentRepository = legacyPaymentRepository;
        this.borrowerRepository = borrowerRepository;
        this.loanProductRepository = loanProductRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
        this.parser = parser;
    }

    @Transactional
    public MigrationReport migrate() {
        log.info("Starting legacy -> modern data migration");
        clearModernTables();

        migrateBorrowers();
        migrateProducts();
        migrateLoanAccounts();
        migratePayments();

        MigrationReport report = reconcile();
        log.info("Data migration complete: {}", report);
        return report;
    }

    private void clearModernTables() {
        paymentRepository.deleteAllInBatch();
        loanAccountRepository.deleteAllInBatch();
        loanProductRepository.deleteAllInBatch();
        borrowerRepository.deleteAllInBatch();
    }

    private void migrateBorrowers() {
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
        for (LegacyBorrower legacy : legacyBorrowers) {
            Borrower borrower = new Borrower();
            borrower.setExternalId(require(legacy.getBorrowerId(), "CDW_BORR_MSTR.BORR_ID"));
            borrower.setFirstName(require(legacy.getFirstName(), "CDW_BORR_MSTR.BORR_FST_NM"));
            borrower.setLastName(require(legacy.getLastName(), "CDW_BORR_MSTR.BORR_LST_NM"));
            borrower.setMiddleInitial(legacy.getMiddleInitial());
            borrower.setSsnHash(legacy.getSsnEncrypted());
            borrower.setDateOfBirth(parser.parseDate(legacy.getDateOfBirth()));
            borrower.setAddressLine1(legacy.getAddressLine1());
            borrower.setAddressLine2(legacy.getAddressLine2());
            borrower.setCity(legacy.getCity());
            borrower.setState(legacy.getStateCode());
            borrower.setZipCode(legacy.getZipCode());
            borrower.setPhone(legacy.getPhoneNumber());
            borrower.setEmail(legacy.getEmail());
            borrower.setCreditScore(parser.parseInteger(legacy.getCreditScore()));
            borrower.setEmploymentStatus(legacy.getEmploymentStatus());
            borrower.setAnnualIncome(parser.parseAmount(legacy.getAnnualIncome()));
            borrower.setStatus(parser.expandBorrowerStatus(legacy.getStatusCode()));
            borrower.setCreatedAt(parser.parseTimestamp(legacy.getCreatedDate()));
            borrower.setUpdatedAt(parser.parseTimestamp(legacy.getUpdatedDate()));
            borrowerRepository.save(borrower);
        }
        log.info("Migrated {} borrowers", legacyBorrowers.size());
    }

    private void migrateProducts() {
        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
        for (LegacyLoanProduct legacy : legacyProducts) {
            LoanProduct product = new LoanProduct();
            product.setCode(require(legacy.getProductCode(), "CDW_LN_PROD.PROD_CD"));
            product.setName(require(legacy.getDescription(), "CDW_LN_PROD.PROD_DESC_TXT"));
            product.setType(require(legacy.getTypeCode(), "CDW_LN_PROD.PROD_TYP_CD"));
            product.setTermMonths(requireValue(parser.parseInteger(legacy.getTermMonths()), "CDW_LN_PROD.PROD_TERM_MOS"));
            product.setRateType(require(legacy.getRateType(), "CDW_LN_PROD.PROD_RT_TYP"));
            product.setMinAmount(parser.parseAmount(legacy.getMinAmount()));
            product.setMaxAmount(parser.parseAmount(legacy.getMaxAmount()));
            product.setIsActive(parser.parseProductActive(legacy.getStatusCode()));
            product.setEffectiveDate(parser.parseDate(legacy.getEffectiveDate()));
            product.setExpirationDate(parser.parseDate(legacy.getExpirationDate()));
            loanProductRepository.save(product);
        }
        log.info("Migrated {} loan products", legacyProducts.size());
    }

    private void migrateLoanAccounts() {
        List<LegacyLoanAccount> legacyAccounts = legacyLoanAccountRepository.findAll();
        for (LegacyLoanAccount legacy : legacyAccounts) {
            String accountNumber = require(legacy.getLoanAccountNumber(), "CDW_LN_ACCT.LN_ACCT_NBR");
            Borrower borrower = borrowerRepository.findByExternalId(legacy.getBorrowerId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot resolve borrower '" + legacy.getBorrowerId() + "' for loan " + accountNumber));
            LoanProduct product = loanProductRepository.findByCode(legacy.getProductCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot resolve product '" + legacy.getProductCode() + "' for loan " + accountNumber));

            LoanAccount account = new LoanAccount();
            account.setAccountNumber(accountNumber);
            account.setBorrower(borrower);
            account.setProduct(product);
            account.setOriginalAmount(requireValue(parser.parseAmount(legacy.getOriginalAmount()), "CDW_LN_ACCT.LN_ORIG_AMT"));
            account.setCurrentBalance(requireValue(parser.parseAmount(legacy.getCurrentBalance()), "CDW_LN_ACCT.LN_CURR_BAL"));
            account.setInterestRate(requireValue(parser.parseAmount(legacy.getInterestRate()), "CDW_LN_ACCT.LN_INT_RT"));
            account.setTermMonths(requireValue(parser.parseInteger(legacy.getTermMonths()), "CDW_LN_ACCT.LN_TERM_MOS"));
            account.setMonthlyPayment(requireValue(parser.parseAmount(legacy.getMonthlyPayment()), "CDW_LN_ACCT.LN_PMT_AMT"));
            account.setOriginationDate(requireValue(parser.parseDate(legacy.getOriginationDate()), "CDW_LN_ACCT.LN_ORIG_DT"));
            account.setMaturityDate(requireValue(parser.parseDate(legacy.getMaturityDate()), "CDW_LN_ACCT.LN_MAT_DT"));
            account.setFirstPaymentDate(parser.parseDate(legacy.getFirstPaymentDate()));
            account.setNextPaymentDate(parser.parseDate(legacy.getNextPaymentDate()));
            account.setStatus(parser.expandLoanStatus(legacy.getStatusCode()));
            account.setDelinquencyDays(parser.parseInteger(legacy.getDelinquencyDays()));
            account.setEscrowBalance(parser.parseAmount(legacy.getEscrowBalance()));
            account.setLtvPercent(parser.parseAmount(legacy.getLtvPercent()));
            account.setPropertyAddress(legacy.getPropertyAddress());
            account.setPropertyCity(legacy.getPropertyCity());
            account.setPropertyState(legacy.getPropertyState());
            account.setPropertyZip(legacy.getPropertyZip());
            account.setPropertyType(parser.expandPropertyType(legacy.getPropertyType()));
            account.setAppraisedValue(parser.parseAmount(legacy.getAppraisedValue()));
            account.setCreatedAt(parser.parseTimestamp(legacy.getCreatedDate()));
            account.setUpdatedAt(parser.parseTimestamp(legacy.getUpdatedDate()));
            loanAccountRepository.save(account);
        }
        log.info("Migrated {} loan accounts", legacyAccounts.size());
    }

    private void migratePayments() {
        List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();
        for (LegacyPayment legacy : legacyPayments) {
            String legacyId = require(legacy.getPaymentSequenceNumber(), "CDW_PMT_HIST.PMT_SEQ_NBR");
            LoanAccount account = loanAccountRepository.findByAccountNumber(legacy.getLoanAccountNumber())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot resolve loan account '" + legacy.getLoanAccountNumber() + "' for payment " + legacyId));

            Payment payment = new Payment();
            payment.setLegacyId(legacyId);
            payment.setLoanAccount(account);
            payment.setPaymentDate(requireValue(parser.parseDate(legacy.getPaymentDate()), "CDW_PMT_HIST.PMT_DT"));
            payment.setTotalAmount(requireValue(parser.parseAmount(legacy.getTotalAmount()), "CDW_PMT_HIST.PMT_AMT"));
            payment.setPrincipalAmount(parser.parseAmount(legacy.getPrincipalAmount()));
            payment.setInterestAmount(parser.parseAmount(legacy.getInterestAmount()));
            payment.setEscrowAmount(parser.parseAmount(legacy.getEscrowAmount()));
            payment.setLateFee(parser.parseAmount(legacy.getLateFee()));
            payment.setType(requireValue(parser.expandPaymentType(legacy.getTypeCode()), "CDW_PMT_HIST.PMT_TYP_CD"));
            payment.setStatus(requireValue(parser.expandPaymentStatus(legacy.getStatusCode()), "CDW_PMT_HIST.PMT_STAT_CD"));
            payment.setReceivedDate(parser.parseDate(legacy.getReceivedDate()));
            payment.setProcessedDate(parser.parseDate(legacy.getProcessedDate()));
            payment.setCreatedAt(parser.parseTimestamp(legacy.getCreatedDate()));
            payment.setUpdatedAt(parser.parseTimestamp(legacy.getUpdatedDate()));
            paymentRepository.save(payment);
        }
        log.info("Migrated {} payments", legacyPayments.size());
    }

    /**
     * Compares legacy and modern row counts and verifies that every modern row
     * carries a resolvable relationship.
     */
    private MigrationReport reconcile() {
        MigrationReport report = new MigrationReport(
                legacyBorrowerRepository.count(), borrowerRepository.count(),
                legacyLoanProductRepository.count(), loanProductRepository.count(),
                legacyLoanAccountRepository.count(), loanAccountRepository.count(),
                legacyPaymentRepository.count(), paymentRepository.count());

        log.info("Reconciliation — borrowers {}/{}, products {}/{}, loan accounts {}/{}, payments {}/{}",
                report.modernBorrowers(), report.legacyBorrowers(),
                report.modernProducts(), report.legacyProducts(),
                report.modernLoanAccounts(), report.legacyLoanAccounts(),
                report.modernPayments(), report.legacyPayments());

        if (!report.countsMatch()) {
            throw new IllegalStateException("Migration reconciliation failed, row counts differ: " + report);
        }

        for (LoanAccount account : loanAccountRepository.findAll()) {
            if (account.getBorrower() == null || account.getProduct() == null) {
                throw new IllegalStateException("Loan account " + account.getAccountNumber() + " has unresolved relationships");
            }
        }
        for (Payment payment : paymentRepository.findAll()) {
            if (payment.getLoanAccount() == null) {
                throw new IllegalStateException("Payment " + payment.getLegacyId() + " has no loan account");
            }
        }
        return report;
    }

    private String require(String value, String source) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Mandatory legacy value missing: " + source);
        }
        return value;
    }

    private <T> T requireValue(T value, String source) {
        if (value == null) {
            throw new IllegalStateException("Mandatory legacy value missing: " + source);
        }
        return value;
    }

    /**
     * Legacy vs modern row counts produced by the reconciliation step.
     */
    public record MigrationReport(long legacyBorrowers, long modernBorrowers,
                                  long legacyProducts, long modernProducts,
                                  long legacyLoanAccounts, long modernLoanAccounts,
                                  long legacyPayments, long modernPayments) {

        public boolean countsMatch() {
            return legacyBorrowers == modernBorrowers
                    && legacyProducts == modernProducts
                    && legacyLoanAccounts == modernLoanAccounts
                    && legacyPayments == modernPayments;
        }
    }
}
