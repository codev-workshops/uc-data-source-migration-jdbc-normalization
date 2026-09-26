package com.workshop.loanservice.repository.normalized;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.entity.normalized.LoanAccount;
import com.workshop.loanservice.entity.normalized.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/** Runs against the Flyway-migrated normalized {@code payment} table. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("repo-test")
class PaymentRepositoryTest {

  private static final String LOAN_ID = "LN-2019-00142";

  @Autowired private PaymentRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void findByLoanAccountLoanAccountNumberReturnsMigratedPayments() {
    assertThat(repository.findByLoanAccountLoanAccountNumber(LOAN_ID))
        .extracting(Payment::getPaymentId)
        .containsExactlyInAnyOrder("PMT-2025120001", "PMT-2025110001");
    assertThat(repository.findByLoanAccountLoanAccountNumber("LN-NOPE")).isEmpty();
  }

  @Test
  void orderByPaymentDateDescIsChronologicalOnTypedDates() {
    LoanAccount loan = entityManager.find(LoanAccount.class, LOAN_ID);
    entityManager.persistAndFlush(seedPayment("PMT-TEST-0001", loan, LocalDate.of(2026, 2, 15)));

    List<Payment> payments =
        repository.findByLoanAccountLoanAccountNumberOrderByPaymentDateDesc(LOAN_ID);

    assertThat(payments)
        .extracting(Payment::getPaymentDate)
        .containsExactly(
            LocalDate.of(2026, 2, 15), LocalDate.of(2025, 12, 15), LocalDate.of(2025, 11, 15));
  }

  private static Payment seedPayment(String id, LoanAccount loan, LocalDate date) {
    Payment pmt = new Payment();
    pmt.setPaymentId(id);
    pmt.setLoanAccount(loan);
    pmt.setPaymentDate(date);
    pmt.setTotalAmount(new BigDecimal("1487.02"));
    pmt.setPrincipalAmount(new BigDecimal("460.00"));
    pmt.setInterestAmount(new BigDecimal("1027.02"));
    pmt.setEscrowAmount(BigDecimal.ZERO);
    pmt.setLateFee(BigDecimal.ZERO);
    pmt.setTypeCode("REG");
    pmt.setStatusCode("PST");
    return pmt;
  }
}
