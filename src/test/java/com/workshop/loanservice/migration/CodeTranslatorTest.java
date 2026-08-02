package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CodeTranslatorTest {

    private final CodeTranslator codes = new CodeTranslator();

    @ParameterizedTest
    @CsvSource({"ACT,ACTIVE", "CLO,CLOSED", "DFT,DEFAULT", "FRB,FORBEARANCE"})
    void translatesLoanStatus(String legacy, String modern) {
        assertThat(codes.loanStatus(legacy)).isEqualTo(modern);
    }

    @ParameterizedTest
    @CsvSource({"SFR,SINGLE_FAMILY", "CND,CONDOMINIUM", "MFR,MULTI_FAMILY", "TWN,TOWNHOUSE"})
    void translatesPropertyType(String legacy, String modern) {
        assertThat(codes.propertyType(legacy)).isEqualTo(modern);
    }

    @ParameterizedTest
    @CsvSource({"REG,REGULAR", "EXT,EXTRA", "PRT,PARTIAL", "PRE,PREPAYMENT"})
    void translatesPaymentType(String legacy, String modern) {
        assertThat(codes.paymentType(legacy)).isEqualTo(modern);
    }

    @ParameterizedTest
    @CsvSource({"PST,POSTED", "REV,REVERSED", "NSF,NSF", "PND,PENDING"})
    void translatesPaymentStatus(String legacy, String modern) {
        assertThat(codes.paymentStatus(legacy)).isEqualTo(modern);
    }

    @Test
    void productIsActiveOnlyForAct() {
        assertThat(codes.productActive("ACT")).isTrue();
        assertThat(codes.productActive("INA")).isFalse();
        assertThat(codes.productActive(null)).isFalse();
    }

    /**
     * The v1 contract exposes labels, not codes, so every code must survive the round trip back to
     * exactly the wording the legacy service produced.
     */
    @ParameterizedTest
    @CsvSource({"ACT,Active", "CLO,Closed", "DFT,Default", "FRB,Forbearance"})
    void loanStatusRoundTripsToTheV1Label(String legacy, String v1Label) {
        assertThat(codes.loanStatusLabel(codes.loanStatus(legacy))).isEqualTo(v1Label);
    }

    @ParameterizedTest
    @CsvSource({"SFR,Single Family Residence", "CND,Condominium", "MFR,Multi-Family Residence", "TWN,Townhouse"})
    void propertyTypeRoundTripsToTheV1Label(String legacy, String v1Label) {
        assertThat(codes.propertyTypeLabel(codes.propertyType(legacy))).isEqualTo(v1Label);
    }

    @ParameterizedTest
    @CsvSource({"PST,Posted", "REV,Reversed", "NSF,Non-Sufficient Funds", "PND,Pending"})
    void paymentStatusRoundTripsToTheV1Label(String legacy, String v1Label) {
        assertThat(codes.paymentStatusLabel(codes.paymentStatus(legacy))).isEqualTo(v1Label);
    }

    @Test
    void unknownCodePassesThroughJustLikeTheLegacyService() {
        assertThat(codes.loanStatus("ZZZ")).isEqualTo("ZZZ");
        assertThat(codes.loanStatusLabel("ZZZ")).isEqualTo("ZZZ");
    }

    @Test
    void nullBecomesUnknownAndRendersAsTheLegacyWording() {
        assertThat(codes.loanStatus(null)).isEqualTo("UNKNOWN");
        assertThat(codes.loanStatusLabel(codes.loanStatus(null))).isEqualTo("Unknown");
        assertThat(codes.paymentTypeLabel(codes.paymentType(null))).isEqualTo("Unknown");
    }
}
