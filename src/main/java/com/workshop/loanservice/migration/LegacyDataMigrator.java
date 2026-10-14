package com.workshop.loanservice.migration;

import static com.workshop.loanservice.migration.LegacyValueParser.amount;
import static com.workshop.loanservice.migration.LegacyValueParser.code;
import static com.workshop.loanservice.migration.LegacyValueParser.date;
import static com.workshop.loanservice.migration.LegacyValueParser.integer;
import static com.workshop.loanservice.migration.LegacyValueParser.required;
import static com.workshop.loanservice.migration.LegacyValueParser.timestamp;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.BorrowerStatus;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.LoanStatus;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.entity.PaymentStatus;
import com.workshop.loanservice.modern.entity.PaymentType;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies the CDW tables into the normalized schema. Rows are upserted on their natural keys
 * (borrower external id, product code, account number, legacy payment id), so re-running is safe.
 * A row that cannot be converted is skipped and reported instead of aborting the run.
 */
@Service
public class LegacyDataMigrator {

    static final String BORROWERS = "borrowers";
    static final String LOAN_PRODUCTS = "loan_products";
    static final String LOAN_ACCOUNTS = "loan_accounts";
    static final String PAYMENTS = "payments";

    private static final Map<String, String> PROPERTY_TYPES =
            Map.of(
                    "SFR", "Single Family Residence",
                    "CND", "Condominium",
                    "MFR", "Multi-Family Residence",
                    "TWN", "Townhouse");

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyLoans;
    private final LegacyPaymentRepository legacyPayments;
    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository loans;
    private final PaymentRepository payments;

    public LegacyDataMigrator(
            LegacyBorrowerRepository legacyBorrowers,
            LegacyLoanProductRepository legacyProducts,
            LegacyLoanAccountRepository legacyLoans,
            LegacyPaymentRepository legacyPayments,
            BorrowerRepository borrowers,
            LoanProductRepository products,
            LoanAccountRepository loans,
            PaymentRepository payments) {
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyLoans = legacyLoans;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.loans = loans;
        this.payments = payments;
    }

    @Transactional("modernTransactionManager")
    public MigrationReport migrate() {
        MigrationReport report = new MigrationReport();
        List<LegacyLoanProduct> legacyProductRows = legacyProducts.findAll();
        List<LegacyBorrower> legacyBorrowerRows = legacyBorrowers.findAll();
        List<LegacyLoanAccount> legacyLoanRows = legacyLoans.findAll();
        List<LegacyPayment> legacyPaymentRows = legacyPayments.findAll();

        migrateEach(LOAN_PRODUCTS, legacyProductRows, LegacyLoanProduct::getProductCode,
                this::upsertProduct, report);
        migrateEach(BORROWERS, legacyBorrowerRows, LegacyBorrower::getBorrowerId,
                this::upsertBorrower, report);
        migrateEach(LOAN_ACCOUNTS, legacyLoanRows, LegacyLoanAccount::getLoanAccountNumber,
                this::upsertLoan, report);
        migrateEach(PAYMENTS, legacyPaymentRows, LegacyPayment::getPaymentSequenceNumber,
                this::upsertPayment, report);

        List<Borrower> borrowerRows = borrowers.findAll();
        List<LoanProduct> productRows = products.findAll();
        List<LoanAccount> loanRows = loans.findAll();
        List<Payment> paymentRows = payments.findAll();
        if (checkCount(report, LOAN_PRODUCTS, legacyProductRows, productRows)) {
            checkSum(report, LOAN_PRODUCTS + ".max_amount",
                    sum(legacyProductRows, p -> amount(p.getMaxAmount())),
                    sum(productRows, LoanProduct::getMaxAmount));
        }
        if (checkCount(report, BORROWERS, legacyBorrowerRows, borrowerRows)) {
            checkSum(report, BORROWERS + ".annual_income",
                    sum(legacyBorrowerRows, b -> amount(b.getAnnualIncome())),
                    sum(borrowerRows, Borrower::getAnnualIncome));
        }
        if (checkCount(report, LOAN_ACCOUNTS, legacyLoanRows, loanRows)) {
            checkSum(report, LOAN_ACCOUNTS + ".original_amount",
                    sum(legacyLoanRows, l -> amount(l.getOriginalAmount())),
                    sum(loanRows, LoanAccount::getOriginalAmount));
            checkSum(report, LOAN_ACCOUNTS + ".current_balance",
                    sum(legacyLoanRows, l -> amount(l.getCurrentBalance())),
                    sum(loanRows, LoanAccount::getCurrentBalance));
        }
        if (checkCount(report, PAYMENTS, legacyPaymentRows, paymentRows)) {
            checkSum(report, PAYMENTS + ".total_amount",
                    sum(legacyPaymentRows, p -> amount(p.getTotalAmount())),
                    sum(paymentRows, Payment::getTotalAmount));
        }
        return report;
    }

    private void upsertProduct(LegacyLoanProduct legacy) {
        LoanProduct product = new LoanProduct();
        product.setCode(required(legacy.getProductCode(), "PROD_CD"));
        product.setName(required(legacy.getDescription(), "PROD_DESC_TXT"));
        product.setType(required(legacy.getTypeCode(), "PROD_TYP_CD"));
        product.setTermMonths(required(integer(legacy.getTermMonths()), "PROD_TERM_MOS"));
        product.setRateType(required(legacy.getRateType(), "PROD_RT_TYP"));
        product.setMinAmount(amount(legacy.getMinAmount()));
        product.setMaxAmount(amount(legacy.getMaxAmount()));
        product.setActive(isActiveProduct(legacy.getStatusCode()));
        product.setEffectiveDate(date(legacy.getEffectiveDate()));
        product.setExpirationDate(date(legacy.getExpirationDate()));
        products.findByCode(product.getCode())
                .ifPresent(existing -> product.setId(existing.getId()));
        products.save(product);
    }

    private void upsertBorrower(LegacyBorrower legacy) {
        Borrower borrower = new Borrower();
        borrower.setExternalId(required(legacy.getBorrowerId(), "BORR_ID"));
        borrower.setFirstName(required(legacy.getFirstName(), "BORR_FST_NM"));
        borrower.setLastName(required(legacy.getLastName(), "BORR_LST_NM"));
        borrower.setMiddleInitial(legacy.getMiddleInitial());
        borrower.setSsnHash(legacy.getSsnEncrypted());
        borrower.setDateOfBirth(date(legacy.getDateOfBirth()));
        borrower.setAddressLine1(legacy.getAddressLine1());
        borrower.setAddressLine2(legacy.getAddressLine2());
        borrower.setCity(legacy.getCity());
        borrower.setState(legacy.getStateCode());
        borrower.setZipCode(legacy.getZipCode());
        borrower.setPhone(legacy.getPhoneNumber());
        borrower.setEmail(legacy.getEmail());
        borrower.setCreditScore(integer(legacy.getCreditScore()));
        borrower.setEmploymentStatus(legacy.getEmploymentStatus());
        borrower.setAnnualIncome(amount(legacy.getAnnualIncome()));
        borrower.setStatus(code(BorrowerStatus.class, legacy.getStatusCode()));
        borrower.setCreatedAt(timestamp(legacy.getCreatedDate()));
        borrower.setUpdatedAt(timestamp(legacy.getUpdatedDate()));
        borrowers.findByExternalId(borrower.getExternalId())
                .ifPresent(existing -> borrower.setId(existing.getId()));
        borrowers.save(borrower);
    }

    private void upsertLoan(LegacyLoanAccount legacy) {
        LoanAccount loan = new LoanAccount();
        loan.setAccountNumber(required(legacy.getLoanAccountNumber(), "LN_ACCT_NBR"));
        loan.setBorrower(borrowers.findByExternalId(legacy.getBorrowerId())
                .orElseThrow(() -> unresolved("borrower", legacy.getBorrowerId())));
        loan.setProduct(products.findByCode(legacy.getProductCode())
                .orElseThrow(() -> unresolved("product", legacy.getProductCode())));
        loan.setOriginalAmount(required(amount(legacy.getOriginalAmount()), "LN_ORIG_AMT"));
        loan.setCurrentBalance(required(amount(legacy.getCurrentBalance()), "LN_CURR_BAL"));
        loan.setInterestRate(required(amount(legacy.getInterestRate()), "LN_INT_RT"));
        loan.setTermMonths(required(integer(legacy.getTermMonths()), "LN_TERM_MOS"));
        loan.setMonthlyPayment(required(amount(legacy.getMonthlyPayment()), "LN_PMT_AMT"));
        loan.setOriginationDate(required(date(legacy.getOriginationDate()), "LN_ORIG_DT"));
        loan.setMaturityDate(required(date(legacy.getMaturityDate()), "LN_MAT_DT"));
        loan.setFirstPaymentDate(date(legacy.getFirstPaymentDate()));
        loan.setNextPaymentDate(date(legacy.getNextPaymentDate()));
        loan.setStatus(code(LoanStatus.class, legacy.getStatusCode()));
        loan.setDelinquencyDays(integer(legacy.getDelinquencyDays()));
        loan.setEscrowBalance(amount(legacy.getEscrowBalance()));
        loan.setLtvPercent(amount(legacy.getLtvPercent()));
        loan.setPropertyAddress(legacy.getPropertyAddress());
        loan.setPropertyCity(legacy.getPropertyCity());
        loan.setPropertyState(legacy.getPropertyState());
        loan.setPropertyZip(legacy.getPropertyZip());
        loan.setPropertyType(propertyType(legacy.getPropertyType()));
        loan.setAppraisedValue(amount(legacy.getAppraisedValue()));
        loan.setCreatedAt(timestamp(legacy.getCreatedDate()));
        loan.setUpdatedAt(timestamp(legacy.getUpdatedDate()));
        loans.findByAccountNumber(loan.getAccountNumber())
                .ifPresent(existing -> loan.setId(existing.getId()));
        loans.save(loan);
    }

    private void upsertPayment(LegacyPayment legacy) {
        Payment payment = new Payment();
        payment.setLegacyPaymentId(required(legacy.getPaymentSequenceNumber(), "PMT_SEQ_NBR"));
        payment.setLoanAccount(loans.findByAccountNumber(legacy.getLoanAccountNumber())
                .orElseThrow(() -> unresolved("loan account", legacy.getLoanAccountNumber())));
        payment.setPaymentDate(required(date(legacy.getPaymentDate()), "PMT_DT"));
        payment.setTotalAmount(required(amount(legacy.getTotalAmount()), "PMT_AMT"));
        payment.setPrincipalAmount(amount(legacy.getPrincipalAmount()));
        payment.setInterestAmount(amount(legacy.getInterestAmount()));
        payment.setEscrowAmount(amount(legacy.getEscrowAmount()));
        payment.setLateFee(amount(legacy.getLateFee()));
        payment.setType(code(PaymentType.class, legacy.getTypeCode()));
        payment.setStatus(code(PaymentStatus.class, legacy.getStatusCode()));
        payment.setReceivedDate(date(legacy.getReceivedDate()));
        payment.setProcessedDate(date(legacy.getProcessedDate()));
        payment.setCreatedAt(timestamp(legacy.getCreatedDate()));
        payment.setUpdatedAt(timestamp(legacy.getUpdatedDate()));
        payments.findByLegacyPaymentId(payment.getLegacyPaymentId())
                .ifPresent(existing -> payment.setId(existing.getId()));
        payments.save(payment);
    }

    private static <L> void migrateEach(
            String table,
            List<L> legacyRows,
            Function<L, String> key,
            Consumer<L> upsert,
            MigrationReport report) {
        for (L legacy : legacyRows) {
            try {
                upsert.accept(legacy);
                report.recordMigrated(table);
            } catch (IllegalArgumentException e) {
                report.recordProblem(
                        "rejected " + table + " " + key.apply(legacy) + ": " + e.getMessage());
            }
        }
    }

    private static boolean checkCount(
            MigrationReport report, String table, List<?> legacyRows, List<?> modernRows) {
        if (legacyRows.size() == modernRows.size()) {
            return true;
        }
        report.recordProblem(table + ": " + legacyRows.size() + " legacy rows but "
                + modernRows.size() + " modern rows");
        return false;
    }

    private static void checkSum(
            MigrationReport report, String column, BigDecimal legacySum, BigDecimal modernSum) {
        if (legacySum.compareTo(modernSum) != 0) {
            report.recordProblem(
                    column + ": legacy sum " + legacySum + " but modern sum " + modernSum);
        }
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> value) {
        return rows.stream()
                .map(value)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isActiveProduct(String statusCode) {
        return switch (String.valueOf(statusCode)) {
            case "ACT" -> true;
            case "INA" -> false;
            default -> throw new IllegalArgumentException(
                    "unknown product status code '" + statusCode + "'");
        };
    }

    private static String propertyType(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String label = PROPERTY_TYPES.get(code);
        if (label == null) {
            throw new IllegalArgumentException("unknown property type code '" + code + "'");
        }
        return label;
    }

    private static IllegalArgumentException unresolved(String entity, String key) {
        return new IllegalArgumentException("unknown " + entity + " '" + key + "'");
    }
}
