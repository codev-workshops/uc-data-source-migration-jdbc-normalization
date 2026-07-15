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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LegacyDataMigrationService {

    private static final String BORROWER_TABLE = "CDW_BORR_MSTR";
    private static final String PRODUCT_TABLE = "CDW_LN_PROD";
    private static final String LOAN_TABLE = "CDW_LN_ACCT";
    private static final String PAYMENT_TABLE = "CDW_PMT_HIST";

    private final LegacyBorrowerRepository legacyBorrowerRepository;
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    private final LegacyPaymentRepository legacyPaymentRepository;
    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;
    private final LegacyValueTransformer transformer;

    public LegacyDataMigrationService(
            LegacyBorrowerRepository legacyBorrowerRepository,
            LegacyLoanProductRepository legacyLoanProductRepository,
            LegacyLoanAccountRepository legacyLoanAccountRepository,
            LegacyPaymentRepository legacyPaymentRepository,
            BorrowerRepository borrowerRepository,
            LoanProductRepository loanProductRepository,
            LoanAccountRepository loanAccountRepository,
            PaymentRepository paymentRepository,
            LegacyValueTransformer transformer
    ) {
        this.legacyBorrowerRepository = legacyBorrowerRepository;
        this.legacyLoanProductRepository = legacyLoanProductRepository;
        this.legacyLoanAccountRepository = legacyLoanAccountRepository;
        this.legacyPaymentRepository = legacyPaymentRepository;
        this.borrowerRepository = borrowerRepository;
        this.loanProductRepository = loanProductRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
        this.transformer = transformer;
    }

    @Transactional
    public LegacyDataMigrationResult migrate() {
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
        List<LegacyLoanAccount> legacyLoanAccounts = legacyLoanAccountRepository.findAll();
        List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();

        if (hasModernRows()) {
            if (isAlreadyMigrated(
                    legacyBorrowers,
                    legacyProducts,
                    legacyLoanAccounts,
                    legacyPayments
            )) {
                return new LegacyDataMigrationResult(
                        legacyBorrowers.size(),
                        legacyProducts.size(),
                        legacyLoanAccounts.size(),
                        legacyPayments.size(),
                        true
                );
            }
            throw new LegacyDataMigrationException(
                    "Modern target is partially populated or conflicts with legacy source"
            );
        }

        List<Borrower> borrowers = borrowerRepository.saveAllAndFlush(
                legacyBorrowers.stream()
                        .map(this::toBorrower)
                        .toList()
        );
        Map<String, Borrower> borrowerByExternalId = borrowers.stream()
                .collect(Collectors.toMap(Borrower::getExternalId, Function.identity()));

        List<LoanProduct> products = loanProductRepository.saveAllAndFlush(
                legacyProducts.stream()
                        .map(this::toLoanProduct)
                        .toList()
        );
        Map<String, LoanProduct> productByCode = products.stream()
                .collect(Collectors.toMap(LoanProduct::getCode, Function.identity()));

        List<LoanAccount> loanAccounts = loanAccountRepository.saveAllAndFlush(
                legacyLoanAccounts.stream()
                        .map(legacyLoanAccount -> toLoanAccount(
                                legacyLoanAccount,
                                borrowerByExternalId,
                                productByCode
                        ))
                        .toList()
        );
        Map<String, LoanAccount> loanAccountByAccountNumber = loanAccounts.stream()
                .collect(Collectors.toMap(LoanAccount::getAccountNumber, Function.identity()));

        paymentRepository.saveAllAndFlush(
                legacyPayments.stream()
                        .map(legacyPayment -> toPayment(
                                legacyPayment,
                                loanAccountByAccountNumber
                        ))
                        .toList()
        );

        return new LegacyDataMigrationResult(
                legacyBorrowers.size(),
                legacyProducts.size(),
                legacyLoanAccounts.size(),
                legacyPayments.size(),
                false
        );
    }

    private boolean hasModernRows() {
        return borrowerRepository.count() > 0
                || loanProductRepository.count() > 0
                || loanAccountRepository.count() > 0
                || paymentRepository.count() > 0;
    }

    private boolean isAlreadyMigrated(
            List<LegacyBorrower> legacyBorrowers,
            List<LegacyLoanProduct> legacyProducts,
            List<LegacyLoanAccount> legacyLoanAccounts,
            List<LegacyPayment> legacyPayments
    ) {
        if (borrowerRepository.count() != legacyBorrowers.size()
                || loanProductRepository.count() != legacyProducts.size()
                || loanAccountRepository.count() != legacyLoanAccounts.size()
                || paymentRepository.count() != legacyPayments.size()) {
            return false;
        }

        Map<String, Borrower> borrowers = borrowerRepository.findAllByOrderByIdAsc()
                .stream()
                .collect(Collectors.toMap(Borrower::getExternalId, Function.identity()));
        Map<String, LoanProduct> products = loanProductRepository.findAllByOrderByIdAsc()
                .stream()
                .collect(Collectors.toMap(LoanProduct::getCode, Function.identity()));
        Map<String, LoanAccount> loanAccounts = loanAccountRepository.findAllByOrderByIdAsc()
                .stream()
                .collect(Collectors.toMap(LoanAccount::getAccountNumber, Function.identity()));
        Map<String, Payment> payments = paymentRepository.findAllByOrderByIdAsc()
                .stream()
                .collect(Collectors.toMap(Payment::getExternalId, Function.identity()));

        return legacyBorrowers.stream()
                .allMatch(row -> sameBorrower(toBorrower(row), borrowers.get(row.getBorrowerId())))
                && legacyProducts.stream()
                .allMatch(row -> sameLoanProduct(
                        toLoanProduct(row),
                        products.get(row.getProductCode())
                ))
                && legacyLoanAccounts.stream()
                .allMatch(row -> sameLoanAccount(
                        toLoanAccount(row, borrowers, products),
                        loanAccounts.get(row.getLoanAccountNumber())
                ))
                && legacyPayments.stream()
                .allMatch(row -> samePayment(
                        toPayment(row, loanAccounts),
                        payments.get(row.getPaymentSequenceNumber())
                ));
    }

    private Borrower toBorrower(LegacyBorrower legacyBorrower) {
        String sourceId = legacyBorrower.getBorrowerId();
        Borrower borrower = new Borrower();
        borrower.setExternalId(required(BORROWER_TABLE, sourceId, "BORR_ID", sourceId));
        borrower.setFirstName(required(BORROWER_TABLE, sourceId, "BORR_FST_NM", legacyBorrower.getFirstName()));
        borrower.setLastName(required(BORROWER_TABLE, sourceId, "BORR_LST_NM", legacyBorrower.getLastName()));
        borrower.setMiddleInitial(optional(legacyBorrower.getMiddleInitial()));
        borrower.setSsnHash(optional(legacyBorrower.getSsnEncrypted()));
        borrower.setDateOfBirth(transformer.optionalDate(
                BORROWER_TABLE,
                sourceId,
                "BORR_DOB_DT",
                legacyBorrower.getDateOfBirth()
        ));
        borrower.setAddressLine1(optional(legacyBorrower.getAddressLine1()));
        borrower.setAddressLine2(optional(legacyBorrower.getAddressLine2()));
        borrower.setCity(optional(legacyBorrower.getCity()));
        borrower.setState(optional(legacyBorrower.getStateCode()));
        borrower.setZipCode(optional(legacyBorrower.getZipCode()));
        borrower.setPhone(optional(legacyBorrower.getPhoneNumber()));
        borrower.setEmail(optional(legacyBorrower.getEmail()));
        borrower.setCreditScore(transformer.optionalInteger(
                BORROWER_TABLE,
                sourceId,
                "BORR_CRDT_SCR",
                legacyBorrower.getCreditScore()
        ));
        borrower.setEmploymentStatus(optional(legacyBorrower.getEmploymentStatus()));
        borrower.setAnnualIncome(transformer.optionalDecimal(
                BORROWER_TABLE,
                sourceId,
                "BORR_ANN_INCM",
                legacyBorrower.getAnnualIncome()
        ));
        borrower.setStatus(transformer.expandBorrowerStatus(
                BORROWER_TABLE,
                sourceId,
                "BORR_STAT_CD",
                legacyBorrower.getStatusCode()
        ));
        borrower.setCreatedAt(transformer.optionalTimestamp(
                BORROWER_TABLE,
                sourceId,
                "BORR_CRET_DT",
                legacyBorrower.getCreatedDate()
        ));
        borrower.setUpdatedAt(transformer.optionalTimestamp(
                BORROWER_TABLE,
                sourceId,
                "BORR_UPDT_DT",
                legacyBorrower.getUpdatedDate()
        ));
        return borrower;
    }

    private LoanProduct toLoanProduct(LegacyLoanProduct legacyProduct) {
        String sourceId = legacyProduct.getProductCode();
        LoanProduct product = new LoanProduct();
        product.setCode(required(PRODUCT_TABLE, sourceId, "PROD_CD", sourceId));
        product.setName(required(PRODUCT_TABLE, sourceId, "PROD_DESC_TXT", legacyProduct.getDescription()));
        product.setType(required(PRODUCT_TABLE, sourceId, "PROD_TYP_CD", legacyProduct.getTypeCode()));
        product.setTermMonths(transformer.requiredInteger(
                PRODUCT_TABLE,
                sourceId,
                "PROD_TERM_MOS",
                legacyProduct.getTermMonths()
        ));
        product.setRateType(required(PRODUCT_TABLE, sourceId, "PROD_RT_TYP", legacyProduct.getRateType()));
        product.setMinAmount(transformer.optionalDecimal(
                PRODUCT_TABLE,
                sourceId,
                "PROD_MIN_AMT",
                legacyProduct.getMinAmount()
        ));
        product.setMaxAmount(transformer.optionalDecimal(
                PRODUCT_TABLE,
                sourceId,
                "PROD_MAX_AMT",
                legacyProduct.getMaxAmount()
        ));
        product.setActive(transformer.expandActiveFlag(
                PRODUCT_TABLE,
                sourceId,
                "PROD_STAT_CD",
                legacyProduct.getStatusCode()
        ));
        product.setEffectiveDate(transformer.optionalDate(
                PRODUCT_TABLE,
                sourceId,
                "PROD_EFF_DT",
                legacyProduct.getEffectiveDate()
        ));
        product.setExpirationDate(transformer.optionalDate(
                PRODUCT_TABLE,
                sourceId,
                "PROD_EXP_DT",
                legacyProduct.getExpirationDate()
        ));
        return product;
    }

    private LoanAccount toLoanAccount(
            LegacyLoanAccount legacyLoanAccount,
            Map<String, Borrower> borrowerByExternalId,
            Map<String, LoanProduct> productByCode
    ) {
        String sourceId = legacyLoanAccount.getLoanAccountNumber();
        Borrower borrower = borrowerByExternalId.get(legacyLoanAccount.getBorrowerId());
        if (borrower == null) {
            throw new LegacyDataMigrationException(
                    "Missing borrower foreign key in " + LOAN_TABLE + " sourceId="
                            + sourceId + " column=BORR_ID value="
                            + legacyLoanAccount.getBorrowerId()
            );
        }
        LoanProduct product = productByCode.get(legacyLoanAccount.getProductCode());
        if (product == null) {
            throw new LegacyDataMigrationException(
                    "Missing loan product foreign key in " + LOAN_TABLE + " sourceId="
                            + sourceId + " column=PROD_CD value="
                            + legacyLoanAccount.getProductCode()
            );
        }

        LoanAccount account = new LoanAccount();
        account.setAccountNumber(required(LOAN_TABLE, sourceId, "LN_ACCT_NBR", sourceId));
        account.setBorrower(borrower);
        account.setProduct(product);
        account.setOriginalAmount(transformer.requiredDecimal(
                LOAN_TABLE,
                sourceId,
                "LN_ORIG_AMT",
                legacyLoanAccount.getOriginalAmount()
        ));
        account.setCurrentBalance(transformer.requiredDecimal(
                LOAN_TABLE,
                sourceId,
                "LN_CURR_BAL",
                legacyLoanAccount.getCurrentBalance()
        ));
        account.setInterestRate(transformer.requiredDecimal(
                LOAN_TABLE,
                sourceId,
                "LN_INT_RT",
                legacyLoanAccount.getInterestRate()
        ));
        account.setTermMonths(transformer.requiredInteger(
                LOAN_TABLE,
                sourceId,
                "LN_TERM_MOS",
                legacyLoanAccount.getTermMonths()
        ));
        account.setMonthlyPayment(transformer.requiredDecimal(
                LOAN_TABLE,
                sourceId,
                "LN_PMT_AMT",
                legacyLoanAccount.getMonthlyPayment()
        ));
        account.setOriginationDate(transformer.requiredDate(
                LOAN_TABLE,
                sourceId,
                "LN_ORIG_DT",
                legacyLoanAccount.getOriginationDate()
        ));
        account.setMaturityDate(transformer.requiredDate(
                LOAN_TABLE,
                sourceId,
                "LN_MAT_DT",
                legacyLoanAccount.getMaturityDate()
        ));
        account.setFirstPaymentDate(transformer.optionalDate(
                LOAN_TABLE,
                sourceId,
                "LN_1ST_PMT_DT",
                legacyLoanAccount.getFirstPaymentDate()
        ));
        account.setNextPaymentDate(transformer.optionalDate(
                LOAN_TABLE,
                sourceId,
                "LN_NXT_PMT_DT",
                legacyLoanAccount.getNextPaymentDate()
        ));
        account.setStatus(transformer.expandLoanStatus(
                LOAN_TABLE,
                sourceId,
                "LN_STAT_CD",
                legacyLoanAccount.getStatusCode()
        ));
        account.setDelinquencyDays(transformer.optionalInteger(
                LOAN_TABLE,
                sourceId,
                "LN_DLQ_DAYS",
                legacyLoanAccount.getDelinquencyDays()
        ));
        account.setEscrowBalance(transformer.optionalDecimal(
                LOAN_TABLE,
                sourceId,
                "LN_ESCROW_BAL",
                legacyLoanAccount.getEscrowBalance()
        ));
        account.setLtvPercent(transformer.optionalDecimal(
                LOAN_TABLE,
                sourceId,
                "LN_LTV_PCT",
                legacyLoanAccount.getLtvPercent()
        ));
        account.setPropertyAddress(optional(legacyLoanAccount.getPropertyAddress()));
        account.setPropertyCity(optional(legacyLoanAccount.getPropertyCity()));
        account.setPropertyState(optional(legacyLoanAccount.getPropertyState()));
        account.setPropertyZip(optional(legacyLoanAccount.getPropertyZip()));
        account.setPropertyType(transformer.expandPropertyType(
                LOAN_TABLE,
                sourceId,
                "PROP_TYP_CD",
                legacyLoanAccount.getPropertyType()
        ));
        account.setAppraisedValue(transformer.optionalDecimal(
                LOAN_TABLE,
                sourceId,
                "PROP_APRS_VAL",
                legacyLoanAccount.getAppraisedValue()
        ));
        account.setCreatedAt(transformer.optionalTimestamp(
                LOAN_TABLE,
                sourceId,
                "LN_CRET_DT",
                legacyLoanAccount.getCreatedDate()
        ));
        account.setUpdatedAt(transformer.optionalTimestamp(
                LOAN_TABLE,
                sourceId,
                "LN_UPDT_DT",
                legacyLoanAccount.getUpdatedDate()
        ));
        return account;
    }

    private Payment toPayment(
            LegacyPayment legacyPayment,
            Map<String, LoanAccount> loanAccountByAccountNumber
    ) {
        String sourceId = legacyPayment.getPaymentSequenceNumber();
        LoanAccount account = loanAccountByAccountNumber.get(legacyPayment.getLoanAccountNumber());
        if (account == null) {
            throw new LegacyDataMigrationException(
                    "Missing loan account foreign key in " + PAYMENT_TABLE + " sourceId="
                            + sourceId + " column=LN_ACCT_NBR value="
                            + legacyPayment.getLoanAccountNumber()
            );
        }

        Payment payment = new Payment();
        payment.setExternalId(required(PAYMENT_TABLE, sourceId, "PMT_SEQ_NBR", sourceId));
        payment.setLoanAccount(account);
        payment.setPaymentDate(transformer.requiredDate(
                PAYMENT_TABLE,
                sourceId,
                "PMT_DT",
                legacyPayment.getPaymentDate()
        ));
        payment.setTotalAmount(transformer.requiredDecimal(
                PAYMENT_TABLE,
                sourceId,
                "PMT_AMT",
                legacyPayment.getTotalAmount()
        ));
        payment.setPrincipalAmount(transformer.optionalDecimal(
                PAYMENT_TABLE,
                sourceId,
                "PMT_PRIN_AMT",
                legacyPayment.getPrincipalAmount()
        ));
        payment.setInterestAmount(transformer.optionalDecimal(
                PAYMENT_TABLE,
                sourceId,
                "PMT_INT_AMT",
                legacyPayment.getInterestAmount()
        ));
        payment.setEscrowAmount(transformer.optionalDecimal(
                PAYMENT_TABLE,
                sourceId,
                "PMT_ESCROW_AMT",
                legacyPayment.getEscrowAmount()
        ));
        payment.setLateFee(transformer.optionalDecimal(
                PAYMENT_TABLE,
                sourceId,
                "PMT_LATE_FEE",
                legacyPayment.getLateFee()
        ));
        payment.setType(transformer.expandPaymentType(
                PAYMENT_TABLE,
                sourceId,
                "PMT_TYP_CD",
                legacyPayment.getTypeCode()
        ));
        payment.setStatus(transformer.expandPaymentStatus(
                PAYMENT_TABLE,
                sourceId,
                "PMT_STAT_CD",
                legacyPayment.getStatusCode()
        ));
        payment.setReceivedDate(transformer.optionalDate(
                PAYMENT_TABLE,
                sourceId,
                "PMT_RECV_DT",
                legacyPayment.getReceivedDate()
        ));
        payment.setProcessedDate(transformer.optionalDate(
                PAYMENT_TABLE,
                sourceId,
                "PMT_PROC_DT",
                legacyPayment.getProcessedDate()
        ));
        payment.setCreatedAt(transformer.optionalTimestamp(
                PAYMENT_TABLE,
                sourceId,
                "PMT_CRET_DT",
                legacyPayment.getCreatedDate()
        ));
        payment.setUpdatedAt(transformer.optionalTimestamp(
                PAYMENT_TABLE,
                sourceId,
                "PMT_UPDT_DT",
                legacyPayment.getUpdatedDate()
        ));
        return payment;
    }

    private String required(String table, String sourceId, String column, String value) {
        return transformer.requiredString(table, sourceId, column, value);
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean sameBorrower(Borrower expected, Borrower actual) {
        return actual != null
                && Objects.equals(expected.getExternalId(), actual.getExternalId())
                && Objects.equals(expected.getFirstName(), actual.getFirstName())
                && Objects.equals(expected.getLastName(), actual.getLastName())
                && Objects.equals(expected.getMiddleInitial(), actual.getMiddleInitial())
                && Objects.equals(expected.getSsnHash(), actual.getSsnHash())
                && Objects.equals(expected.getDateOfBirth(), actual.getDateOfBirth())
                && Objects.equals(expected.getAddressLine1(), actual.getAddressLine1())
                && Objects.equals(expected.getAddressLine2(), actual.getAddressLine2())
                && Objects.equals(expected.getCity(), actual.getCity())
                && Objects.equals(expected.getState(), actual.getState())
                && Objects.equals(expected.getZipCode(), actual.getZipCode())
                && Objects.equals(expected.getPhone(), actual.getPhone())
                && Objects.equals(expected.getEmail(), actual.getEmail())
                && Objects.equals(expected.getCreditScore(), actual.getCreditScore())
                && Objects.equals(expected.getEmploymentStatus(), actual.getEmploymentStatus())
                && sameDecimal(expected.getAnnualIncome(), actual.getAnnualIncome())
                && Objects.equals(expected.getStatus(), actual.getStatus())
                && Objects.equals(expected.getCreatedAt(), actual.getCreatedAt())
                && Objects.equals(expected.getUpdatedAt(), actual.getUpdatedAt());
    }

    private boolean sameLoanProduct(LoanProduct expected, LoanProduct actual) {
        return actual != null
                && Objects.equals(expected.getCode(), actual.getCode())
                && Objects.equals(expected.getName(), actual.getName())
                && Objects.equals(expected.getType(), actual.getType())
                && Objects.equals(expected.getTermMonths(), actual.getTermMonths())
                && Objects.equals(expected.getRateType(), actual.getRateType())
                && sameDecimal(expected.getMinAmount(), actual.getMinAmount())
                && sameDecimal(expected.getMaxAmount(), actual.getMaxAmount())
                && Objects.equals(expected.getActive(), actual.getActive())
                && Objects.equals(expected.getEffectiveDate(), actual.getEffectiveDate())
                && Objects.equals(expected.getExpirationDate(), actual.getExpirationDate());
    }

    private boolean sameLoanAccount(LoanAccount expected, LoanAccount actual) {
        return actual != null
                && Objects.equals(expected.getAccountNumber(), actual.getAccountNumber())
                && Objects.equals(
                        expected.getBorrower().getExternalId(),
                        actual.getBorrower().getExternalId()
                )
                && Objects.equals(expected.getProduct().getCode(), actual.getProduct().getCode())
                && sameDecimal(expected.getOriginalAmount(), actual.getOriginalAmount())
                && sameDecimal(expected.getCurrentBalance(), actual.getCurrentBalance())
                && sameDecimal(expected.getInterestRate(), actual.getInterestRate())
                && Objects.equals(expected.getTermMonths(), actual.getTermMonths())
                && sameDecimal(expected.getMonthlyPayment(), actual.getMonthlyPayment())
                && Objects.equals(expected.getOriginationDate(), actual.getOriginationDate())
                && Objects.equals(expected.getMaturityDate(), actual.getMaturityDate())
                && Objects.equals(expected.getFirstPaymentDate(), actual.getFirstPaymentDate())
                && Objects.equals(expected.getNextPaymentDate(), actual.getNextPaymentDate())
                && Objects.equals(expected.getStatus(), actual.getStatus())
                && Objects.equals(expected.getDelinquencyDays(), actual.getDelinquencyDays())
                && sameDecimal(expected.getEscrowBalance(), actual.getEscrowBalance())
                && sameDecimal(expected.getLtvPercent(), actual.getLtvPercent())
                && Objects.equals(expected.getPropertyAddress(), actual.getPropertyAddress())
                && Objects.equals(expected.getPropertyCity(), actual.getPropertyCity())
                && Objects.equals(expected.getPropertyState(), actual.getPropertyState())
                && Objects.equals(expected.getPropertyZip(), actual.getPropertyZip())
                && Objects.equals(expected.getPropertyType(), actual.getPropertyType())
                && sameDecimal(expected.getAppraisedValue(), actual.getAppraisedValue())
                && Objects.equals(expected.getCreatedAt(), actual.getCreatedAt())
                && Objects.equals(expected.getUpdatedAt(), actual.getUpdatedAt());
    }

    private boolean samePayment(Payment expected, Payment actual) {
        return actual != null
                && Objects.equals(expected.getExternalId(), actual.getExternalId())
                && Objects.equals(
                        expected.getLoanAccount().getAccountNumber(),
                        actual.getLoanAccount().getAccountNumber()
                )
                && Objects.equals(expected.getPaymentDate(), actual.getPaymentDate())
                && sameDecimal(expected.getTotalAmount(), actual.getTotalAmount())
                && sameDecimal(expected.getPrincipalAmount(), actual.getPrincipalAmount())
                && sameDecimal(expected.getInterestAmount(), actual.getInterestAmount())
                && sameDecimal(expected.getEscrowAmount(), actual.getEscrowAmount())
                && sameDecimal(expected.getLateFee(), actual.getLateFee())
                && Objects.equals(expected.getType(), actual.getType())
                && Objects.equals(expected.getStatus(), actual.getStatus())
                && Objects.equals(expected.getReceivedDate(), actual.getReceivedDate())
                && Objects.equals(expected.getProcessedDate(), actual.getProcessedDate())
                && Objects.equals(expected.getCreatedAt(), actual.getCreatedAt())
                && Objects.equals(expected.getUpdatedAt(), actual.getUpdatedAt());
    }

    private boolean sameDecimal(BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        return expected.compareTo(actual) == 0;
    }
}
