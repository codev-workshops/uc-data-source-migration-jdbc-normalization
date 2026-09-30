package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs per-table data-quality rules against the legacy CDW tables.
 * The legacy schema has no FK constraints, so referential integrity is checked here in code.
 */
@Component
public class LegacyDataValidator {

    public static final String BORROWER_TABLE = "CDW_BORR_MSTR";
    public static final String PRODUCT_TABLE = "CDW_LN_PROD";
    public static final String LOAN_TABLE = "CDW_LN_ACCT";
    public static final String PAYMENT_TABLE = "CDW_PMT_HIST";

    private static final Set<String> ACTIVE_INACTIVE = Set.of("ACT", "INA");
    private static final Set<String> LOAN_STATUSES = Set.of("ACT", "CLO", "DFT", "FRB");
    private static final Set<String> PROPERTY_TYPES = Set.of("SFR", "CND", "MFR", "TWN");
    private static final Set<String> PAYMENT_TYPES = Set.of("REG", "EXT", "PRT", "PRE");
    private static final Set<String> PAYMENT_STATUSES = Set.of("PST", "REV", "NSF", "PND");
    private static final int MAX_RATE_SCALE = 3;
    private static final int MAX_RATE_INTEGER_DIGITS = 2;

    private final LegacyBorrowerRepository borrowerRepository;
    private final LegacyLoanAccountRepository loanAccountRepository;
    private final LegacyLoanProductRepository loanProductRepository;
    private final LegacyPaymentRepository paymentRepository;

    public LegacyDataValidator(LegacyBorrowerRepository borrowerRepository,
                               LegacyLoanAccountRepository loanAccountRepository,
                               LegacyLoanProductRepository loanProductRepository,
                               LegacyPaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.loanProductRepository = loanProductRepository;
        this.paymentRepository = paymentRepository;
    }

    public ValidationResult validate() {
        List<LegacyBorrower> borrowers = borrowerRepository.findAll();
        List<LegacyLoanProduct> products = loanProductRepository.findAll();
        List<LegacyLoanAccount> loans = loanAccountRepository.findAll();
        List<LegacyPayment> payments = paymentRepository.findAll();

        Map<String, LegacyBorrower> borrowersById = borrowers.stream()
                .collect(Collectors.toMap(LegacyBorrower::getBorrowerId, Function.identity()));
        Set<String> productCodes = products.stream()
                .map(LegacyLoanProduct::getProductCode).collect(Collectors.toSet());
        Set<String> accountNumbers = loans.stream()
                .map(LegacyLoanAccount::getLoanAccountNumber).collect(Collectors.toSet());

        Map<String, List<String>> recordIds = new LinkedHashMap<>();
        List<ValidationFinding> findings = new ArrayList<>();
        run(borrowerRules(), borrowers, recordIds, findings);
        run(productRules(), products, recordIds, findings);
        run(loanRules(borrowersById, productCodes), loans, recordIds, findings);
        run(paymentRules(accountNumbers), payments, recordIds, findings);
        return new ValidationResult(recordIds, List.copyOf(findings));
    }

    private static <T> void run(RuleSet<T> rules, List<T> records,
                                Map<String, List<String>> recordIds,
                                List<ValidationFinding> findings) {
        List<T> sorted = records.stream().sorted(Comparator.comparing(rules::recordId)).toList();
        recordIds.put(rules.table(), sorted.stream().map(rules::recordId).toList());
        findings.addAll(rules.validate(sorted));
    }

    RuleSet<LegacyBorrower> borrowerRules() {
        return RuleSet.forTable(BORROWER_TABLE, LegacyBorrower::getBorrowerId)
                .required("BORR_ID", LegacyBorrower::getBorrowerId)
                .required("BORR_FST_NM", LegacyBorrower::getFirstName)
                .required("BORR_LST_NM", LegacyBorrower::getLastName)
                .date("BORR_DOB_DT", LegacyBorrower::getDateOfBirth)
                .date("BORR_CRET_DT", LegacyBorrower::getCreatedDate)
                .date("BORR_UPDT_DT", LegacyBorrower::getUpdatedDate)
                .integerInRange("BORR_CRDT_SCR", LegacyBorrower::getCreditScore, 300, 850)
                .amountAtLeast("BORR_ANN_INCM", LegacyBorrower::getAnnualIncome, BigDecimal.ZERO)
                .oneOf("BORR_STAT_CD", LegacyBorrower::getStatusCode, ACTIVE_INACTIVE)
                .check(Severity.ERROR, "BORR_ST_CD",
                        b -> b.getStateCode() == null || b.getStateCode().length() != 2,
                        b -> "state code '" + b.getStateCode() + "' is not 2 characters")
                .check(Severity.ERROR, "BORR_EMAIL_ADDR",
                        b -> b.getEmail() == null || !b.getEmail().contains("@"),
                        b -> "email '" + b.getEmail() + "' does not contain '@'")
                .warnIfNull("BORR_MID_INIT", LegacyBorrower::getMiddleInitial)
                .warnIfNull("BORR_ADDR_LN2", LegacyBorrower::getAddressLine2);
    }

    RuleSet<LegacyLoanProduct> productRules() {
        return RuleSet.forTable(PRODUCT_TABLE, LegacyLoanProduct::getProductCode)
                .required("PROD_CD", LegacyLoanProduct::getProductCode)
                .oneOf("PROD_STAT_CD", LegacyLoanProduct::getStatusCode, ACTIVE_INACTIVE)
                .date("PROD_EFF_DT", LegacyLoanProduct::getEffectiveDate)
                .date("PROD_EXP_DT", LegacyLoanProduct::getExpirationDate)
                .check(Severity.ERROR, "PROD_EXP_DT",
                        p -> bothPresent(LegacyValues.parseDate(p.getEffectiveDate()),
                                LegacyValues.parseDate(p.getExpirationDate()),
                                (eff, exp) -> !eff.isBefore(exp)),
                        p -> "effective date " + p.getEffectiveDate()
                                + " is not before expiration date " + p.getExpirationDate())
                .amount("PROD_MIN_AMT", LegacyLoanProduct::getMinAmount)
                .amount("PROD_MAX_AMT", LegacyLoanProduct::getMaxAmount)
                .check(Severity.ERROR, "PROD_MIN_AMT",
                        p -> bothPresent(LegacyValues.parseAmount(p.getMinAmount()),
                                LegacyValues.parseAmount(p.getMaxAmount()),
                                (min, max) -> min.compareTo(max) > 0),
                        p -> "min amount " + p.getMinAmount()
                                + " exceeds max amount " + p.getMaxAmount())
                .integerInRange("PROD_TERM_MOS", LegacyLoanProduct::getTermMonths, 1,
                        Integer.MAX_VALUE);
    }

    RuleSet<LegacyLoanAccount> loanRules(Map<String, LegacyBorrower> borrowersById,
                                         Set<String> productCodes) {
        return RuleSet.forTable(LOAN_TABLE, LegacyLoanAccount::getLoanAccountNumber)
                .required("LN_ACCT_NBR", LegacyLoanAccount::getLoanAccountNumber)
                .required("BORR_ID", LegacyLoanAccount::getBorrowerId)
                .required("PROD_CD", LegacyLoanAccount::getProductCode)
                .check(Severity.ERROR, "BORR_ID",
                        l -> l.getBorrowerId() != null
                                && !borrowersById.containsKey(l.getBorrowerId()),
                        l -> "borrower '" + l.getBorrowerId() + "' not found in " + BORROWER_TABLE)
                .check(Severity.ERROR, "PROD_CD",
                        l -> l.getProductCode() != null
                                && !productCodes.contains(l.getProductCode()),
                        l -> "product '" + l.getProductCode() + "' not found in " + PRODUCT_TABLE)
                .date("LN_ORIG_DT", LegacyLoanAccount::getOriginationDate)
                .date("LN_MAT_DT", LegacyLoanAccount::getMaturityDate)
                .date("LN_1ST_PMT_DT", LegacyLoanAccount::getFirstPaymentDate)
                .date("LN_NXT_PMT_DT", LegacyLoanAccount::getNextPaymentDate)
                .date("LN_CRET_DT", LegacyLoanAccount::getCreatedDate)
                .date("LN_UPDT_DT", LegacyLoanAccount::getUpdatedDate)
                .amount("LN_ORIG_AMT", LegacyLoanAccount::getOriginalAmount)
                .amount("LN_CURR_BAL", LegacyLoanAccount::getCurrentBalance)
                .amount("LN_PMT_AMT", LegacyLoanAccount::getMonthlyPayment)
                .amount("LN_ESCROW_BAL", LegacyLoanAccount::getEscrowBalance)
                .amount("PROP_APRS_VAL", LegacyLoanAccount::getAppraisedValue)
                .decimalInRange("LN_INT_RT", LegacyLoanAccount::getInterestRate,
                        BigDecimal.ZERO, BigDecimal.valueOf(30))
                .check(Severity.ERROR, "LN_INT_RT",
                        l -> LegacyValues.parseDecimal(l.getInterestRate())
                                .filter(rate -> !fitsDecimal53(rate)).isPresent(),
                        l -> "'" + l.getInterestRate() + "' does not fit DECIMAL(5,3)")
                .integerInRange("LN_TERM_MOS", LegacyLoanAccount::getTermMonths, 1,
                        Integer.MAX_VALUE)
                .integerInRange("LN_DLQ_DAYS", LegacyLoanAccount::getDelinquencyDays, 0,
                        Integer.MAX_VALUE)
                .oneOf("LN_STAT_CD", LegacyLoanAccount::getStatusCode, LOAN_STATUSES)
                .oneOf("PROP_TYP_CD", LegacyLoanAccount::getPropertyType, PROPERTY_TYPES)
                .decimalInRange("LN_LTV_PCT", LegacyLoanAccount::getLtvPercent,
                        BigDecimal.ZERO, BigDecimal.valueOf(150))
                .check(Severity.WARNING, "LN_STAT_CD",
                        l -> "ACT".equals(l.getStatusCode())
                                && LegacyValues.parseInteger(l.getDelinquencyDays())
                                .filter(days -> days > 0).isPresent(),
                        l -> "status ACT conflicts with LN_DLQ_DAYS=" + l.getDelinquencyDays())
                .check(Severity.WARNING, "BORR_FST_NM",
                        l -> namesDisagree(borrowersById.get(l.getBorrowerId()),
                                LegacyBorrower::getFirstName, l.getBorrowerFirstName()),
                        l -> "denormalized first name '" + l.getBorrowerFirstName()
                                + "' disagrees with " + BORROWER_TABLE)
                .check(Severity.WARNING, "BORR_LST_NM",
                        l -> namesDisagree(borrowersById.get(l.getBorrowerId()),
                                LegacyBorrower::getLastName, l.getBorrowerLastName()),
                        l -> "denormalized last name '" + l.getBorrowerLastName()
                                + "' disagrees with " + BORROWER_TABLE);
    }

    RuleSet<LegacyPayment> paymentRules(Set<String> accountNumbers) {
        return RuleSet.forTable(PAYMENT_TABLE, LegacyPayment::getPaymentSequenceNumber)
                .required("PMT_SEQ_NBR", LegacyPayment::getPaymentSequenceNumber)
                .required("LN_ACCT_NBR", LegacyPayment::getLoanAccountNumber)
                .check(Severity.ERROR, "LN_ACCT_NBR",
                        p -> p.getLoanAccountNumber() != null
                                && !accountNumbers.contains(p.getLoanAccountNumber()),
                        p -> "loan '" + p.getLoanAccountNumber() + "' not found in " + LOAN_TABLE)
                .date("PMT_DT", LegacyPayment::getPaymentDate)
                .date("PMT_RECV_DT", LegacyPayment::getReceivedDate)
                .date("PMT_PROC_DT", LegacyPayment::getProcessedDate)
                .date("PMT_CRET_DT", LegacyPayment::getCreatedDate)
                .date("PMT_UPDT_DT", LegacyPayment::getUpdatedDate)
                .amount("PMT_AMT", LegacyPayment::getTotalAmount)
                .amount("PMT_PRIN_AMT", LegacyPayment::getPrincipalAmount)
                .amount("PMT_INT_AMT", LegacyPayment::getInterestAmount)
                .amount("PMT_ESCROW_AMT", LegacyPayment::getEscrowAmount)
                .amount("PMT_LATE_FEE", LegacyPayment::getLateFee)
                .oneOf("PMT_TYP_CD", LegacyPayment::getTypeCode, PAYMENT_TYPES)
                .oneOf("PMT_STAT_CD", LegacyPayment::getStatusCode, PAYMENT_STATUSES)
                .rule(LegacyDataValidator::reconcileComponents)
                .check(Severity.WARNING, "PMT_PROC_DT",
                        p -> bothPresent(LegacyValues.parseDate(p.getReceivedDate()),
                                LegacyValues.parseDate(p.getProcessedDate()),
                                LocalDate::isAfter),
                        p -> "processed " + p.getProcessedDate()
                                + " before it was received " + p.getReceivedDate())
                .check(Severity.WARNING, "PMT_LATE_FEE",
                        p -> bothPresent(LegacyValues.parseDate(p.getPaymentDate()),
                                LegacyValues.parseDate(p.getReceivedDate()),
                                LocalDate::isBefore)
                                && LegacyValues.parseAmount(p.getLateFee())
                                .filter(fee -> fee.signum() == 0).isPresent(),
                        p -> "received " + p.getReceivedDate() + " after payment date "
                                + p.getPaymentDate() + " but late fee is " + p.getLateFee());
    }

    private static Stream<ValidationFinding> reconcileComponents(LegacyPayment payment) {
        List<Optional<BigDecimal>> components = Stream.of(payment.getPrincipalAmount(),
                        payment.getInterestAmount(), payment.getEscrowAmount(), payment.getLateFee())
                .map(LegacyValues::parseAmount)
                .toList();
        Optional<BigDecimal> total = LegacyValues.parseAmount(payment.getTotalAmount());
        if (total.isEmpty() || components.stream().anyMatch(Optional::isEmpty)) {
            return Stream.empty();
        }
        BigDecimal sum = components.stream().map(Optional::get)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total.get()) == 0) {
            return Stream.empty();
        }
        return Stream.of(new ValidationFinding(Severity.WARNING, PAYMENT_TABLE,
                payment.getPaymentSequenceNumber(), "PMT_AMT",
                "principal+interest+escrow+late fee = " + sum.toPlainString()
                        + " does not equal PMT_AMT " + payment.getTotalAmount()));
    }

    private static boolean fitsDecimal53(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        int integerDigits = stripped.precision() - stripped.scale();
        return stripped.scale() <= MAX_RATE_SCALE && integerDigits <= MAX_RATE_INTEGER_DIGITS;
    }

    private static boolean namesDisagree(LegacyBorrower borrower,
                                         Function<LegacyBorrower, String> nameGetter,
                                         String denormalizedName) {
        return borrower != null && !Objects.equals(nameGetter.apply(borrower), denormalizedName);
    }

    private static <A> boolean bothPresent(Optional<A> first, Optional<A> second,
                                           BiPredicate<A, A> test) {
        return first.isPresent() && second.isPresent() && test.test(first.get(), second.get());
    }
}
