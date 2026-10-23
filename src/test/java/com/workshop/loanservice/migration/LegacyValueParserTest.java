package com.workshop.loanservice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LegacyValueParserTest {

    @Test
    void parseDecimalStripsThousandsSeparators() {
        assertThat(LegacyValueParser.parseDecimal("AMT", "1,487.02")).isEqualByComparingTo("1487.02");
        assertThat(LegacyValueParser.parseDecimal("AMT", " 285,000 ")).isEqualByComparingTo("285000");
        assertThat(LegacyValueParser.parseDecimal("RATE", "4.750")).isEqualTo(new BigDecimal("4.750"));
    }

    @Test
    void blankValuesBecomeNull() {
        assertThat(LegacyValueParser.parseDecimal("AMT", null)).isNull();
        assertThat(LegacyValueParser.parseDecimal("AMT", "   ")).isNull();
        assertThat(LegacyValueParser.parseInteger("SCORE", "")).isNull();
        assertThat(LegacyValueParser.parseDate("DT", " ")).isNull();
    }

    @Test
    void malformedValuesAreRejected() {
        assertThatThrownBy(() -> LegacyValueParser.parseDecimal("AMT", "12O.00"))
                .isInstanceOf(LegacyDataException.class).hasMessageContaining("AMT");
        assertThatThrownBy(() -> LegacyValueParser.parseInteger("SCORE", "7.5"))
                .isInstanceOf(LegacyDataException.class);
        assertThatThrownBy(() -> LegacyValueParser.parseDate("DT", "2020-02-01"))
                .isInstanceOf(LegacyDataException.class);
        assertThatThrownBy(() -> LegacyValueParser.parseDate("DT", "02/30/2020"))
                .isInstanceOf(LegacyDataException.class);
    }

    @Test
    void malformedValuesAreNotEchoedInMessages() {
        assertThatThrownBy(() -> LegacyValueParser.parseDate("BORR_DOB_DT", "1978-03-15"))
                .hasMessageContaining("BORR_DOB_DT").hasMessageNotContaining("1978").hasNoCause();
        assertThatThrownBy(() -> LegacyValueParser.parseDecimal("BORR_ANN_INCM", "95,OOO"))
                .hasMessageContaining("BORR_ANN_INCM").hasMessageNotContaining("95").hasNoCause();
        assertThatThrownBy(() -> LegacyValueParser.parseInteger("BORR_CRDT_SCR", "7x2"))
                .hasMessageContaining("BORR_CRDT_SCR").hasMessageNotContaining("7x2").hasNoCause();
    }

    @Test
    void parseDateReadsMonthDayYear() {
        assertThat(LegacyValueParser.parseDate("DT", "02/15/2019")).isEqualTo(LocalDate.of(2019, 2, 15));
    }

    @Test
    void requireRejectsBlank() {
        assertThatThrownBy(() -> LegacyValueParser.require("ID", "  "))
                .isInstanceOf(LegacyDataException.class).hasMessageContaining("ID");
    }
}
