package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyPayment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LegacyPaymentRepositoryTest {

    @Autowired
    private LegacyPaymentRepository repository;

    @Test
    void findByLoanAccountNumber_returnsAllPaymentsForLoan() {
        List<LegacyPayment> payments = repository.findByLoanAccountNumber("LN-2019-00142");

        assertThat(payments).extracting(LegacyPayment::getPaymentSequenceNumber)
                .containsExactlyInAnyOrder("PMT-2025120001", "PMT-2025110001");
    }

    @Test
    void findByLoanAccountNumberOrderByPaymentDateDesc_returnsDescending() {
        List<LegacyPayment> payments =
                repository.findByLoanAccountNumberOrderByPaymentDateDesc("LN-2019-00142");

        assertThat(payments).extracting(LegacyPayment::getPaymentDate)
                .containsExactly("12/15/2025", "11/15/2025");
        assertThat(payments).extracting(LegacyPayment::getPaymentSequenceNumber)
                .containsExactly("PMT-2025120001", "PMT-2025110001");
    }

    @Test
    void findByLoanAccountNumber_unknownLoan_returnsEmpty() {
        assertThat(repository.findByLoanAccountNumber("LN-DOES-NOT-EXIST")).isEmpty();
    }
}
