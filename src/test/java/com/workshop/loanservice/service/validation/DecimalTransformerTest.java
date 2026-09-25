package com.workshop.loanservice.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DecimalTransformerTest {

  private final DecimalTransformer transformer = new DecimalTransformer();

  @Test
  void parsesRate() {
    TransformResult<BigDecimal> r = transformer.transform("4.750", "LN_INT_RT");
    assertThat(r.isValid()).isTrue();
    assertThat(r.value()).isEqualByComparingTo("4.750");
  }

  @Test
  void missingIsViolation() {
    TransformResult<BigDecimal> r = transformer.transform(null, "LN_INT_RT");
    assertThat(r.value()).isNull();
    assertThat(r.violations().get(0).ruleId()).isEqualTo(DecimalTransformer.RULE_MISSING);
  }

  @ParameterizedTest
  @ValueSource(strings = {"4.5%", "N/A", "1,000", "-1.0", "4."})
  void malformedIsViolation(String raw) {
    TransformResult<BigDecimal> r = transformer.transform(raw, "LN_INT_RT");
    assertThat(r.value()).isNull();
    assertThat(r.violations().get(0).ruleId()).isEqualTo(DecimalTransformer.RULE_FORMAT);
  }
}
