package com.workshop.loanservice.service.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @CsvSource({"ACT, ACTIVE", "INA, INACTIVE", "act, ACTIVE", "' ina ', INACTIVE"})
    void borrowerStatusTableIsExhaustiveAndCaseInsensitive(String code, String expected) {
        assertThat(LegacyCodeMapper.borrowerStatus(code, "B-1")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLO", "DFT", "FRB", "PST", "REG", "SFR", "A", "ACTI"})
    void borrowerStatusRejectsCodesFromOtherDomains(String code) {
        assertThatThrownBy(() -> LegacyCodeMapper.borrowerStatus(code, "B-1"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("unknown code");
    }

    @ParameterizedTest
    @CsvSource({"ACT, ACTIVE", "CLO, CLOSED", "DFT, DEFAULT", "FRB, FORBEARANCE", "frb, FORBEARANCE"})
    void loanStatusTableIsExhaustive(String code, String expected) {
        assertThat(LegacyCodeMapper.loanStatus(code, "LN-1")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INA", "PST", "REG", "SFR", "DEF", "CLOSED", "FOR"})
    void loanStatusRejectsCodesFromOtherDomains(String code) {
        assertThatThrownBy(() -> LegacyCodeMapper.loanStatus(code, "LN-1"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("unknown code");
    }

    @ParameterizedTest
    @CsvSource({"ACT, true", "INA, false", "act, true", "' INA', false"})
    void productStatusBooleanTableIsExhaustive(String code, boolean expected) {
        assertThat(LegacyCodeMapper.productActive(code, "FXD30")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", "FALSE", "Y", "N", "1", "0", "CLO", "ACTIVE"})
    void productStatusNeverGuessesABooleanFromNonCodes(String code) {
        assertThatThrownBy(() -> LegacyCodeMapper.productActive(code, "FXD30"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("unknown code");
    }

    @ParameterizedTest
    @CsvSource({"REG, REGULAR", "EXT, EXTRA", "PRT, PARTIAL", "PRE, PREPAYMENT", "reg, REGULAR"})
    void paymentTypeTableIsExhaustive(String code, String expected) {
        assertThat(LegacyCodeMapper.paymentType(code, "PMT-1")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PST", "REV", "NSF", "PND", "ACT", "REGULAR", "PR"})
    void paymentTypeRejectsPaymentStatusAndOtherCodes(String code) {
        assertThatThrownBy(() -> LegacyCodeMapper.paymentType(code, "PMT-1"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("unknown code");
    }

    @ParameterizedTest
    @CsvSource({"PST, POSTED", "REV, REVERSED", "NSF, NSF", "PND, PENDING", "pst, POSTED"})
    void paymentStatusTableIsExhaustive(String code, String expected) {
        assertThat(LegacyCodeMapper.paymentStatus(code, "PMT-1")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REG", "EXT", "PRT", "PRE", "ACT", "POSTED", "PEN"})
    void paymentStatusRejectsPaymentTypeAndOtherCodes(String code) {
        assertThatThrownBy(() -> LegacyCodeMapper.paymentStatus(code, "PMT-1"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("unknown code");
    }

    @ParameterizedTest
    @CsvSource({"SFR, SINGLE_FAMILY", "CND, CONDOMINIUM", "MFR, MULTI_FAMILY", "TWN, TOWNHOUSE", "sfr, SINGLE_FAMILY"})
    void propertyTypeTableIsExhaustive(String code, String expected) {
        assertThat(LegacyCodeMapper.propertyType(code, "LN-1")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONDO", "SF", "TH", "ACT", "REG", "SINGLE_FAMILY"})
    void propertyTypeRejectsUnknownCodes(String code) {
        assertThatThrownBy(() -> LegacyCodeMapper.propertyType(code, "LN-1"))
                .isInstanceOf(MigrationException.class).hasMessageContaining("unknown code");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankCodesReportNullOrBlankNotUnknown(String code) {
        assertThatThrownBy(() -> LegacyCodeMapper.paymentType(code, "PMT-1"))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("null or blank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "XXX", "ACTIVE", "1", "A C T", "ACT;"})
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
