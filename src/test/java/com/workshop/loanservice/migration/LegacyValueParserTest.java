package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyValueParserTest {

    private final LegacyValueParser parser = new LegacyValueParser();

    @ParameterizedTest
    @CsvSource({
        "'285,000', 285000",
        "'1,487.02', 1487.02",
        "'0.00', 0.00",
        "'  92,500.00  ', 92500.00"
    })
    void parsesLegacyAmounts(String raw, String expected) {
        assertThat(parser.parseAmount(raw, "AMT")).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void keepsAmountScaleSoRoundTripsDoNotInventCents() {
        assertThat(parser.parseAmount("285,000", "AMT").scale()).isZero();
        assertThat(parser.parseAmount("0.00", "AMT").scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void treatsBlankAsAbsentRatherThanZero(String raw) {
        assertThat(parser.parseAmount(raw, "AMT")).isNull();
        assertThat(parser.parseInteger(raw, "NUM")).isNull();
        assertThat(parser.parseDate(raw, "DT")).isNull();
    }

    @Test
    void nullIsAbsent() {
        assertThat(parser.parseAmount(null, "AMT")).isNull();
        assertThat(parser.parseTimestamp(null, "DT")).isNull();
    }

    @Test
    void parsesLegacyDate() {
        assertThat(parser.parseDate("02/15/2019", "DT")).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(parser.parseTimestamp("02/15/2019", "DT")).isEqualTo(LocalDate.of(2019, 2, 15).atStartOfDay());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2019-02-15", "02/30/2020", "13/01/2020", "15/02/2019", "not a date"})
    void rejectsUnparseableDatesInsteadOfGuessing(String raw) {
        assertThatThrownBy(() -> parser.parseDate(raw, "LN_ORIG_DT"))
            .isInstanceOf(LegacyValueParseException.class)
            .hasMessageContaining("LN_ORIG_DT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.2.3", "12,34,56.78.9", "$285,000"})
    void rejectsUnparseableAmounts(String raw) {
        assertThatThrownBy(() -> parser.parseAmount(raw, "LN_ORIG_AMT"))
            .isInstanceOf(LegacyValueParseException.class);
    }

    @Test
    void rejectsUnparseableInteger() {
        assertThatThrownBy(() -> parser.parseInteger("74five", "BORR_CRDT_SCR"))
            .isInstanceOf(LegacyValueParseException.class)
            .hasMessageContaining("BORR_CRDT_SCR");
    }
}
