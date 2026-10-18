package com.workshop.loanservice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workshop.loanservice.entity.modern.BorrowerStatus;
import com.workshop.loanservice.entity.modern.LoanStatus;
import com.workshop.loanservice.entity.modern.PaymentStatus;
import com.workshop.loanservice.entity.modern.PaymentType;
import org.junit.jupiter.api.Test;

class LegacyCodeMapperTest {

    @Test
    void mapsActiveInactiveCodes() {
        assertThat(LegacyCodeMapper.borrowerStatus("ACT")).isEqualTo(BorrowerStatus.ACTIVE);
        assertThat(LegacyCodeMapper.borrowerStatus("INA")).isEqualTo(BorrowerStatus.INACTIVE);
        assertThat(LegacyCodeMapper.productActive("ACT")).isTrue();
        assertThat(LegacyCodeMapper.productActive("INA")).isFalse();
    }

    @Test
    void expandsLoanAndPropertyCodes() {
        assertThat(LegacyCodeMapper.loanStatus("ACT")).isEqualTo(LoanStatus.ACTIVE);
        assertThat(LegacyCodeMapper.loanStatus("CLO")).isEqualTo(LoanStatus.CLOSED);
        assertThat(LegacyCodeMapper.loanStatus("DFT")).isEqualTo(LoanStatus.DEFAULT);
        assertThat(LegacyCodeMapper.loanStatus("FRB")).isEqualTo(LoanStatus.FORBEARANCE);
        assertThat(LegacyCodeMapper.loanStatus(null)).isNull();
        assertThat(LegacyCodeMapper.propertyType("SFR")).isEqualTo("Single Family Residence");
        assertThat(LegacyCodeMapper.propertyType("CND")).isEqualTo("Condominium");
        assertThat(LegacyCodeMapper.propertyType("MFR")).isEqualTo("Multi-Family Residence");
        assertThat(LegacyCodeMapper.propertyType("TWN")).isEqualTo("Townhouse");
    }

    @Test
    void expandsPaymentCodes() {
        assertThat(LegacyCodeMapper.paymentType("REG")).isEqualTo(PaymentType.REGULAR);
        assertThat(LegacyCodeMapper.paymentType("PRE")).isEqualTo(PaymentType.PREPAYMENT);
        assertThat(LegacyCodeMapper.paymentStatus("PST")).isEqualTo(PaymentStatus.POSTED);
        assertThat(LegacyCodeMapper.paymentStatus("NSF").getDisplayName()).isEqualTo("Non-Sufficient Funds");
    }

    @Test
    void rejectsUnknownOrMissingRequiredCodes() {
        assertThatThrownBy(() -> LegacyCodeMapper.loanStatus("XYZ")).isInstanceOf(LegacyDataException.class);
        assertThatThrownBy(() -> LegacyCodeMapper.paymentType(null)).isInstanceOf(LegacyDataException.class);
        assertThatThrownBy(() -> LegacyCodeMapper.paymentStatus("???")).isInstanceOf(LegacyDataException.class);
    }
}
