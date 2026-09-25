package com.workshop.loanservice.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountTransformerTest {

  private final AmountTransformer transformer = new AmountTransformer();

  @ParameterizedTest
  @ValueSource(strings = {"285,000", "271,432.56", "1,487.02", "0.00", "50", "1500000.5"})
  void acceptsWellFormedAmounts(String raw) {
    TransformResult<BigDecimal> r = transformer.transform(raw, "AMT");
    assertThat(r.isValid()).isTrue();
    assertThat(r.value()).isEqualByComparingTo(new BigDecimal(raw.replace(",", "")));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void missingAmountIsViolationNotZero(String raw) {
    TransformResult<BigDecimal> r = transformer.transform(raw, "AMT");
    assertThat(r.value()).isNull();
    assertThat(r.violations()).hasSize(1);
    assertThat(r.violations().get(0).ruleId()).isEqualTo(AmountTransformer.RULE_MISSING);
    assertThat(r.violations().get(0).severity()).isEqualTo(ValidationViolation.Severity.ERROR);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "12,34,567",
        "1.487,02",
        "1,000.000",
        "$1,000",
        "-5",
        "abc",
        "1,,000",
        ",100",
        "1000,",
        "1.2.3",
        "N/A",
        "4.5%"
      })
  void malformedAmountIsViolationNotZero(String raw) {
    TransformResult<BigDecimal> r = transformer.transform(raw, "AMT");
    assertThat(r.value()).isNull();
    assertThat(r.hasError()).isTrue();
    assertThat(r.violations().get(0).ruleId()).isEqualTo(AmountTransformer.RULE_FORMAT);
    assertThat(r.violations().get(0).rawValue()).isEqualTo(raw);
    assertThat(r.violations().get(0).field()).isEqualTo("AMT");
  }
}
