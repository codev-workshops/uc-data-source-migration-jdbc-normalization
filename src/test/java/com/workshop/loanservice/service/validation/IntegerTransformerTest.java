package com.workshop.loanservice.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntegerTransformerTest {

  @Test
  void creditScoreHappyPath() {
    TransformResult<Integer> r = IntegerTransformer.creditScore().transform("745", "CS");
    assertThat(r.isValid()).isTrue();
    assertThat(r.value()).isEqualTo(745);
  }

  @ParameterizedTest
  @ValueSource(strings = {"299", "851", "9999"})
  void creditScoreOutOfRange(String raw) {
    TransformResult<Integer> r = IntegerTransformer.creditScore().transform(raw, "CS");
    assertThat(r.value()).isNull();
    assertThat(r.violations().get(0).ruleId()).isEqualTo(IntegerTransformer.RULE_RANGE);
  }

  @Test
  void creditScoreBoundariesInclusive() {
    assertThat(IntegerTransformer.creditScore().transform("300", "CS").value()).isEqualTo(300);
    assertThat(IntegerTransformer.creditScore().transform("850", "CS").value()).isEqualTo(850);
  }

  @Test
  void termMonthsRange() {
    assertThat(IntegerTransformer.termMonths().transform("360", "T").isValid()).isTrue();
    assertThat(IntegerTransformer.termMonths().transform("0", "T").violations().get(0).ruleId())
        .isEqualTo(IntegerTransformer.RULE_RANGE);
    assertThat(IntegerTransformer.termMonths().transform("999", "T").violations().get(0).ruleId())
        .isEqualTo(IntegerTransformer.RULE_RANGE);
  }

  @Test
  void delinquencyDaysRejectsNegative() {
    TransformResult<Integer> r = IntegerTransformer.delinquencyDays().transform("-3", "D");
    assertThat(r.value()).isNull();
    assertThat(r.violations().get(0).ruleId()).isEqualTo(IntegerTransformer.RULE_FORMAT);
    assertThat(IntegerTransformer.delinquencyDays().transform("0", "D").value()).isZero();
    assertThat(IntegerTransformer.delinquencyDays().transform("15", "D").value()).isEqualTo(15);
  }

  @Test
  void missingAndMalformed() {
    IntegerTransformer t = IntegerTransformer.creditScore();
    assertThat(t.transform(null, "CS").violations().get(0).ruleId())
        .isEqualTo(IntegerTransformer.RULE_MISSING);
    assertThat(t.transform(" ", "CS").violations().get(0).ruleId())
        .isEqualTo(IntegerTransformer.RULE_MISSING);
    assertThat(t.transform("7a5", "CS").violations().get(0).ruleId())
        .isEqualTo(IntegerTransformer.RULE_FORMAT);
    assertThat(t.transform("7.5", "CS").violations().get(0).ruleId())
        .isEqualTo(IntegerTransformer.RULE_FORMAT);
  }
}
