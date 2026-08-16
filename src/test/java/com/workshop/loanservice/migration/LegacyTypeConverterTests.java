package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyTypeConverterTests {

    private final LegacyTypeConverter converter = new LegacyTypeConverter();

    @Test
    void parsesLegacyStrings() {
        assertThat(converter.parseDate("02/15/2019")).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(converter.parseAmount("285,000")).isEqualByComparingTo(new BigDecimal("285000"));
        assertThat(converter.parseAmount("1,487.02")).isEqualByComparingTo(new BigDecimal("1487.02"));
        assertThat(converter.parseInteger("742")).isEqualTo(742);
    }

    @Test
    void treatsNullsAndBlanksAsAbsent() {
        assertThat(converter.parseDate(null)).isNull();
        assertThat(converter.parseDate("  ")).isNull();
        assertThat(converter.parseInteger(null)).isNull();
        assertThat(converter.parseAmount(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void expandsCodesToCanonicalValues() {
        assertThat(converter.canonicalLoanStatus("FRB")).isEqualTo("FORBEARANCE");
        assertThat(converter.canonicalPaymentType("PRE")).isEqualTo("PREPAYMENT");
        assertThat(converter.canonicalPaymentStatus("NSF")).isEqualTo("NSF");
        assertThat(converter.canonicalPropertyType("MFR")).isEqualTo("Multi-Family Residence");
        assertThat(converter.parseActiveFlag("ACT")).isTrue();
        assertThat(converter.parseActiveFlag("INA")).isFalse();
    }

    @Test
    void passesUnknownCodesThrough() {
        assertThat(converter.canonicalLoanStatus("ZZZ")).isEqualTo("ZZZ");
        assertThat(converter.canonicalPropertyType(null)).isNull();
    }
}
