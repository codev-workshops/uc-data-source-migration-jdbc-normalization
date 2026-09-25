package com.workshop.loanservice.service.validation;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Verifies that every legacy foreign-key-like column resolves to exactly one parent record and that
 * denormalized borrower columns on {@code CDW_LN_ACCT} agree with {@code CDW_BORR_MSTR} before they
 * are dropped in the modern schema.
 */
@Component
public class ReferenceResolver {

  /** Rule id for a child key that has no parent record. */
  public static final String RULE_ORPHAN = "REF_ORPHAN";

  /** Rule id for a child key that resolves to more than one parent record. */
  public static final String RULE_AMBIGUOUS = "REF_AMBIGUOUS";

  /** Rule id for a null child key. */
  public static final String RULE_MISSING = "REF_MISSING";

  /** Rule id for denormalized borrower columns that disagree with the master record. */
  public static final String RULE_DENORM_MISMATCH = "REF_DENORM_MISMATCH";

  /** Rule id for denormalized columns that cannot be verified against the master record. */
  public static final String RULE_DENORM_UNVERIFIABLE = "REF_DENORM_UNVERIFIABLE";

  /** Runs all reference checks and returns the resulting violations. */
  public List<ValidationViolation> resolve(
      Collection<LegacyBorrower> borrowers,
      Collection<LegacyLoanProduct> products,
      Collection<LegacyLoanAccount> loans,
      Collection<LegacyPayment> payments) {
    Map<String, List<LegacyBorrower>> borrowersById =
        index(borrowers, LegacyBorrower::getBorrowerId);
    Map<String, List<LegacyLoanProduct>> productsByCode =
        index(products, LegacyLoanProduct::getProductCode);
    Map<String, List<LegacyLoanAccount>> loansByNumber =
        index(loans, LegacyLoanAccount::getLoanAccountNumber);

    List<ValidationViolation> violations = new ArrayList<>();
    for (LegacyLoanAccount loan : loans) {
      String key = loan.getLoanAccountNumber();
      List<LegacyBorrower> matchedBorrowers =
          checkReference(
              violations,
              DataQualityValidator.TABLE_LOAN,
              key,
              "BORR_ID",
              loan.getBorrowerId(),
              borrowersById,
              "CDW_BORR_MSTR");
      checkReference(
          violations,
          DataQualityValidator.TABLE_LOAN,
          key,
          "PROD_CD",
          loan.getProductCode(),
          productsByCode,
          "CDW_LN_PROD");
      if (matchedBorrowers.size() == 1) {
        checkDenormalizedBorrower(violations, loan, matchedBorrowers.get(0));
      }
    }
    for (LegacyPayment payment : payments) {
      checkReference(
          violations,
          DataQualityValidator.TABLE_PAYMENT,
          payment.getPaymentSequenceNumber(),
          "LN_ACCT_NBR",
          payment.getLoanAccountNumber(),
          loansByNumber,
          "CDW_LN_ACCT");
    }
    return violations;
  }

  private static <E> Map<String, List<E>> index(Collection<E> rows, Function<E, String> keyFn) {
    return rows.stream()
        .filter(row -> keyFn.apply(row) != null)
        .collect(Collectors.groupingBy(keyFn));
  }

  private static <P> List<P> checkReference(
      List<ValidationViolation> violations,
      String entityType,
      String recordKey,
      String field,
      String rawKey,
      Map<String, List<P>> parents,
      String parentTable) {
    if (rawKey == null || rawKey.isBlank()) {
      violations.add(
          ValidationViolation.error(field, rawKey, RULE_MISSING, "Reference key is null or blank")
              .bind(entityType, recordKey));
      return List.of();
    }
    List<P> matches = parents.getOrDefault(rawKey, List.of());
    if (matches.isEmpty()) {
      violations.add(
          ValidationViolation.error(
                  field,
                  rawKey,
                  RULE_ORPHAN,
                  "No " + parentTable + " record for '" + rawKey + "' (orphan)")
              .bind(entityType, recordKey));
    } else if (matches.size() > 1) {
      violations.add(
          ValidationViolation.error(
                  field,
                  rawKey,
                  RULE_AMBIGUOUS,
                  matches.size() + " " + parentTable + " records for '" + rawKey + "'")
              .bind(entityType, recordKey));
    }
    return matches;
  }

  private static void checkDenormalizedBorrower(
      List<ValidationViolation> violations, LegacyLoanAccount loan, LegacyBorrower master) {
    String key = loan.getLoanAccountNumber();
    if (!Objects.equals(loan.getBorrowerFirstName(), master.getFirstName())) {
      violations.add(
          ValidationViolation.error(
                  "BORR_FST_NM",
                  loan.getBorrowerFirstName(),
                  RULE_DENORM_MISMATCH,
                  "Loan first name differs from CDW_BORR_MSTR value '"
                      + master.getFirstName()
                      + "'")
              .bind(DataQualityValidator.TABLE_LOAN, key));
    }
    if (!Objects.equals(loan.getBorrowerLastName(), master.getLastName())) {
      violations.add(
          ValidationViolation.error(
                  "BORR_LST_NM",
                  loan.getBorrowerLastName(),
                  RULE_DENORM_MISMATCH,
                  "Loan last name differs from CDW_BORR_MSTR value '" + master.getLastName() + "'")
              .bind(DataQualityValidator.TABLE_LOAN, key));
    }
    if (loan.getBorrowerSsnLast4() != null) {
      violations.add(
          ValidationViolation.warn(
                  "BORR_SSN_LST4",
                  loan.getBorrowerSsnLast4(),
                  RULE_DENORM_UNVERIFIABLE,
                  "CDW_BORR_MSTR only stores BORR_SSN_ENCR; last-4 cannot be verified before drop")
              .bind(DataQualityValidator.TABLE_LOAN, key));
    }
  }
}
