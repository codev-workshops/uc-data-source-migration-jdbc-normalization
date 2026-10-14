package com.workshop.loanservice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workshop.loanservice.modern.entity.LoanStatus;
import com.workshop.loanservice.modern.entity.PaymentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LegacyValueParserTest {

    @Test
    void parsesLegacyDates() {
        assertThat(LegacyValueParser.date("02/15/2019")).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(LegacyValueParser.timestamp("12/01/2025"))
                .isEqualTo(LocalDateTime.of(2025, 12, 1, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"13/01/2025", "02/30/2025", "2025-01-15", "1/5/2025", "garbage"})
    void rejectsMalformedDates(String value) {
        assertThatThrownBy(() -> LegacyValueParser.date(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid date");
    }

    @Test
    void parsesAmountsWithAndWithoutGrouping() {
        assertThat(LegacyValueParser.amount("285,000")).isEqualByComparingTo("285000");
        assertThat(LegacyValueParser.amount("1,487.02")).isEqualByComparingTo("1487.02");
        assertThat(LegacyValueParser.amount("1,500,000")).isEqualByComparingTo("1500000");
        assertThat(LegacyValueParser.amount("811.61")).isEqualByComparingTo("811.61");
        assertThat(LegacyValueParser.amount("0")).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,48", "12,34.5", "1.2.3", "$100", "abc"})
    void rejectsMalformedAmounts(String value) {
        assertThatThrownBy(() -> LegacyValueParser.amount(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid amount");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankValuesBecomeNull(String value) {
        assertThat(LegacyValueParser.date(value)).isNull();
        assertThat(LegacyValueParser.amount(value)).isNull();
        assertThat(LegacyValueParser.integer(value)).isNull();
    }

    @Test
    void parsesIntegersStrictly() {
        assertThat(LegacyValueParser.integer("360")).isEqualTo(360);
        assertThatThrownBy(() -> LegacyValueParser.integer("36O"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsLegacyCodesToEnums() {
        assertThat(LegacyValueParser.code(LoanStatus.class, "FRB"))
                .isEqualTo(LoanStatus.FORBEARANCE);
        assertThat(LegacyValueParser.code(PaymentStatus.class, "NSF")).isEqualTo(PaymentStatus.NSF);
        assertThatThrownBy(() -> LegacyValueParser.code(LoanStatus.class, "XYZ"))
                .hasMessage("unknown LoanStatus code 'XYZ'");
    }

    @Test
    void requiredRejectsMissingValues() {
        assertThat(LegacyValueParser.required("x", "COL")).isEqualTo("x");
        assertThatThrownBy(() -> LegacyValueParser.required(" ", "PMT_DT"))
                .hasMessage("PMT_DT is required");
        assertThatThrownBy(() -> LegacyValueParser.required(null, "PMT_AMT"))
                .hasMessage("PMT_AMT is required");
    }
}
