package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyValueParserTest {

    @Test
    void parsesAmountsWithThousandsSeparators() {
        assertThat(LegacyValueParser.parseAmount("C", "1,487.02")).isEqualByComparingTo("1487.02");
        assertThat(LegacyValueParser.parseAmount("C", "1,500,000")).isEqualByComparingTo("1500000");
        assertThat(LegacyValueParser.parseAmount("C", " 0.00 ")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parsesDecimalsIntegersAndDates() {
        assertThat(LegacyValueParser.parseDecimal("C", "4.750")).isEqualByComparingTo("4.75");
        assertThat(LegacyValueParser.parseInteger("C", "745")).isEqualTo(745);
        assertThat(LegacyValueParser.parseDate("C", "02/15/2019")).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(LegacyValueParser.parseTimestamp("C", "12/01/2025")).isEqualTo(LocalDateTime.of(2025, 12, 1, 0, 0));
    }

    @Test
    void blankValuesBecomeNull() {
        assertThat(LegacyValueParser.parseAmount("C", null)).isNull();
        assertThat(LegacyValueParser.parseAmount("C", "  ")).isNull();
        assertThat(LegacyValueParser.parseInteger("C", "")).isNull();
        assertThat(LegacyValueParser.parseDate("C", null)).isNull();
    }

    @Test
    void malformedValuesAreRejectedWithColumnName() {
        assertThatThrownBy(() -> LegacyValueParser.parseAmount("LN_ORIG_AMT", "12a,000"))
                .isInstanceOf(MalformedLegacyDataException.class)
                .hasMessageContaining("LN_ORIG_AMT");
        assertThatThrownBy(() -> LegacyValueParser.parseInteger("BORR_CRDT_SCR", "7.5"))
                .isInstanceOf(MalformedLegacyDataException.class);
        assertThatThrownBy(() -> LegacyValueParser.parseDate("BORR_DOB_DT", "02/30/1990"))
                .isInstanceOf(MalformedLegacyDataException.class);
        assertThatThrownBy(() -> LegacyValueParser.parseDate("BORR_DOB_DT", "1990-02-01"))
                .isInstanceOf(MalformedLegacyDataException.class);
    }

    @Test
    void unknownOrMissingCodesAreRejected() {
        assertThat(LegacyCodeMappings.propertyType("CND")).isEqualTo("Condominium");
        assertThat(LegacyCodeMappings.propertyType(null)).isNull();
        assertThatThrownBy(() -> LegacyCodeMappings.loanStatus("XYZ"))
                .isInstanceOf(MalformedLegacyDataException.class)
                .hasMessageContaining("unknown code");
        assertThatThrownBy(() -> LegacyCodeMappings.paymentType(null))
                .isInstanceOf(MalformedLegacyDataException.class)
                .hasMessageContaining("missing code");
    }
}
