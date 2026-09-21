package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.LoanProduct;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.service.migration.MigrationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationServiceTransformTest {

    @Test
    void transformsBorrower() {
        LegacyBorrower src = new LegacyBorrower();
        src.setBorrowerId("B-10001");
        src.setFirstName("James");
        src.setLastName("Mitchell");
        src.setMiddleInitial("R");
        src.setSsnEncrypted("ENC_XXX_001");
        src.setDateOfBirth("03/15/1978");
        src.setAddressLine2("");
        src.setCreditScore("745");
        src.setAnnualIncome("92,500");
        src.setCreatedDate("01/15/2019");
        src.setUpdatedDate("11/03/2025");
        src.setStatusCode("ACT");
        src.setRecordType("PRI");

        Borrower b = MigrationService.toBorrower(src);

        assertThat(b.getExternalId()).isEqualTo("B-10001");
        assertThat(b.getDateOfBirth()).isEqualTo(LocalDate.of(1978, 3, 15));
        assertThat(b.getAddressLine2()).isNull();
        assertThat(b.getCreditScore()).isEqualTo(745);
        assertThat(b.getAnnualIncome()).isEqualByComparingTo("92500");
        assertThat(b.getStatus()).isEqualTo("ACTIVE");
        assertThat(b.getCreatedAt()).isEqualTo(LocalDateTime.of(2019, 1, 15, 0, 0));
        assertThat(b.getUpdatedAt()).isEqualTo(LocalDateTime.of(2025, 11, 3, 0, 0));
    }

    @Test
    void borrowerWithMalformedIncomeFailsWithRecordAndField() {
        LegacyBorrower src = new LegacyBorrower();
        src.setBorrowerId("B-99");
        src.setFirstName("X");
        src.setLastName("Y");
        src.setAnnualIncome("ninety");
        src.setStatusCode("ACT");

        assertThatThrownBy(() -> MigrationService.toBorrower(src))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("B-99")
                .hasMessageContaining("BORR_ANN_INCM");
    }

    @Test
    void transformsLoanProduct() {
        LegacyLoanProduct src = new LegacyLoanProduct();
        src.setProductCode("FXD30");
        src.setDescription("30-Year Fixed Rate Mortgage");
        src.setTypeCode("FXD");
        src.setTermMonths("360");
        src.setRateType("FIXED");
        src.setMinAmount("50,000");
        src.setMaxAmount("1,500,000");
        src.setStatusCode("INA");
        src.setEffectiveDate("01/01/2020");
        src.setExpirationDate("12/31/2099");

        LoanProduct p = MigrationService.toLoanProduct(src);

        assertThat(p.getCode()).isEqualTo("FXD30");
        assertThat(p.getTermMonths()).isEqualTo(360);
        assertThat(p.getMinAmount()).isEqualByComparingTo("50000");
        assertThat(p.getMaxAmount()).isEqualByComparingTo("1500000");
        assertThat(p.getIsActive()).isFalse();
        assertThat(p.getExpirationDate()).isEqualTo(LocalDate.of(2099, 12, 31));
    }

    @Test
    void transformsLoanAccountAndResolvesReferences() {
        Borrower borrower = new Borrower();
        LoanProduct product = new LoanProduct();
        LegacyLoanAccount src = new LegacyLoanAccount();
        src.setLoanAccountNumber("LN-2019-00142");
        src.setBorrowerId("B-10001");
        src.setProductCode("FXD30");
        src.setOriginalAmount("285,000");
        src.setCurrentBalance("271,432.56");
        src.setInterestRate("4.750");
        src.setTermMonths("360");
        src.setMonthlyPayment("1,487.02");
        src.setOriginationDate("02/15/2019");
        src.setMaturityDate("02/15/2049");
        src.setFirstPaymentDate("03/15/2019");
        src.setNextPaymentDate("01/15/2026");
        src.setStatusCode("FRB");
        src.setDelinquencyDays("15");
        src.setEscrowBalance("3,245.80");
        src.setLtvPercent("82.5");
        src.setPropertyType("SFR");
        src.setAppraisedValue("345,000");
        src.setCreatedDate("02/01/2019");
        src.setUpdatedDate("12/01/2025");

        LoanAccount a = MigrationService.toLoanAccount(src, borrower, product);

        assertThat(a.getBorrower()).isSameAs(borrower);
        assertThat(a.getProduct()).isSameAs(product);
        assertThat(a.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(a.getCurrentBalance()).isEqualByComparingTo("271432.56");
        assertThat(a.getInterestRate()).isEqualByComparingTo("4.750");
        assertThat(a.getMonthlyPayment()).isEqualByComparingTo("1487.02");
        assertThat(a.getStatus()).isEqualTo("FORBEARANCE");
        assertThat(a.getDelinquencyDays()).isEqualTo(15);
        assertThat(a.getEscrowBalance()).isEqualByComparingTo("3245.80");
        assertThat(a.getLtvPercent()).isEqualByComparingTo("82.5");
        assertThat(a.getPropertyType()).isEqualTo("SINGLE_FAMILY");
        assertThat(a.getMaturityDate()).isEqualTo(LocalDate.of(2049, 2, 15));
    }

    @Test
    void loanAccountWithUnknownStatusFailsLoud() {
        LegacyLoanAccount src = new LegacyLoanAccount();
        src.setLoanAccountNumber("LN-X");
        src.setOriginalAmount("1");
        src.setCurrentBalance("1");
        src.setInterestRate("1");
        src.setTermMonths("1");
        src.setMonthlyPayment("1");
        src.setOriginationDate("01/01/2020");
        src.setMaturityDate("01/01/2021");
        src.setStatusCode("ZZZ");

        assertThatThrownBy(() -> MigrationService.toLoanAccount(src, new Borrower(), new LoanProduct()))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("LN-X")
                .hasMessageContaining("LN_STAT_CD");
    }

    @Test
    void transformsPayment() {
        LoanAccount account = new LoanAccount();
        LegacyPayment src = new LegacyPayment();
        src.setPaymentSequenceNumber("PMT-2025110003");
        src.setLoanAccountNumber("LN-2018-00089");
        src.setPaymentDate("11/01/2025");
        src.setTotalAmount("1,077.05");
        src.setPrincipalAmount("295.82");
        src.setInterestAmount("781.23");
        src.setEscrowAmount("0.00");
        src.setLateFee("47.50");
        src.setTypeCode("REG");
        src.setStatusCode("PST");
        src.setReceivedDate("11/18/2025");
        src.setProcessedDate("11/19/2025");
        src.setCreatedDate("11/19/2025");
        src.setUpdatedDate("11/19/2025");

        Payment p = MigrationService.toPayment(src, account);

        assertThat(p.getLoanAccount()).isSameAs(account);
        assertThat(p.getLegacyPaymentId()).isEqualTo("PMT-2025110003");
        assertThat(p.getPaymentDate()).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(p.getTotalAmount()).isEqualByComparingTo("1077.05");
        assertThat(p.getLateFee()).isEqualByComparingTo("47.50");
        assertThat(p.getType()).isEqualTo("REGULAR");
        assertThat(p.getStatus()).isEqualTo("POSTED");
        assertThat(p.getCreatedAt()).isEqualTo(LocalDateTime.of(2025, 11, 19, 0, 0));
    }

    @Test
    void paymentWithMissingAmountIsNotDefaultedToZero() {
        LegacyPayment src = new LegacyPayment();
        src.setPaymentSequenceNumber("PMT-X");
        src.setPaymentDate("11/01/2025");
        src.setTotalAmount(null);
        src.setTypeCode("REG");
        src.setStatusCode("PST");

        assertThatThrownBy(() -> MigrationService.toPayment(src, new LoanAccount()))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("PMT-X")
                .hasMessageContaining("PMT_AMT");
    }
}
