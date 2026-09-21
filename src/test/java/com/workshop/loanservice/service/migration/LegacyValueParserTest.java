package com.workshop.loanservice.service.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @CsvSource({
            "01/01/2020, 2020-01-01",
            "12/31/2099, 2099-12-31",
            "02/29/2024, 2024-02-29",
            "' 03/15/1978 ', 1978-03-15"
    })
    void parsesStrictMonthDayYearDates(String legacy, LocalDate expected) {
        assertThat(LegacyValueParser.requireDate(legacy, "B-1", "DOB")).isEqualTo(expected);
        assertThat(LegacyValueParser.optionalDate(legacy, "B-1", "DOB")).isEqualTo(expected);
        assertThat(LegacyValueParser.optionalTimestamp(legacy, "B-1", "DOB")).isEqualTo(expected.atStartOfDay());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2019-02-15", "15/02/2019", "02/30/2019", "02/29/2023", "13/01/2020", "00/10/2020",
            "01/00/2020", "abc", "2/15/19", "1/5/2020", "02-15-2019", "02/15/2019 10:00"})
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
    @CsvSource({
            "'285,000', 285000",
            "'271,432.56', 271432.56",
            "'0.00', 0.00",
            "'92,500', 92500",
            "' 1,077.05 ', 1077.05",
            "'-47.50', -47.50",
            "'82.5', 82.5",
            "'.50', 0.50"
    })
    void parsesCommaStrippedDecimals(String legacy, BigDecimal expected) {
        assertThat(LegacyValueParser.requireAmount(legacy, "LN-1", "AMT")).isEqualByComparingTo(expected);
        assertThat(LegacyValueParser.optionalAmount(legacy, "LN-1", "AMT")).isEqualByComparingTo(expected);
    }

    @Test
    void amountsKeepTheirLegacyScale() {
        assertThat(LegacyValueParser.requireAmount("285,000", "LN-1", "AMT").scale()).isZero();
        assertThat(LegacyValueParser.requireAmount("0.00", "LN-1", "AMT").scale()).isEqualTo(2);
        assertThat(LegacyValueParser.requireAmount("4.750", "LN-1", "RT").scale()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,4x7.02", "$100", "N/A", "--", "1.2.3", "1 000", "100-", "NaN", "1e"})
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

    @ParameterizedTest
    @ValueSource(strings = {"36.5", "abc", "1e3", "12-", "0x1F", "3 60", "99999999999"})
    void rejectsMalformedIntegers(String value) {
        assertThatThrownBy(() -> LegacyValueParser.requireInteger(value, "LN-1", "LN_TERM_MOS"))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("LN-1")
                .hasMessageContaining("LN_TERM_MOS");
        assertThatThrownBy(() -> LegacyValueParser.optionalInteger(value, "LN-1", "LN_TERM_MOS"))
                .isInstanceOf(MigrationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1,4x7", "02/30/2019"})
    void optionalParsersStillRejectMalformedNonBlankInput(String value) {
        assertThatThrownBy(() -> LegacyValueParser.optionalAmount(value, "X-1", "F"))
                .isInstanceOf(MigrationException.class);
        assertThatThrownBy(() -> LegacyValueParser.optionalDate(value, "X-1", "F"))
                .isInstanceOf(MigrationException.class);
        assertThatThrownBy(() -> LegacyValueParser.optionalTimestamp(value, "X-1", "F"))
                .isInstanceOf(MigrationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void optionalParsersMapBlankToNullNeverToDefault(String value) {
        assertThat(LegacyValueParser.optionalAmount(value, "X-1", "F")).isNull();
        assertThat(LegacyValueParser.optionalInteger(value, "X-1", "F")).isNull();
        assertThat(LegacyValueParser.optionalDate(value, "X-1", "F")).isNull();
        assertThat(LegacyValueParser.optionalTimestamp(value, "X-1", "F")).isNull();
        assertThat(LegacyValueParser.optionalText(value)).isNull();
    }

    @Test
    void parsesIntegers() {
        assertThat(LegacyValueParser.requireInteger("1,000", "P-1", "TERM")).isEqualTo(1000);
        assertThat(LegacyValueParser.requireInteger("0", "LN-1", "DLQ")).isZero();
        assertThat(LegacyValueParser.requireInteger("-5", "LN-1", "DLQ")).isEqualTo(-5);
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
