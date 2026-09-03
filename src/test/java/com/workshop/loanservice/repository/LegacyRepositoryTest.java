package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyPayment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterisation tests for the custom Spring Data finders on the legacy repositories, run
 * against the legacy seed data in H2.
 *
 * <p>In particular pins the fact that
 * {@link LegacyPaymentRepository#findByLoanAccountNumberOrderByPaymentDateDesc(String)} sorts
 * the all-VARCHAR {@code PMT_DT} column as a <b>string</b> ({@code MM/DD/YYYY}), which is not
 * chronological order once payments span different years.
 *
 * <p>Uses its own in-memory H2 database name so the legacy schema script can run independently
 * of any other Spring context cached in the same JVM.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:repolegacydw;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Transactional
class LegacyRepositoryTest {

    @Autowired
    private LegacyLoanAccountRepository loanAccountRepository;

    @Autowired
    private LegacyPaymentRepository paymentRepository;

    // ---------------------------------------------------------------------------------------
    // findByBorrowerId
    // ---------------------------------------------------------------------------------------

    @Test
    void findByBorrowerIdReturnsLoansForSeededBorrower() {
        List<LegacyLoanAccount> loans = loanAccountRepository.findByBorrowerId("B-10001");
        assertThat(loans).extracting(LegacyLoanAccount::getLoanAccountNumber)
                .containsExactly("LN-2019-00142");
        assertThat(loans.get(0).getBorrowerFirstName()).isEqualTo("James");
        assertThat(loans.get(0).getOriginalAmount()).isEqualTo("285,000");
    }

    @Test
    void findByBorrowerIdReturnsMultipleLoansWhenBorrowerHasSeveral() {
        LegacyLoanAccount extra = new LegacyLoanAccount();
        extra.setLoanAccountNumber("LN-TEST-00002");
        extra.setBorrowerId("B-10001");
        extra.setProductCode("FXD15");
        loanAccountRepository.saveAndFlush(extra);

        assertThat(loanAccountRepository.findByBorrowerId("B-10001"))
                .extracting(LegacyLoanAccount::getLoanAccountNumber)
                .containsExactlyInAnyOrder("LN-2019-00142", "LN-TEST-00002");
    }

    @Test
    void findByBorrowerIdIsExactCaseSensitiveMatch() {
        assertThat(loanAccountRepository.findByBorrowerId("b-10001")).isEmpty();
        assertThat(loanAccountRepository.findByBorrowerId("B-1000")).isEmpty();
    }

    @Test
    void findByBorrowerIdUnknownReturnsEmptyList() {
        assertThat(loanAccountRepository.findByBorrowerId("UNKNOWN")).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // findByLoanAccountNumberOrderByPaymentDateDesc
    // ---------------------------------------------------------------------------------------

    @Test
    void findPaymentsForSeededLoanOrdersByDateDescending() {
        List<LegacyPayment> payments =
                paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc("LN-2019-00142");
        assertThat(payments).extracting(LegacyPayment::getPaymentSequenceNumber)
                .containsExactly("PMT-2025120001", "PMT-2025110001");
        assertThat(payments).extracting(LegacyPayment::getPaymentDate)
                .containsExactly("12/15/2025", "11/15/2025");
    }

    @Test
    void findPaymentsUnknownLoanReturnsEmptyList() {
        assertThat(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc("UNKNOWN")).isEmpty();
    }

    @Test
    void findPaymentsDoesNotLeakOtherLoansPayments() {
        assertThat(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc("LN-2020-00398"))
                .extracting(LegacyPayment::getLoanAccountNumber)
                .containsOnly("LN-2020-00398");
    }

    /**
     * KNOWN BUG (MIGRATION_ANALYSIS.md §4): {@code PMT_DT} is a VARCHAR holding
     * {@code MM/DD/YYYY}, so {@code ORDER BY PMT_DT DESC} is a lexicographic sort. A payment made
     * in December 2023 sorts <i>before</i> one made in January 2025 because {@code "12" > "01"}.
     * This test documents the current (chronologically wrong) order.
     */
    @Test
    void findPaymentsOrdersByStringNotChronologically() {
        String loan = "LN-TEST-00003";
        paymentRepository.saveAndFlush(payment("PMT-T-JAN2025", loan, "01/05/2025"));
        paymentRepository.saveAndFlush(payment("PMT-T-DEC2023", loan, "12/20/2023"));
        paymentRepository.saveAndFlush(payment("PMT-T-JUN2024", loan, "06/15/2024"));
        paymentRepository.saveAndFlush(payment("PMT-T-JAN2024", loan, "01/05/2024"));

        List<LegacyPayment> payments = paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc(loan);

        assertThat(payments).extracting(LegacyPayment::getPaymentDate)
                .containsExactly("12/20/2023", "06/15/2024", "01/05/2025", "01/05/2024");
        assertThat(payments).extracting(LegacyPayment::getPaymentSequenceNumber)
                .containsExactly("PMT-T-DEC2023", "PMT-T-JUN2024", "PMT-T-JAN2025", "PMT-T-JAN2024");
    }

    private static LegacyPayment payment(String id, String loan, String date) {
        LegacyPayment p = new LegacyPayment();
        p.setPaymentSequenceNumber(id);
        p.setLoanAccountNumber(loan);
        p.setPaymentDate(date);
        p.setTotalAmount("1.00");
        p.setTypeCode("REG");
        p.setStatusCode("PST");
        return p;
    }
}
