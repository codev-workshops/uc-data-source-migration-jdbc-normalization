package com.workshop.loanservice.service.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyCodeMapperTest {

    @Test
    void expandsBorrowerStatus() {
        assertThat(LegacyCodeMapper.borrowerStatus("ACT", "B-1")).isEqualTo("ACTIVE");
        assertThat(LegacyCodeMapper.borrowerStatus("INA", "B-1")).isEqualTo("INACTIVE");
        assertThat(LegacyCodeMapper.borrowerStatus(" act ", "B-1")).isEqualTo("ACTIVE");
    }

    @Test
    void expandsLoanStatus() {
        assertThat(LegacyCodeMapper.loanStatus("ACT", "LN-1")).isEqualTo("ACTIVE");
        assertThat(LegacyCodeMapper.loanStatus("CLO", "LN-1")).isEqualTo("CLOSED");
        assertThat(LegacyCodeMapper.loanStatus("DFT", "LN-1")).isEqualTo("DEFAULT");
        assertThat(LegacyCodeMapper.loanStatus("FRB", "LN-1")).isEqualTo("FORBEARANCE");
    }

    @Test
    void mapsProductStatusToBoolean() {
        assertThat(LegacyCodeMapper.productActive("ACT", "FXD30")).isTrue();
        assertThat(LegacyCodeMapper.productActive("INA", "FXD30")).isFalse();
    }

    @Test
    void expandsPaymentTypeAndStatus() {
        assertThat(LegacyCodeMapper.paymentType("REG", "P-1")).isEqualTo("REGULAR");
        assertThat(LegacyCodeMapper.paymentType("EXT", "P-1")).isEqualTo("EXTRA");
        assertThat(LegacyCodeMapper.paymentType("PRT", "P-1")).isEqualTo("PARTIAL");
        assertThat(LegacyCodeMapper.paymentType("PRE", "P-1")).isEqualTo("PREPAYMENT");
        assertThat(LegacyCodeMapper.paymentStatus("PST", "P-1")).isEqualTo("POSTED");
        assertThat(LegacyCodeMapper.paymentStatus("REV", "P-1")).isEqualTo("REVERSED");
        assertThat(LegacyCodeMapper.paymentStatus("NSF", "P-1")).isEqualTo("NSF");
        assertThat(LegacyCodeMapper.paymentStatus("PND", "P-1")).isEqualTo("PENDING");
    }

    @Test
    void expandsPropertyType() {
        assertThat(LegacyCodeMapper.propertyType("SFR", "LN-1")).isEqualTo("SINGLE_FAMILY");
        assertThat(LegacyCodeMapper.propertyType("CND", "LN-1")).isEqualTo("CONDOMINIUM");
        assertThat(LegacyCodeMapper.propertyType("MFR", "LN-1")).isEqualTo("MULTI_FAMILY");
        assertThat(LegacyCodeMapper.propertyType("TWN", "LN-1")).isEqualTo("TOWNHOUSE");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "XXX", "ACTIVE", "1"})
    void unknownOrBlankCodesFailLoud(String code) {
        assertThatThrownBy(() -> LegacyCodeMapper.borrowerStatus(code, "B-9"))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("B-9").hasMessageContaining("BORR_STAT_CD");
        assertThatThrownBy(() -> LegacyCodeMapper.loanStatus(code, "LN-9"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("LN_STAT_CD");
        assertThatThrownBy(() -> LegacyCodeMapper.productActive(code, "P-9"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("PROD_STAT_CD");
        assertThatThrownBy(() -> LegacyCodeMapper.paymentType(code, "PMT-9"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("PMT_TYP_CD");
        assertThatThrownBy(() -> LegacyCodeMapper.paymentStatus(code, "PMT-9"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("PMT_STAT_CD");
        assertThatThrownBy(() -> LegacyCodeMapper.propertyType(code, "LN-9"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("PROP_TYP_CD");
    }
}
