package com.workshop.loanservice.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSetTest {

    private record Row(String id, String value) {
    }

    private static List<ValidationFinding> run(RuleSet<Row> rules, String value) {
        return rules.validate(List.of(new Row("R-1", value)));
    }

    private static RuleSet<Row> rules() {
        return RuleSet.forTable("T", Row::id);
    }

    @Test
    void parsesLegacyValuesStrictly() {
        assertThat(LegacyValues.parseDate("02/28/1990")).contains(LocalDate.of(1990, 2, 28));
        assertThat(LegacyValues.parseDate("02/30/1990")).isEmpty();
        assertThat(LegacyValues.parseDate("1990-02-28")).isEmpty();
        assertThat(LegacyValues.parseAmount("1,487.02")).contains(new BigDecimal("1487.02"));
        assertThat(LegacyValues.parseAmount("12a")).isEmpty();
        assertThat(LegacyValues.parseDecimal("1,487.02")).isEmpty();
        assertThat(LegacyValues.parseInteger(" 360 ")).contains(360);
        assertThat(LegacyValues.parseInteger("99999999999")).isEmpty();
    }

    @Test
    void requiredReportsBlankAsErrorWithRecordContext() {
        List<ValidationFinding> findings = run(rules().required("COL", Row::value), " ");

        assertThat(findings).containsExactly(
                new ValidationFinding(Severity.ERROR, "T", "R-1", "COL", "required value is missing"));
    }

    @Test
    void formatChecksSkipNullsAndFlagMalformedValues() {
        RuleSet<Row> rules = rules()
                .date("COL", Row::value)
                .amount("COL", Row::value)
                .integerInRange("COL", Row::value, 0, 10);

        assertThat(run(rules, null)).isEmpty();
        assertThat(run(rules, "bad")).hasSize(3).allMatch(f -> f.severity() == Severity.ERROR);
    }

    @Test
    void rangeAndCodeChecks() {
        assertThat(run(rules().integerInRange("C", Row::value, 300, 850), "299")).hasSize(1);
        assertThat(run(rules().integerInRange("C", Row::value, 300, 850), "850")).isEmpty();
        assertThat(run(rules().amountAtLeast("C", Row::value, BigDecimal.ZERO), "-1")).hasSize(1);
        assertThat(run(rules().decimalInRange("C", Row::value, BigDecimal.ZERO, BigDecimal.TEN), "10.5"))
                .hasSize(1);
        assertThat(run(rules().oneOf("C", Row::value, Set.of("ACT", "INA")), "XYZ"))
                .singleElement()
                .extracting(ValidationFinding::message)
                .isEqualTo("'XYZ' is not one of [ACT, INA]");
    }

    @Test
    void warnIfNullEmitsWarning() {
        assertThat(run(rules().warnIfNull("C", Row::value), null))
                .singleElement()
                .extracting(ValidationFinding::severity)
                .isEqualTo(Severity.WARNING);
    }
}
