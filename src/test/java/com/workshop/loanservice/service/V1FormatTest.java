package com.workshop.loanservice.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class V1FormatTest {

    @Test
    void rendersLegacyDateFormat() {
        assertThat(V1Format.date(LocalDate.of(2019, 2, 15))).isEqualTo("02/15/2019");
        assertThat(V1Format.date(null)).isNull();
    }

    /** Scale is part of the JSON contract: 0.00 and 0 are different bytes to a client. */
    @Test
    void moneyKeepsTwoDecimals() {
        assertThat(V1Format.money(new BigDecimal("0.00")).toPlainString()).isEqualTo("0.00");
        assertThat(V1Format.money(new BigDecimal("271432.56")).toPlainString()).isEqualTo("271432.56");
        assertThat(V1Format.money(null).toPlainString()).isEqualTo("0");
    }

    @Test
    void originationAmountDropsCentsWhenWhole() {
        assertThat(V1Format.originationAmount(new BigDecimal("285000.00")).toPlainString()).isEqualTo("285000");
        assertThat(V1Format.originationAmount(new BigDecimal("285000.50")).toPlainString()).isEqualTo("285000.50");
    }

    @Test
    void rateKeepsThreeDecimals() {
        assertThat(V1Format.rate(new BigDecimal("4.750")).toPlainString()).isEqualTo("4.750");
        assertThat(V1Format.rate(new BigDecimal("3.1")).toPlainString()).isEqualTo("3.100");
    }

    @Test
    void concatenatesPropertyAddressExactlyAsV1Did() {
        assertThat(V1Format.propertyAddress("742 Elm Street", "Springfield", "IL", "62701"))
            .isEqualTo("742 Elm Street, Springfield, IL 62701");
    }

    @Test
    void fullNameOmitsTheMiddleInitialWhenAbsent() {
        assertThat(V1Format.fullName("James", "R", "Mitchell")).isEqualTo("James R. Mitchell");
        assertThat(V1Format.fullName("James", null, "Mitchell")).isEqualTo("James Mitchell");
    }
}
