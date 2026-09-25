package com.workshop.loanservice.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DateTransformerTest {

  private final DateTransformer transformer = new DateTransformer();

  @Test
  void parsesStrictMmDdYyyy() {
    TransformResult<LocalDate> r = transformer.transform("02/15/2019", "DT");
    assertThat(r.isValid()).isTrue();
    assertThat(r.value()).isEqualTo(LocalDate.of(2019, 2, 15));
  }

  @Test
  void leapDayIsValidOnlyInLeapYears() {
    assertThat(transformer.transform("02/29/2024", "DT").isValid()).isTrue();
    assertThat(transformer.transform("02/29/2023", "DT").violations().get(0).ruleId())
        .isEqualTo(DateTransformer.RULE_CALENDAR);
  }

  @Test
  void missingIsViolation() {
    assertThat(transformer.transform(null, "DT").violations().get(0).ruleId())
        .isEqualTo(DateTransformer.RULE_MISSING);
    assertThat(transformer.transform("", "DT").violations().get(0).ruleId())
        .isEqualTo(DateTransformer.RULE_MISSING);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2019-02-15", "2/15/2019", "15/02/2019x", "02-15-2019", "Feb 15 2019"})
  void wrongFormatIsViolation(String raw) {
    TransformResult<LocalDate> r = transformer.transform(raw, "DT");
    assertThat(r.value()).isNull();
    assertThat(r.violations().get(0).ruleId()).isEqualTo(DateTransformer.RULE_FORMAT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"13/45/2020", "02/30/1990", "00/10/2020", "04/31/2021"})
  void impossibleCalendarDateIsViolation(String raw) {
    TransformResult<LocalDate> r = transformer.transform(raw, "DT");
    assertThat(r.value()).isNull();
    assertThat(r.violations().get(0).ruleId()).isEqualTo(DateTransformer.RULE_CALENDAR);
  }
}
