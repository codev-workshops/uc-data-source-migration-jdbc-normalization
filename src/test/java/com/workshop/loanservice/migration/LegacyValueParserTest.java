package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyValueParserTest {

    private final LegacyValueParser parser = new LegacyValueParser();

    @Test
    void parsesLegacyDate() {
        assertThat(parser.parseDate("02/15/2019")).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(parser.parseDate(null)).isNull();
        assertThat(parser.parseDate("  ")).isNull();
    }

    @Test
    void rejectsMalformedDate() {
        assertThatThrownBy(() -> parser.parseDate("2019-02-15"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed legacy date");
        assertThatThrownBy(() -> parser.parseDate("13/45/2019"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsImpossibleCalendarDates() {
        assertThatThrownBy(() -> parser.parseDate("02/30/2019"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed legacy date");
        assertThatThrownBy(() -> parser.parseDate("02/29/2019"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseDate("04/31/2019"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(parser.parseDate("02/29/2020")).isEqualTo(LocalDate.of(2020, 2, 29));
    }

    @Test
    void parsesTimestampAtStartOfDay() {
        assertThat(parser.parseTimestamp("01/15/2019")).isEqualTo(LocalDateTime.of(2019, 1, 15, 0, 0));
        assertThat(parser.parseTimestamp(null)).isNull();
    }

    @Test
    void parsesAmountsWithCommas() {
        assertThat(parser.parseAmount("285,000")).isEqualByComparingTo("285000");
        assertThat(parser.parseAmount("1,487.02")).isEqualByComparingTo("1487.02");
        assertThat(parser.parseAmount("0.00")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(parser.parseAmount(null)).isNull();
        assertThat(parser.parseAmount("")).isNull();
    }

    @Test
    void rejectsMalformedAmount() {
        assertThatThrownBy(() -> parser.parseAmount("1.2.3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed legacy amount");
    }

    @Test
    void parsesIntegers() {
        assertThat(parser.parseInteger("745")).isEqualTo(745);
        assertThat(parser.parseInteger(" 360 ")).isEqualTo(360);
        assertThat(parser.parseInteger(null)).isNull();
        assertThatThrownBy(() -> parser.parseInteger("7a5"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expandsBorrowerStatus() {
        assertThat(parser.expandBorrowerStatus("ACT")).isEqualTo("ACTIVE");
        assertThat(parser.expandBorrowerStatus("INA")).isEqualTo("INACTIVE");
        assertThat(parser.expandBorrowerStatus(null)).isNull();
        assertThatThrownBy(() -> parser.expandBorrowerStatus("XXX"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsProductStatusToActiveFlag() {
        assertThat(parser.parseProductActive("ACT")).isTrue();
        assertThat(parser.parseProductActive("INA")).isFalse();
        assertThatThrownBy(() -> parser.parseProductActive("XXX"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expandsLoanStatus() {
        assertThat(parser.expandLoanStatus("ACT")).isEqualTo("ACTIVE");
        assertThat(parser.expandLoanStatus("CLO")).isEqualTo("CLOSED");
        assertThat(parser.expandLoanStatus("DFT")).isEqualTo("DEFAULT");
        assertThat(parser.expandLoanStatus("FRB")).isEqualTo("FORBEARANCE");
        assertThatThrownBy(() -> parser.expandLoanStatus("XXX"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expandsPropertyType() {
        assertThat(parser.expandPropertyType("SFR")).isEqualTo("SINGLE_FAMILY");
        assertThat(parser.expandPropertyType("CND")).isEqualTo("CONDOMINIUM");
        assertThat(parser.expandPropertyType("MFR")).isEqualTo("MULTI_FAMILY");
        assertThat(parser.expandPropertyType("TWN")).isEqualTo("TOWNHOUSE");
        assertThatThrownBy(() -> parser.expandPropertyType("XXX"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expandsPaymentTypeAndStatus() {
        assertThat(parser.expandPaymentType("REG")).isEqualTo("REGULAR");
        assertThat(parser.expandPaymentType("EXT")).isEqualTo("EXTRA");
        assertThat(parser.expandPaymentType("PRT")).isEqualTo("PARTIAL");
        assertThat(parser.expandPaymentType("PRE")).isEqualTo("PREPAYMENT");
        assertThat(parser.expandPaymentStatus("PST")).isEqualTo("POSTED");
        assertThat(parser.expandPaymentStatus("REV")).isEqualTo("REVERSED");
        assertThat(parser.expandPaymentStatus("NSF")).isEqualTo("NSF");
        assertThat(parser.expandPaymentStatus("PND")).isEqualTo("PENDING");
        assertThatThrownBy(() -> parser.expandPaymentType("XXX"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.expandPaymentStatus("XXX"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
