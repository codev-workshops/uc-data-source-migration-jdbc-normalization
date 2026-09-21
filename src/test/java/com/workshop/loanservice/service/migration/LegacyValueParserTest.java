package com.workshop.loanservice.service.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyValueParserTest {

    @Test
    void parsesLegacyDateAndTimestamp() {
        assertThat(LegacyValueParser.requireDate("03/15/1978", "B-1", "DOB")).isEqualTo(LocalDate.of(1978, 3, 15));
        assertThat(LegacyValueParser.requireTimestamp("12/31/2099", "P-1", "EXP"))
                .isEqualTo(LocalDateTime.of(2099, 12, 31, 0, 0));
        assertThat(LegacyValueParser.optionalDate(" ", "B-1", "DOB")).isNull();
        assertThat(LegacyValueParser.optionalTimestamp(null, "B-1", "DOB")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2019-02-15", "15/02/2019", "02/30/2019", "abc", "2/15/19"})
    void rejectsMalformedDates(String value) {
        assertThatThrownBy(() -> LegacyValueParser.requireDate(value, "LN-1", "LN_ORIG_DT"))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("LN-1")
                .hasMessageContaining("LN_ORIG_DT");
    }

    @Test
    void parsesAmountsWithThousandsSeparators() {
        assertThat(LegacyValueParser.requireAmount("1,487.02", "P-1", "AMT")).isEqualByComparingTo("1487.02");
        assertThat(LegacyValueParser.requireAmount("1,500,000", "P-1", "AMT")).isEqualByComparingTo("1500000");
        assertThat(LegacyValueParser.requireAmount("0", "P-1", "AMT")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(LegacyValueParser.requireAmount("4.750", "P-1", "RT")).isEqualByComparingTo("4.750");
        assertThat(LegacyValueParser.optionalAmount("", "P-1", "AMT")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,4x7.02", "$100", "N/A", "--"})
    void rejectsMalformedAmounts(String value) {
        assertThatThrownBy(() -> LegacyValueParser.requireAmount(value, "PMT-1", "PMT_AMT"))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("PMT-1")
                .hasMessageContaining("PMT_AMT");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void requiredValuesRejectNullAndBlank(String value) {
        assertThatThrownBy(() -> LegacyValueParser.requireAmount(value, "PMT-1", "PMT_AMT"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("null or blank");
        assertThatThrownBy(() -> LegacyValueParser.requireDate(value, "PMT-1", "PMT_DT"))
                .isInstanceOf(MigrationException.class);
        assertThatThrownBy(() -> LegacyValueParser.requireInteger(value, "PMT-1", "TERM"))
                .isInstanceOf(MigrationException.class);
        assertThatThrownBy(() -> LegacyValueParser.requireText(value, "PMT-1", "NAME"))
                .isInstanceOf(MigrationException.class);
    }

    @Test
    void amountsAreCheckedAgainstTargetPrecisionAndScale() {
        assertThat(LegacyValueParser.requireAmount("99,999,999.99", "P-1", "AMT", 10, 2))
                .isEqualByComparingTo("99999999.99");
        assertThat(LegacyValueParser.requireAmount("5.250", "L-1", "RT", 5, 3)).isEqualByComparingTo("5.250");
        assertThat(LegacyValueParser.optionalAmount(" ", "P-1", "AMT", 10, 2)).isNull();

        assertThatThrownBy(() -> LegacyValueParser.requireAmount("123,456,789.00", "P-1", "AMT", 10, 2))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("P-1").hasMessageContaining("AMT").hasMessageContaining("DECIMAL(10,2)");
        assertThatThrownBy(() -> LegacyValueParser.requireAmount("1.005", "P-1", "AMT", 10, 2))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("decimal places");
    }

    @Test
    void parsesIntegers() {
        assertThat(LegacyValueParser.requireInteger("360", "P-1", "TERM")).isEqualTo(360);
        assertThat(LegacyValueParser.requireInteger(" 745 ", "B-1", "SCORE")).isEqualTo(745);
        assertThat(LegacyValueParser.optionalInteger(null, "B-1", "SCORE")).isNull();
        assertThatThrownBy(() -> LegacyValueParser.requireInteger("36.5", "P-1", "TERM"))
                .isInstanceOf(MigrationException.class);
        assertThatThrownBy(() -> LegacyValueParser.requireInteger("abc", "P-1", "TERM"))
                .isInstanceOf(MigrationException.class);
    }

    @Test
    void optionalTextTrimsAndBlanksToNull() {
        assertThat(LegacyValueParser.optionalText(" Apt 3B ")).isEqualTo("Apt 3B");
        assertThat(LegacyValueParser.optionalText("")).isNull();
        assertThat(LegacyValueParser.optionalText(null)).isNull();
    }
}
