package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyValueParserTest {

    @Test
    void parsesLegacyDates() {
        assertEquals(LocalDate.of(2019, 2, 15), LegacyValueParser.date("02/15/2019"));
        assertNull(LegacyValueParser.date("   "));
        assertNull(LegacyValueParser.date(null));
        assertThrows(MigrationDataException.class, () -> LegacyValueParser.date("2019-02-15"));
    }

    @Test
    void parsesAmountsWithThousandSeparators() {
        assertEquals(0, new BigDecimal("285000").compareTo(LegacyValueParser.amount("285,000")));
        assertEquals(0, new BigDecimal("1487.02").compareTo(LegacyValueParser.amount("1,487.02")));
        assertNull(LegacyValueParser.amount(""));
        assertThrows(MigrationDataException.class, () -> LegacyValueParser.amount("N/A"));
    }

    @Test
    void parsesIntegers() {
        assertEquals(360, LegacyValueParser.integer("360"));
        assertNull(LegacyValueParser.integer(null));
        assertThrows(MigrationDataException.class, () -> LegacyValueParser.integer("3.5"));
    }

    @Test
    void expandsCodes() {
        assertEquals("ACTIVE", LegacyValueParser.loanStatus("ACT"));
        assertEquals("FORBEARANCE", LegacyValueParser.loanStatus("FRB"));
        assertEquals("INACTIVE", LegacyValueParser.borrowerStatus("INA"));
        assertEquals("Condominium", LegacyValueParser.propertyType("CND"));
        assertEquals("PREPAYMENT", LegacyValueParser.paymentType("PRE"));
        assertEquals("NSF", LegacyValueParser.paymentStatus("NSF"));
        assertEquals("XXX", LegacyValueParser.loanStatus("XXX"), "unknown codes pass through unchanged");
        assertNull(LegacyValueParser.loanStatus(null));
    }

    @Test
    void mapsActiveFlag() {
        assertEquals(Boolean.TRUE, LegacyValueParser.activeFlag("ACT"));
        assertEquals(Boolean.FALSE, LegacyValueParser.activeFlag("INA"));
        assertNull(LegacyValueParser.activeFlag(" "));
    }
}
