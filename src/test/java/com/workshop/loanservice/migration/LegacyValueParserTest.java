package com.workshop.loanservice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workshop.loanservice.modern.entity.QuarantineReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LegacyValueParserTest {

  @Test
  void textTrimsAndTreatsBlankAsNull() {
    assertThat(LegacyValueParser.text("  Apt 3B ")).isEqualTo("Apt 3B");
    assertThat(LegacyValueParser.text("   ")).isNull();
    assertThat(LegacyValueParser.text(null)).isNull();
    assertRejected(() -> LegacyValueParser.requireText("BORR_FST_NM", " "),
        QuarantineReason.MISSING_REQUIRED, "BORR_FST_NM");
  }

  @Test
  void parseDateIsStrictMonthDayYear() {
    assertThat(LegacyValueParser.parseDate("PMT_DT", "12/15/2025"))
        .isEqualTo(LocalDate.of(2025, 12, 15));
    assertThat(LegacyValueParser.parseDate("PMT_DT", "")).isNull();
    assertRejected(() -> LegacyValueParser.parseDate("PMT_DT", "13/40/2025"),
        QuarantineReason.MALFORMED_VALUE, "PMT_DT");
    assertRejected(() -> LegacyValueParser.parseDate("PMT_DT", "02/30/2025"),
        QuarantineReason.MALFORMED_VALUE, "PMT_DT");
    assertRejected(() -> LegacyValueParser.parseDate("PMT_DT", "2025-12-15"),
        QuarantineReason.MALFORMED_VALUE, "PMT_DT");
    assertRejected(() -> LegacyValueParser.requireDate("PMT_DT", null),
        QuarantineReason.MISSING_REQUIRED, "PMT_DT");
  }

  @Test
  void requireTimestampWidensToMidnight() {
    assertThat(LegacyValueParser.requireTimestamp("LN_CRET_DT", "02/01/2019"))
        .isEqualTo(LocalDateTime.of(2019, 2, 1, 0, 0));
  }

  @Test
  void parseAmountStripsFormattingAndKeepsBlankAsNull() {
    assertThat(LegacyValueParser.parseAmount("LN_CURR_BAL", "271,432.56"))
        .isEqualTo(new BigDecimal("271432.56"));
    assertThat(LegacyValueParser.parseAmount("LN_ORIG_AMT", "$285,000"))
        .isEqualTo(new BigDecimal("285000.00"));
    assertThat(LegacyValueParser.parseAmount("PMT_AMT", "10.005"))
        .isEqualTo(new BigDecimal("10.01"));
    assertThat(LegacyValueParser.parseAmount("LN_CURR_BAL", " ")).isNull();
    assertRejected(() -> LegacyValueParser.parseAmount("LN_CURR_BAL", "abc"),
        QuarantineReason.MALFORMED_VALUE, "LN_CURR_BAL");
    assertRejected(() -> LegacyValueParser.requireAmount("LN_CURR_BAL", ""),
        QuarantineReason.MISSING_REQUIRED, "LN_CURR_BAL");
  }

  @Test
  void parseDecimalScalesToTargetColumn() {
    assertThat(LegacyValueParser.parseDecimal("LN_INT_RT", "4.75", 3))
        .isEqualTo(new BigDecimal("4.750"));
    assertThat(LegacyValueParser.parseDecimal("LN_LTV_PCT", "82.5", 2))
        .isEqualTo(new BigDecimal("82.50"));
    assertRejected(() -> LegacyValueParser.parseDecimal("LN_INT_RT", "4,75%", 3),
        QuarantineReason.MALFORMED_VALUE, "LN_INT_RT");
  }

  @Test
  void parseIntegerAndShort() {
    assertThat(LegacyValueParser.parseInteger("LN_DLQ_DAYS", "1,015")).isEqualTo(1015);
    assertThat(LegacyValueParser.parseShort("PROD_TERM_MOS", "360")).isEqualTo((short) 360);
    assertThat(LegacyValueParser.parseInteger("LN_DLQ_DAYS", "")).isNull();
    assertRejected(() -> LegacyValueParser.parseInteger("BORR_CRDT_SCR", "7x5"),
        QuarantineReason.MALFORMED_VALUE, "BORR_CRDT_SCR");
    assertRejected(() -> LegacyValueParser.parseShort("PROD_TERM_MOS", "40000"),
        QuarantineReason.MALFORMED_VALUE, "PROD_TERM_MOS");
  }

  @Test
  void normaliseCodeUpperCasesAndRenames() {
    assertThat(LegacyValueParser.normaliseCode("LN_STAT_CD", " act ")).isEqualTo("ACT");
    assertThat(LegacyValueParser.normaliseCode("BORR_EMP_STAT", "SELF-EMP"))
        .isEqualTo("SELF_EMPLOYED");
    assertRejected(() -> LegacyValueParser.normaliseCode("LN_STAT_CD", null),
        QuarantineReason.MISSING_REQUIRED, "LN_STAT_CD");
  }

  @Test
  void activeFlagAndExpirySentinel() {
    assertThat(LegacyValueParser.activeFlag("ACT")).isTrue();
    assertThat(LegacyValueParser.activeFlag("INA")).isFalse();
    assertThat(LegacyValueParser.expirySentinelToNull(LocalDate.of(2099, 12, 31))).isNull();
    assertThat(LegacyValueParser.expirySentinelToNull(LocalDate.of(2030, 6, 30)))
        .isEqualTo(LocalDate.of(2030, 6, 30));
  }

  @Test
  void paymentSplitMustReconcileToTotal() {
    PaymentSplitValidator.validate(
        new BigDecimal("2924.18"),
        new BigDecimal("1876.54"),
        new BigDecimal("814.78"),
        new BigDecimal("232.86"));

    assertThatThrownBy(
            () ->
                PaymentSplitValidator.validate(
                    new BigDecimal("1487.02"),
                    new BigDecimal("456.78"),
                    new BigDecimal("1074.69"),
                    new BigDecimal("355.55")))
        .isInstanceOfSatisfying(
            RowRejectedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(QuarantineReason.PAYMENT_SPLIT_MISMATCH);
              assertThat(e.getField()).isEqualTo("PMT_AMT");
              assertThat(e.getMessage()).contains("1487.02", "1887.02", "-400.00");
            });
  }

  private static void assertRejected(
      Runnable parse, QuarantineReason expectedReason, String expectedField) {
    assertThatThrownBy(parse::run)
        .isInstanceOfSatisfying(
            RowRejectedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(expectedReason);
              assertThat(e.getField()).isEqualTo(expectedField);
            });
  }
}
