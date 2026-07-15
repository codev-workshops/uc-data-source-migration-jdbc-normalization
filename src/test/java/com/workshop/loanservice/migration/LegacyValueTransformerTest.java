package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyValueTransformerTest {

    private final LegacyValueTransformer transformer = new LegacyValueTransformer();

    @Test
    void parsesLegacyDatesAndNumbers() {
        assertThat(transformer.requiredDate("table", "id", "date", "12/15/2025"))
                .isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(transformer.requiredDecimal("table", "id", "amount", "1,487.02"))
                .isEqualByComparingTo(new BigDecimal("1487.02"));
        assertThat(transformer.requiredInteger("table", "id", "count", "360"))
                .isEqualTo(360);
    }

    @Test
    void expandsKnownCodesToModernValues() {
        assertThat(transformer.expandBorrowerStatus("table", "id", "status", "ACT"))
                .isEqualTo("ACTIVE");
        assertThat(transformer.expandActiveFlag("table", "id", "status", "INA"))
                .isFalse();
        assertThat(transformer.expandLoanStatus("table", "id", "status", "FRB"))
                .isEqualTo("FORBEARANCE");
        assertThat(transformer.expandPropertyType("table", "id", "type", "SFR"))
                .isEqualTo("Single Family Residence");
        assertThat(transformer.expandPaymentType("table", "id", "type", "PRE"))
                .isEqualTo("PREPAYMENT");
        assertThat(transformer.expandPaymentStatus("table", "id", "status", "PST"))
                .isEqualTo("POSTED");
    }

    @Test
    void rejectsMalformedRequiredValuesAndUnknownCodes() {
        assertThatThrownBy(() -> transformer.requiredDate(
                "table",
                "id",
                "date",
                "2025-12-15"
        )).isInstanceOf(LegacyDataMigrationException.class);

        assertThatThrownBy(() -> transformer.requiredDecimal(
                "table",
                "id",
                "amount",
                "not-number"
        )).isInstanceOf(LegacyDataMigrationException.class);

        assertThatThrownBy(() -> transformer.expandPaymentType(
                "table",
                "id",
                "type",
                "BAD"
        )).isInstanceOf(LegacyDataMigrationException.class);
    }
}
