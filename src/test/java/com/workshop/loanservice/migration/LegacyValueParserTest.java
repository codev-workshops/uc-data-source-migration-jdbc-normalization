package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.workshop.loanservice.migration.LegacyValueParser.DecimalSpec;
import com.workshop.loanservice.modern.entity.BorrowerStatus;
import com.workshop.loanservice.modern.entity.LoanStatus;
import com.workshop.loanservice.modern.entity.PaymentStatus;
import com.workshop.loanservice.modern.entity.PaymentType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    private static final DecimalSpec DECIMAL_10_2 = new DecimalSpec(10, 2);

    @Test
    void sanitizesWhitespaceAndControlCharacters() {
        assertThat(LegacyValueParser.clean("  James\t ")).isEqualTo("James");
        assertThat(LegacyValueParser.clean("\u00A0Zoe\u0000\u200B")).isEqualTo("Zoe");
        assertThat(LegacyValueParser.clean("742 Elm\nStreet")).isEqualTo("742 Elm Street");
        assertThat(LegacyValueParser.clean("\u0000\t \r\n")).isNull();
        assertThat(LegacyValueParser.parseDate("C", " 02/15/2019\r\n")).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(LegacyValueParser.parseAmount("C", "\t1,487.02\u0000")).isEqualByComparingTo("1487.02");
    }

    @Test
    void parsesCurrencyAndNegativeAmounts() {
        assertThat(LegacyValueParser.parseAmount("C", "$1,234.56")).isEqualByComparingTo("1234.56");
        assertThat(LegacyValueParser.parseAmount("C", "(123.45)")).isEqualByComparingTo("-123.45");
        assertThat(LegacyValueParser.parseAmount("C", "($1,000.00)")).isEqualByComparingTo("-1000.00");
        assertThat(LegacyValueParser.parseAmount("C", "-$5.00")).isEqualByComparingTo("-5.00");
        assertThat(LegacyValueParser.parseAmount("C", "$-5.00")).isEqualByComparingTo("-5.00");
        assertThat(LegacyValueParser.parseAmount("C", ".50")).isEqualByComparingTo("0.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1E5", "1.5e3", "1,23,4", "12,34", "1,234.5.6", "$", "()", "(-5.00)", "-$-5",
            "(12.50", "12.50)", "1 000", "USD 10", "--5", "+5", "NaN", "Infinity", "0x1F", "\u0661\u0662"})
    void rejectsMalformedAmounts(String raw) {
        assertThatThrownBy(() -> LegacyValueParser.parseAmount("PMT_AMT", raw))
                .isInstanceOf(MalformedLegacyDataException.class)
                .hasMessageContaining("PMT_AMT");
    }

    @Test
    void enforcesColumnPrecisionAndScale() {
        assertThat(LegacyValueParser.parseAmount("C", "99,999,999.99", DECIMAL_10_2)).isEqualByComparingTo("99999999.99");
        assertThat(LegacyValueParser.parseAmount("C", "1.230", DECIMAL_10_2)).isEqualByComparingTo("1.23");
        assertThat(LegacyValueParser.parseAmount("C", "1,500,000", DECIMAL_10_2)).isEqualByComparingTo("1500000");
        assertThat(LegacyValueParser.parseDecimal("C", "4.750", new DecimalSpec(5, 3))).isEqualByComparingTo("4.75");

        assertThatThrownBy(() -> LegacyValueParser.parseAmount("C", "123,456,789.00", DECIMAL_10_2))
                .isInstanceOf(MalformedLegacyDataException.class).hasMessageContaining("too large for DECIMAL(10, 2)");
        assertThatThrownBy(() -> LegacyValueParser.parseAmount("C", "1.234", DECIMAL_10_2))
                .isInstanceOf(MalformedLegacyDataException.class).hasMessageContaining("more than 2 decimal places");
        assertThatThrownBy(() -> LegacyValueParser.parseDecimal("C", "100.000", new DecimalSpec(5, 3)))
                .isInstanceOf(MalformedLegacyDataException.class).hasMessageContaining("too large");
    }

    @ParameterizedTest
    @ValueSource(strings = {"02/29/2023", "02/30/1990", "04/31/2020", "13/01/2020", "00/10/2020", "2/5/2020",
            "02/15/19", "02/15/20190", "2019-02-15", "02-15-2019", "02/15/2019 10:00"})
    void rejectsInvalidDates(String raw) {
        assertThatThrownBy(() -> LegacyValueParser.parseDate("PMT_DT", raw))
                .isInstanceOf(MalformedLegacyDataException.class)
                .hasMessageContaining("not a valid MM/DD/YYYY date");
    }

    @Test
    void acceptsLeapDayInLeapYear() {
        assertThat(LegacyValueParser.parseDate("C", "02/29/2024")).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(LegacyValueParser.parseDate("C", "")).isNull();
        assertThat(LegacyValueParser.parseDate("C", " \t ")).isNull();
    }

    @Test
    void rejectsMalformedIntegersAndDecimals() {
        assertThat(LegacyValueParser.parseInteger("C", " 0745 ")).isEqualTo(745);
        assertThatThrownBy(() -> LegacyValueParser.parseInteger("C", "99999999999"))
                .isInstanceOf(MalformedLegacyDataException.class).hasMessageContaining("outside the integer range");
        assertThatThrownBy(() -> LegacyValueParser.parseInteger("C", "1,000"))
                .isInstanceOf(MalformedLegacyDataException.class);
        assertThatThrownBy(() -> LegacyValueParser.parseDecimal("C", "4.75%"))
                .isInstanceOf(MalformedLegacyDataException.class);
        assertThatThrownBy(() -> LegacyValueParser.parseDecimal("C", "4.7E0"))
                .isInstanceOf(MalformedLegacyDataException.class);
    }

    @Test
    void escapesControlCharactersInErrorMessages() {
        assertThatThrownBy(() -> LegacyValueParser.parseInteger("C", "12\n[WARN] forged"))
                .hasMessage("C='12\\u000A[WARN] forged': not an integer");
    }

    @Test
    void mapsCodesCaseInsensitivelyAfterTrimming() {
        assertThat(LegacyCodeMappings.paymentStatus(" nsf ")).isEqualTo(PaymentStatus.NSF);
        assertThat(LegacyCodeMappings.paymentType("reg\t")).isEqualTo(PaymentType.REGULAR);
        assertThat(LegacyCodeMappings.loanStatus("Frb")).isEqualTo(LoanStatus.FORBEARANCE);
        assertThat(LegacyCodeMappings.propertyType(" sfr ")).isEqualTo("Single Family Residence");
        assertThat(LegacyCodeMappings.productActive("ina")).isFalse();
    }

    @Test
    void acceptsModernNamesAsAliases() {
        assertThat(LegacyCodeMappings.borrowerStatus("active  ")).isEqualTo(BorrowerStatus.ACTIVE);
        assertThat(LegacyCodeMappings.loanStatus(" Default")).isEqualTo(LoanStatus.DEFAULT);
        assertThat(LegacyCodeMappings.paymentStatus("posted")).isEqualTo(PaymentStatus.POSTED);
        assertThat(LegacyCodeMappings.productActive("Active")).isTrue();
    }

    @Test
    void blankCodesAreMissing() {
        assertThatThrownBy(() -> LegacyCodeMappings.borrowerStatus(" \t "))
                .isInstanceOf(MalformedLegacyDataException.class).hasMessageContaining("missing code");
        assertThat(LegacyCodeMappings.propertyType("  ")).isNull();
    }
}
