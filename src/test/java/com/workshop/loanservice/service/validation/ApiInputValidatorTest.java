package com.workshop.loanservice.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.service.validation.ValidationViolation.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiInputValidatorTest {

  @ParameterizedTest
  @ValueSource(strings = {"LN-2018-00089", "LN-0000-00000", "LN-9999-99901"})
  void wellFormedLoanNumbersPass(String raw) {
    assertThat(ApiInputValidator.loanAccountNumber("id", raw)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"B-10001", "B-99901"})
  void wellFormedBorrowerIdsPass(String raw) {
    assertThat(ApiInputValidator.borrowerId("id", raw)).isEmpty();
  }

  @Test
  void blankIsMissing() {
    assertThat(only(ApiInputValidator.loanAccountNumber("id", "  ")).ruleId())
        .isEqualTo(ApiInputValidator.RULE_MISSING);
    assertThat(only(ApiInputValidator.borrowerId("id", null)).ruleId())
        .isEqualTo(ApiInputValidator.RULE_MISSING);
  }

  @ParameterizedTest
  @ValueSource(strings = {"LN-2018-0089", "ln-2018-00089", "10001", "B-1", "LN_2018_00089"})
  void wrongShapeIsFormat(String raw) {
    ValidationViolation v = only(ApiInputValidator.loanAccountNumber("id", raw));
    assertThat(v.ruleId()).isEqualTo(ApiInputValidator.RULE_FORMAT);
    assertThat(v.entityType()).isEqualTo(ApiInputValidator.ENTITY_TYPE);
    assertThat(v.rawValue()).isEqualTo(raw);
    assertThat(v.severity()).isEqualTo(Severity.ERROR);
  }

  @ParameterizedTest
  @ValueSource(strings = {"LN-2018-00089;--", "B-10001' OR 1=1", "LN-2018-00089-00000000000"})
  void disallowedCharactersOrLengthIsUnsafe(String raw) {
    assertThat(only(ApiInputValidator.borrowerId("id", raw)).ruleId())
        .isEqualTo(ApiInputValidator.RULE_UNSAFE);
  }

  @Test
  void notFoundCarriesTableAndKey() {
    ValidationViolation v = ApiInputValidator.notFound("loanId", "LN-0000-00000", "CDW_LN_ACCT");
    assertThat(v.ruleId()).isEqualTo(ApiInputValidator.RULE_NOT_FOUND);
    assertThat(v.recordKey()).isEqualTo("LN-0000-00000");
    assertThat(v.message()).contains("CDW_LN_ACCT");
  }

  private static ValidationViolation only(List<ValidationViolation> list) {
    assertThat(list).hasSize(1);
    return list.get(0);
  }
}
