package com.workshop.loanservice.service.validation;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Runs every field-level, cross-field and referential rule against the legacy repositories and
 * returns the bound violations.
 */
@Service
public class DataQualityValidator {

  /** Entity type for {@code CDW_BORR_MSTR}. */
  public static final String TABLE_BORROWER = "CDW_BORR_MSTR";

  /** Entity type for {@code CDW_LN_PROD}. */
  public static final String TABLE_PRODUCT = "CDW_LN_PROD";

  /** Entity type for {@code CDW_LN_ACCT}. */
  public static final String TABLE_LOAN = "CDW_LN_ACCT";

  /** Entity type for {@code CDW_PMT_HIST}. */
  public static final String TABLE_PAYMENT = "CDW_PMT_HIST";

  /** Payment components do not sum to the stated total. */
  public static final String RULE_PMT_SUM_MISMATCH = "PMT_SUM_MISMATCH";

  /** Loan is ACT but has delinquency days. */
  public static final String RULE_ACTIVE_DELINQUENT = "LN_ACTIVE_DELINQUENT";

  /** Loan carries an escrow balance but its payments never contribute escrow. */
  public static final String RULE_ESCROW_NO_CONTRIBUTION = "LN_ESCROW_NO_CONTRIBUTION";

  /** {@code BORR_SSN_ENCR} is a development placeholder, not a real hash. */
  public static final String RULE_SSN_PLACEHOLDER = "BORR_SSN_PLACEHOLDER";

  /** Modern {@code payments} table has no column for {@code PMT_SEQ_NBR}. */
  public static final String RULE_PMT_SEQ_LOSS = "PMT_SEQ_NBR_LOSS";

  private static final Pattern SSN_PLACEHOLDER = Pattern.compile("^ENC_XXX_.*$");

  private final LegacyBorrowerRepository borrowerRepository;
  private final LegacyLoanProductRepository productRepository;
  private final LegacyLoanAccountRepository loanRepository;
  private final LegacyPaymentRepository paymentRepository;
  private final ReferenceResolver referenceResolver;

  private final AmountTransformer amounts = new AmountTransformer();
  private final DecimalTransformer decimals = new DecimalTransformer();
  private final DateTransformer dates = new DateTransformer();
  private final IntegerTransformer creditScores = IntegerTransformer.creditScore();
  private final IntegerTransformer terms = IntegerTransformer.termMonths();
  private final IntegerTransformer delinquency = IntegerTransformer.delinquencyDays();

  public DataQualityValidator(
      LegacyBorrowerRepository borrowerRepository,
      LegacyLoanProductRepository productRepository,
      LegacyLoanAccountRepository loanRepository,
      LegacyPaymentRepository paymentRepository,
      ReferenceResolver referenceResolver) {
    this.borrowerRepository = borrowerRepository;
    this.productRepository = productRepository;
    this.loanRepository = loanRepository;
    this.paymentRepository = paymentRepository;
    this.referenceResolver = referenceResolver;
  }

  /** Loads all legacy tables and validates them. */
  public DataQualityResult validateAll() {
    return validate(
        borrowerRepository.findAll(),
        productRepository.findAll(),
        loanRepository.findAll(),
        paymentRepository.findAll());
  }

  /** Validates the supplied in-memory records; useful for seeding dirty data in tests. */
  public DataQualityResult validate(
      List<LegacyBorrower> borrowers,
      List<LegacyLoanProduct> products,
      List<LegacyLoanAccount> loans,
      List<LegacyPayment> payments) {
    List<ValidationViolation> violations = new ArrayList<>();
    borrowers.forEach(b -> validateBorrower(b, violations));
    products.forEach(p -> validateProduct(p, violations));
    loans.forEach(l -> validateLoan(l, violations));
    payments.forEach(p -> validatePayment(p, violations));
    checkEscrowContributions(loans, payments, violations);
    violations.addAll(referenceResolver.resolve(borrowers, products, loans, payments));

    Map<String, List<String>> keys = new LinkedHashMap<>();
    keys.put(TABLE_BORROWER, keysOf(borrowers, LegacyBorrower::getBorrowerId));
    keys.put(TABLE_PRODUCT, keysOf(products, LegacyLoanProduct::getProductCode));
    keys.put(TABLE_LOAN, keysOf(loans, LegacyLoanAccount::getLoanAccountNumber));
    keys.put(TABLE_PAYMENT, keysOf(payments, LegacyPayment::getPaymentSequenceNumber));
    return new DataQualityResult(keys, violations);
  }

  private void validateBorrower(LegacyBorrower b, List<ValidationViolation> out) {
    String key = b.getBorrowerId();
    collect(out, TABLE_BORROWER, key, creditScores.transform(b.getCreditScore(), "BORR_CRDT_SCR"));
    collect(out, TABLE_BORROWER, key, amounts.transform(b.getAnnualIncome(), "BORR_ANN_INCM"));
    collect(out, TABLE_BORROWER, key, dates.transform(b.getDateOfBirth(), "BORR_DOB_DT"));
    collect(out, TABLE_BORROWER, key, dates.transform(b.getCreatedDate(), "BORR_CRET_DT"));
    collect(out, TABLE_BORROWER, key, dates.transform(b.getUpdatedDate(), "BORR_UPDT_DT"));
    collect(
        out,
        TABLE_BORROWER,
        key,
        new CodeExpansionTransformer(LegacyCodeSet.BORR_STAT_CD)
            .transform(b.getStatusCode(), "BORR_STAT_CD"));
    requireText(out, TABLE_BORROWER, key, "BORR_FST_NM", b.getFirstName());
    requireText(out, TABLE_BORROWER, key, "BORR_LST_NM", b.getLastName());
    if (b.getSsnEncrypted() != null && SSN_PLACEHOLDER.matcher(b.getSsnEncrypted()).matches()) {
      out.add(
          ValidationViolation.warn(
                  "BORR_SSN_ENCR",
                  b.getSsnEncrypted(),
                  RULE_SSN_PLACEHOLDER,
                  "Value matches ENC_XXX_* placeholder pattern; not a real hash, re-encrypt")
              .bind(TABLE_BORROWER, key));
    }
  }

  private void validateProduct(LegacyLoanProduct p, List<ValidationViolation> out) {
    String key = p.getProductCode();
    collect(out, TABLE_PRODUCT, key, terms.transform(p.getTermMonths(), "PROD_TERM_MOS"));
    collect(out, TABLE_PRODUCT, key, amounts.transform(p.getMinAmount(), "PROD_MIN_AMT"));
    collect(out, TABLE_PRODUCT, key, amounts.transform(p.getMaxAmount(), "PROD_MAX_AMT"));
    collect(out, TABLE_PRODUCT, key, dates.transform(p.getEffectiveDate(), "PROD_EFF_DT"));
    collect(out, TABLE_PRODUCT, key, dates.transform(p.getExpirationDate(), "PROD_EXP_DT"));
    collect(
        out,
        TABLE_PRODUCT,
        key,
        new CodeExpansionTransformer(LegacyCodeSet.PROD_STAT_CD)
            .transform(p.getStatusCode(), "PROD_STAT_CD"));
    requireText(out, TABLE_PRODUCT, key, "PROD_DESC_TXT", p.getDescription());
  }

  private void validateLoan(LegacyLoanAccount l, List<ValidationViolation> out) {
    String key = l.getLoanAccountNumber();
    collect(out, TABLE_LOAN, key, amounts.transform(l.getOriginalAmount(), "LN_ORIG_AMT"));
    collect(out, TABLE_LOAN, key, amounts.transform(l.getCurrentBalance(), "LN_CURR_BAL"));
    collect(out, TABLE_LOAN, key, amounts.transform(l.getMonthlyPayment(), "LN_PMT_AMT"));
    collect(out, TABLE_LOAN, key, amounts.transform(l.getEscrowBalance(), "LN_ESCROW_BAL"));
    collect(out, TABLE_LOAN, key, amounts.transform(l.getAppraisedValue(), "PROP_APRS_VAL"));
    collect(out, TABLE_LOAN, key, decimals.transform(l.getInterestRate(), "LN_INT_RT"));
    collect(out, TABLE_LOAN, key, decimals.transform(l.getLtvPercent(), "LN_LTV_PCT"));
    collect(out, TABLE_LOAN, key, terms.transform(l.getTermMonths(), "LN_TERM_MOS"));
    collect(out, TABLE_LOAN, key, dates.transform(l.getOriginationDate(), "LN_ORIG_DT"));
    collect(out, TABLE_LOAN, key, dates.transform(l.getMaturityDate(), "LN_MAT_DT"));
    collect(out, TABLE_LOAN, key, dates.transform(l.getFirstPaymentDate(), "LN_1ST_PMT_DT"));
    collect(out, TABLE_LOAN, key, dates.transform(l.getNextPaymentDate(), "LN_NXT_PMT_DT"));
    collect(out, TABLE_LOAN, key, dates.transform(l.getCreatedDate(), "LN_CRET_DT"));
    collect(out, TABLE_LOAN, key, dates.transform(l.getUpdatedDate(), "LN_UPDT_DT"));
    TransformResult<String> status =
        new CodeExpansionTransformer(LegacyCodeSet.LN_STAT_CD)
            .transform(l.getStatusCode(), "LN_STAT_CD");
    collect(out, TABLE_LOAN, key, status);
    collect(
        out,
        TABLE_LOAN,
        key,
        new CodeExpansionTransformer(LegacyCodeSet.PROP_TYP_CD)
            .transform(l.getPropertyType(), "PROP_TYP_CD"));
    TransformResult<Integer> dlq = delinquency.transform(l.getDelinquencyDays(), "LN_DLQ_DAYS");
    collect(out, TABLE_LOAN, key, dlq);
    if ("Active".equals(status.value()) && dlq.value() != null && dlq.value() > 0) {
      out.add(
          ValidationViolation.error(
                  "LN_DLQ_DAYS",
                  l.getDelinquencyDays(),
                  RULE_ACTIVE_DELINQUENT,
                  "Loan status is ACT but delinquency_days=" + dlq.value())
              .bind(TABLE_LOAN, key));
    }
  }

  private void validatePayment(LegacyPayment p, List<ValidationViolation> out) {
    String key = p.getPaymentSequenceNumber();
    TransformResult<BigDecimal> total = amounts.transform(p.getTotalAmount(), "PMT_AMT");
    TransformResult<BigDecimal> principal =
        amounts.transform(p.getPrincipalAmount(), "PMT_PRIN_AMT");
    TransformResult<BigDecimal> interest = amounts.transform(p.getInterestAmount(), "PMT_INT_AMT");
    TransformResult<BigDecimal> escrow = amounts.transform(p.getEscrowAmount(), "PMT_ESCROW_AMT");
    TransformResult<BigDecimal> lateFee = amounts.transform(p.getLateFee(), "PMT_LATE_FEE");
    collect(out, TABLE_PAYMENT, key, total);
    collect(out, TABLE_PAYMENT, key, principal);
    collect(out, TABLE_PAYMENT, key, interest);
    collect(out, TABLE_PAYMENT, key, escrow);
    collect(out, TABLE_PAYMENT, key, lateFee);
    collect(out, TABLE_PAYMENT, key, dates.transform(p.getPaymentDate(), "PMT_DT"));
    collect(out, TABLE_PAYMENT, key, dates.transform(p.getReceivedDate(), "PMT_RECV_DT"));
    collect(out, TABLE_PAYMENT, key, dates.transform(p.getProcessedDate(), "PMT_PROC_DT"));
    collect(out, TABLE_PAYMENT, key, dates.transform(p.getCreatedDate(), "PMT_CRET_DT"));
    collect(out, TABLE_PAYMENT, key, dates.transform(p.getUpdatedDate(), "PMT_UPDT_DT"));
    collect(
        out,
        TABLE_PAYMENT,
        key,
        new CodeExpansionTransformer(LegacyCodeSet.PMT_TYP_CD)
            .transform(p.getTypeCode(), "PMT_TYP_CD"));
    collect(
        out,
        TABLE_PAYMENT,
        key,
        new CodeExpansionTransformer(LegacyCodeSet.PMT_STAT_CD)
            .transform(p.getStatusCode(), "PMT_STAT_CD"));

    if (total.value() != null
        && principal.value() != null
        && interest.value() != null
        && escrow.value() != null
        && lateFee.value() != null) {
      BigDecimal sum =
          principal.value().add(interest.value()).add(escrow.value()).add(lateFee.value());
      if (sum.compareTo(total.value()) != 0) {
        out.add(
            ValidationViolation.error(
                    "PMT_AMT",
                    p.getTotalAmount(),
                    RULE_PMT_SUM_MISMATCH,
                    "principal+interest+escrow+lateFee=" + sum + " but PMT_AMT=" + total.value())
                .bind(TABLE_PAYMENT, key));
      }
    }
    out.add(
        ValidationViolation.warn(
                "PMT_SEQ_NBR",
                key,
                RULE_PMT_SEQ_LOSS,
                "Modern payments table has no legacy-ID column; PMT_SEQ_NBR will be lost")
            .bind(TABLE_PAYMENT, key));
  }

  private void checkEscrowContributions(
      List<LegacyLoanAccount> loans, List<LegacyPayment> payments, List<ValidationViolation> out) {
    Map<String, List<LegacyPayment>> byLoan =
        payments.stream()
            .filter(p -> p.getLoanAccountNumber() != null)
            .collect(Collectors.groupingBy(LegacyPayment::getLoanAccountNumber));
    for (LegacyLoanAccount loan : loans) {
      BigDecimal balance = amounts.transform(loan.getEscrowBalance(), "LN_ESCROW_BAL").value();
      if (balance == null || balance.signum() <= 0) {
        continue;
      }
      List<LegacyPayment> loanPayments =
          byLoan.getOrDefault(loan.getLoanAccountNumber(), List.of());
      if (loanPayments.isEmpty()) {
        continue;
      }
      boolean anyEscrow =
          loanPayments.stream()
              .map(p -> amounts.transform(p.getEscrowAmount(), "PMT_ESCROW_AMT").value())
              .anyMatch(v -> v != null && v.signum() > 0);
      if (!anyEscrow) {
        out.add(
            ValidationViolation.warn(
                    "LN_ESCROW_BAL",
                    loan.getEscrowBalance(),
                    RULE_ESCROW_NO_CONTRIBUTION,
                    "escrow_balance>0 but every payment has PMT_ESCROW_AMT=0")
                .bind(TABLE_LOAN, loan.getLoanAccountNumber()));
      }
    }
  }

  private static void requireText(
      List<ValidationViolation> out, String table, String key, String field, String value) {
    if (value == null || value.isBlank()) {
      out.add(
          ValidationViolation.error(field, value, "TEXT_MISSING", "Required text is null or blank")
              .bind(table, key));
    }
  }

  private static void collect(
      List<ValidationViolation> out, String table, String key, TransformResult<?> result) {
    result.violations().forEach(v -> out.add(v.bind(table, key)));
  }

  private static <E> List<String> keysOf(List<E> rows, Function<E, String> keyFn) {
    return rows.stream().map(keyFn).collect(Collectors.toList());
  }
}
