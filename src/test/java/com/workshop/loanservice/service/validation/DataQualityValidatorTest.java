package com.workshop.loanservice.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyPayment;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cross-field rules, exercised without a Spring context. */
class DataQualityValidatorTest {

  private final DataQualityValidator validator =
      new DataQualityValidator(null, null, null, null, new ReferenceResolver());

  @Test
  void paymentComponentsMustSumToTotal() {
    LegacyPayment p = payment("PMT-2025120001", "1,487.02", "456.78", "1,074.69", "355.55", "0.00");
    DataQualityResult r = validator.validate(List.of(), List.of(), List.of(), List.of(p));
    assertThat(r.violations())
        .filteredOn(v -> v.ruleId().equals(DataQualityValidator.RULE_PMT_SUM_MISMATCH))
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.recordKey()).isEqualTo("PMT-2025120001");
              assertThat(v.message()).contains("1887.02").contains("1487.02");
              assertThat(v.severity()).isEqualTo(ValidationViolation.Severity.ERROR);
            });
  }

  @Test
  void consistentPaymentHasNoSumMismatch() {
    LegacyPayment p = payment("PMT-1", "1,000.00", "500.00", "400.00", "100.00", "0.00");
    DataQualityResult r = validator.validate(List.of(), List.of(), List.of(), List.of(p));
    assertThat(r.violations())
        .extracting(ValidationViolation::ruleId)
        .doesNotContain(DataQualityValidator.RULE_PMT_SUM_MISMATCH);
  }

  @Test
  void everyPaymentCarriesSeqNbrLossWarning() {
    LegacyPayment p = payment("PMT-1", "1,000.00", "500.00", "400.00", "100.00", "0.00");
    DataQualityResult r = validator.validate(List.of(), List.of(), List.of(), List.of(p));
    assertThat(r.violations())
        .filteredOn(v -> v.ruleId().equals(DataQualityValidator.RULE_PMT_SEQ_LOSS))
        .singleElement()
        .extracting(ValidationViolation::severity)
        .isEqualTo(ValidationViolation.Severity.WARN);
  }

  @Test
  void activeLoanWithDelinquencyIsFlagged() {
    LegacyLoanAccount l = loan("LN-2018-00089", "ACT", "15", "0.00");
    DataQualityResult r = validator.validate(List.of(), List.of(), List.of(l), List.of());
    assertThat(r.violations())
        .extracting(ValidationViolation::ruleId)
        .contains(DataQualityValidator.RULE_ACTIVE_DELINQUENT);
    LegacyLoanAccount clean = loan("LN-1", "ACT", "0", "0.00");
    r = validator.validate(List.of(), List.of(), List.of(clean), List.of());
    assertThat(r.violations())
        .extracting(ValidationViolation::ruleId)
        .doesNotContain(DataQualityValidator.RULE_ACTIVE_DELINQUENT);
  }

  @Test
  void escrowBalanceWithoutEscrowContributionsIsFlagged() {
    LegacyLoanAccount l = loan("LN-1", "ACT", "0", "3,245.80");
    LegacyPayment p = payment("PMT-1", "900.00", "500.00", "400.00", "0.00", "0.00");
    p.setLoanAccountNumber("LN-1");
    DataQualityResult r = validator.validate(List.of(), List.of(), List.of(l), List.of(p));
    assertThat(r.violations())
        .extracting(ValidationViolation::ruleId)
        .contains(DataQualityValidator.RULE_ESCROW_NO_CONTRIBUTION);
  }

  @Test
  void ssnPlaceholderIsWarned() {
    LegacyBorrower b = new LegacyBorrower();
    b.setBorrowerId("B-1");
    b.setFirstName("A");
    b.setLastName("B");
    b.setSsnEncrypted("ENC_XXX_001");
    b.setCreditScore("700");
    b.setAnnualIncome("50,000");
    b.setDateOfBirth("01/01/1980");
    b.setCreatedDate("01/01/2020");
    b.setUpdatedDate("01/01/2020");
    b.setStatusCode("ACT");
    DataQualityResult r = validator.validate(List.of(b), List.of(), List.of(), List.of());
    assertThat(r.violations())
        .filteredOn(v -> v.ruleId().equals(DataQualityValidator.RULE_SSN_PLACEHOLDER))
        .singleElement()
        .extracting(ValidationViolation::severity)
        .isEqualTo(ValidationViolation.Severity.WARN);
    assertThat(r.recordKeysByTable().get(DataQualityValidator.TABLE_BORROWER))
        .containsExactly("B-1");
  }

  private static LegacyLoanAccount loan(
      String number, String status, String delinquencyDays, String escrowBalance) {
    LegacyLoanAccount l = new LegacyLoanAccount();
    l.setLoanAccountNumber(number);
    l.setBorrowerId("B-1");
    l.setProductCode("P-1");
    l.setOriginalAmount("100,000");
    l.setCurrentBalance("90,000.00");
    l.setInterestRate("4.5");
    l.setTermMonths("360");
    l.setMonthlyPayment("900.00");
    l.setOriginationDate("01/01/2020");
    l.setMaturityDate("01/01/2050");
    l.setFirstPaymentDate("02/01/2020");
    l.setNextPaymentDate("02/01/2026");
    l.setStatusCode(status);
    l.setDelinquencyDays(delinquencyDays);
    l.setEscrowBalance(escrowBalance);
    l.setLtvPercent("80");
    l.setPropertyType("SFR");
    l.setAppraisedValue("125,000");
    l.setCreatedDate("01/01/2020");
    l.setUpdatedDate("01/01/2020");
    return l;
  }

  private static LegacyPayment payment(
      String seq, String total, String principal, String interest, String escrow, String late) {
    LegacyPayment p = new LegacyPayment();
    p.setPaymentSequenceNumber(seq);
    p.setLoanAccountNumber("LN-1");
    p.setPaymentDate("12/15/2025");
    p.setTotalAmount(total);
    p.setPrincipalAmount(principal);
    p.setInterestAmount(interest);
    p.setEscrowAmount(escrow);
    p.setLateFee(late);
    p.setTypeCode("REG");
    p.setStatusCode("PST");
    return p;
  }
}
