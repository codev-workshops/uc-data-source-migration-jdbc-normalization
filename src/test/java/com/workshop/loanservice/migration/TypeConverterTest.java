package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

/** Unit tests for the strict legacy -> modern conversion utility. */
class TypeConverterTest {

    private final TypeConverter convert = new TypeConverter();

    @Test
    void parsesLegacyDate() {
        assertThat(convert.parseDate("origination_date", "02/15/2019"))
                .isEqualTo(LocalDate.of(2019, 2, 15));
    }

    @Test
    void parsesTimestampAtStartOfDay() {
        assertThat(convert.parseTimestamp("created_at", "01/15/2019"))
                .isEqualTo(LocalDateTime.of(2019, 1, 15, 0, 0));
    }

    @Test
    void blankInputsConvertToNull() {
        assertThat(convert.parseDate("d", null)).isNull();
        assertThat(convert.parseDate("d", "  ")).isNull();
        assertThat(convert.parseDecimal("a", "")).isNull();
        assertThat(convert.parseInteger("i", null)).isNull();
    }

    @Test
    void parsesDecimalStrippingThousandsSeparators() {
        assertThat(convert.parseDecimal("original_amount", "285,000")).isEqualByComparingTo("285000");
        assertThat(convert.parseDecimal("current_balance", "271,432.56")).isEqualByComparingTo("271432.56");
        // scale is preserved for downstream contract fidelity
        assertThat(convert.parseDecimal("interest_rate", "4.250").scale()).isEqualTo(3);
    }

    @Test
    void parsesInteger() {
        assertThat(convert.parseInteger("credit_score", "745")).isEqualTo(745);
    }

    @Test
    void expandsCodes() {
        assertThat(convert.loanStatus("status", "ACT")).isEqualTo("ACTIVE");
        assertThat(convert.paymentType("type", "REG")).isEqualTo("REGULAR");
        assertThat(convert.paymentStatus("status", "NSF")).isEqualTo("NSF");
        assertThat(convert.propertyType("property_type", "SFR")).isEqualTo("Single Family Residence");
        assertThat(convert.productActive("is_active", "ACT")).isTrue();
    }

    @Test
    void invalidDateRaisesContextualError() {
        assertThatThrownBy(() -> convert.parseDate("origination_date", "13/40/2019"))
                .asInstanceOf(type(ConversionException.class))
                .satisfies(e -> {
                    assertThat(e.getField()).isEqualTo("origination_date");
                    assertThat(e.getInvalidValue()).isEqualTo("13/40/2019");
                });
    }

    @Test
    void invalidDecimalRaisesContextualError() {
        assertThatThrownBy(() -> convert.parseDecimal("original_amount", "abc"))
                .asInstanceOf(type(ConversionException.class))
                .satisfies(e -> assertThat(e.getField()).isEqualTo("original_amount"));
    }

    @Test
    void unknownCodeRaisesContextualError() {
        assertThatThrownBy(() -> convert.loanStatus("status", "ZZZ"))
                .asInstanceOf(type(ConversionException.class))
                .satisfies(e -> assertThat(e.getInvalidValue()).isEqualTo("ZZZ"));
    }
}
