package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The migration has already run at startup ({@code MigrationRunner}), so these
 * assertions cover its result plus what a rerun does.
 */
@SpringBootTest
class MigrationServiceTests {

    @Autowired private MigrationService migrationService;
    @Autowired private BorrowerRepository borrowers;
    @Autowired private LoanProductRepository products;
    @Autowired private LoanAccountRepository loans;
    @Autowired private PaymentRepository payments;

    @Test
    void migratesEveryLegacyRow() {
        assertThat(borrowers.count()).isEqualTo(5);
        assertThat(products.count()).isEqualTo(5);
        assertThat(loans.count()).isEqualTo(5);
        assertThat(payments.count()).isEqualTo(10);
    }

    @Test
    void convertsTypesAndResolvesForeignKeys() {
        LoanAccount loan = loans.findByAccountNumber("LN-2019-00142").orElseThrow();

        assertThat(loan.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(loan.getProduct().getCode()).isEqualTo("FXD30");
        assertThat(loan.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("285000"));
        assertThat(loan.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("271432.56"));
        assertThat(loan.getInterestRate()).isEqualByComparingTo(new BigDecimal("4.750"));
        assertThat(loan.getTermMonths()).isEqualTo(360);
        assertThat(loan.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(loan.getStatus()).isEqualTo("ACTIVE");
        assertThat(loan.getPropertyType()).isEqualTo("Single Family Residence");
    }

    @Test
    void keepsLegacyPaymentIdAsBusinessKey() {
        Payment payment = payments
                .findByLoanAccountAccountNumberOrderByPaymentDateDescExternalIdDesc("LN-2019-00142")
                .get(0);

        assertThat(payment.getExternalId()).isEqualTo("PMT-2025120001");
        assertThat(payment.getLoanAccount().getAccountNumber()).isEqualTo("LN-2019-00142");
        assertThat(payment.getType()).isEqualTo("REGULAR");
        assertThat(payment.getStatus()).isEqualTo("POSTED");
    }

    @Test
    void rerunningIsIdempotent() {
        MigrationReport report = migrationService.migrate();

        assertThat(report.isClean()).isTrue();
        assertThat(report.getTables().values())
                .allSatisfy(table -> assertThat(table.getMigrated()).isZero());
        assertThat(report.getTables().get("payments").getSkippedExisting()).isEqualTo(10);
        assertThat(payments.count()).isEqualTo(10);
    }
}
