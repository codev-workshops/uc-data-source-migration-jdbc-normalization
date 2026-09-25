package com.workshop.loanservice.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceResolverTest {

  private final ReferenceResolver resolver = new ReferenceResolver();

  @Test
  void cleanReferencesProduceNoViolations() {
    List<ValidationViolation> vs =
        resolver.resolve(
            List.of(borrower("B-1", "Ann", "Lee")),
            List.of(product("P-1")),
            List.of(loan("LN-1", "B-1", "P-1", "Ann", "Lee")),
            List.of(payment("PMT-1", "LN-1")));
    assertThat(vs)
        .extracting(ValidationViolation::ruleId)
        .containsOnly(ReferenceResolver.RULE_DENORM_UNVERIFIABLE);
  }

  @Test
  void orphanBorrowerProductAndLoanAreDetected() {
    List<ValidationViolation> vs =
        resolver.resolve(
            List.of(),
            List.of(),
            List.of(loan("LN-1", "B-404", "P-404", "Ann", "Lee")),
            List.of(payment("PMT-1", "LN-404")));
    assertThat(vs)
        .filteredOn(v -> v.ruleId().equals(ReferenceResolver.RULE_ORPHAN))
        .extracting(ValidationViolation::field)
        .containsExactlyInAnyOrder("BORR_ID", "PROD_CD", "LN_ACCT_NBR");
  }

  @Test
  void duplicateParentKeyIsAmbiguous() {
    List<ValidationViolation> vs =
        resolver.resolve(
            List.of(borrower("B-1", "Ann", "Lee"), borrower("B-1", "Ann", "Lee")),
            List.of(product("P-1")),
            List.of(loan("LN-1", "B-1", "P-1", "Ann", "Lee")),
            List.of());
    assertThat(vs)
        .extracting(ValidationViolation::ruleId)
        .contains(ReferenceResolver.RULE_AMBIGUOUS);
  }

  @Test
  void missingReferenceKeyIsFlagged() {
    List<ValidationViolation> vs =
        resolver.resolve(
            List.of(), List.of(), List.of(loan("LN-1", null, " ", "Ann", "Lee")), List.of());
    assertThat(vs).filteredOn(v -> v.ruleId().equals(ReferenceResolver.RULE_MISSING)).hasSize(2);
  }

  @Test
  void denormalizedNameMismatchIsFlagged() {
    List<ValidationViolation> vs =
        resolver.resolve(
            List.of(borrower("B-1", "Ann", "Lee")),
            List.of(product("P-1")),
            List.of(loan("LN-1", "B-1", "P-1", "Anne", "Li")),
            List.of());
    assertThat(vs)
        .filteredOn(v -> v.ruleId().equals(ReferenceResolver.RULE_DENORM_MISMATCH))
        .extracting(ValidationViolation::field)
        .containsExactlyInAnyOrder("BORR_FST_NM", "BORR_LST_NM");
  }

  private static LegacyBorrower borrower(String id, String first, String last) {
    LegacyBorrower b = new LegacyBorrower();
    b.setBorrowerId(id);
    b.setFirstName(first);
    b.setLastName(last);
    b.setSsnEncrypted("ENC_XXX_001");
    return b;
  }

  private static LegacyLoanProduct product(String code) {
    LegacyLoanProduct p = new LegacyLoanProduct();
    p.setProductCode(code);
    return p;
  }

  private static LegacyLoanAccount loan(
      String number, String borrowerId, String productCode, String first, String last) {
    LegacyLoanAccount l = new LegacyLoanAccount();
    l.setLoanAccountNumber(number);
    l.setBorrowerId(borrowerId);
    l.setProductCode(productCode);
    l.setBorrowerFirstName(first);
    l.setBorrowerLastName(last);
    l.setBorrowerSsnLast4("1234");
    return l;
  }

  private static LegacyPayment payment(String seq, String loan) {
    LegacyPayment p = new LegacyPayment();
    p.setPaymentSequenceNumber(seq);
    p.setLoanAccountNumber(loan);
    return p;
  }
}
