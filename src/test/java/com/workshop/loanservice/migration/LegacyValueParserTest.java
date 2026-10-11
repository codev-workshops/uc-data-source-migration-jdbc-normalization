package com.workshop.loanservice.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LegacyValueParserTest {

  @Test
  void parsesLegacyDates() {
    assertEquals(LocalDate.of(1978, 3, 15), LegacyValueParser.parseDate("C", "03/15/1978"));
    assertEquals(LocalDateTime.of(2025, 12, 1, 0, 0),
        LegacyValueParser.parseTimestamp("C", " 12/01/2025 "));
  }

  @Test
  void parsesAmountsWithThousandsSeparators() {
    assertEquals(new BigDecimal("1500000"), LegacyValueParser.parseAmount("C", "1,500,000"));
    assertEquals(new BigDecimal("1487.02"), LegacyValueParser.parseAmount("C", "1,487.02"));
  }

  @Test
  void parsesDecimalsAndIntegers() {
    assertEquals(new BigDecimal("4.750"), LegacyValueParser.parseDecimal("C", "4.750"));
    assertEquals(360, LegacyValueParser.parseInteger("C", " 360 "));
  }

  @Test
  void nullOrBlankBecomesNull() {
    assertNull(LegacyValueParser.parseDate("C", null));
    assertNull(LegacyValueParser.parseDate("C", "  "));
    assertNull(LegacyValueParser.parseAmount("C", ""));
    assertNull(LegacyValueParser.parseDecimal("C", null));
    assertNull(LegacyValueParser.parseInteger("C", " "));
  }

  @Test
  void malformedValuesAreRejectedWithColumnName() {
    LegacyDataException e = assertThrows(LegacyDataException.class,
        () -> LegacyValueParser.parseDate("BORR_DOB_DT", "1978-03-15"));
    assertEquals("BORR_DOB_DT has malformed value '1978-03-15'", e.getMessage());
    assertThrows(LegacyDataException.class, () -> LegacyValueParser.parseDate("C", "02/30/2020"));
    assertThrows(LegacyDataException.class, () -> LegacyValueParser.parseAmount("C", "12a.00"));
    assertThrows(LegacyDataException.class, () -> LegacyValueParser.parseDecimal("C", "1,5"));
    assertThrows(LegacyDataException.class, () -> LegacyValueParser.parseInteger("C", "7.5"));
  }
}
