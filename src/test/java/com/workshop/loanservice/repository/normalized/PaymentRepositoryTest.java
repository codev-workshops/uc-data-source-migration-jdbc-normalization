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
  @Autowired private LoanAccountRepository loanAccountRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void findByLoanAccountAccountNumberReturnsMigratedPayments() {
    assertThat(repository.findByLoanAccountAccountNumber(LOAN_ID))
        .extracting(Payment::getExternalId)
        .containsExactlyInAnyOrder("PMT-2025120001", "PMT-2025110001");
    assertThat(repository.findByLoanAccountAccountNumber("LN-NOPE")).isEmpty();
  }

  @Test
  void migratedPaymentCarriesGeneratedIdAndExpandedValues() {
    Payment payment = repository.findByExternalId("PMT-2025120001").orElseThrow();

    assertThat(payment.getId()).isNotNull();
    assertThat(payment.getType()).isEqualTo("REGULAR");
    assertThat(payment.getStatus()).isEqualTo("POSTED");
    assertThat(payment.getTotalAmount()).isEqualByComparingTo("1487.02");
    assertThat(payment.getPaymentDate()).isEqualTo(LocalDate.of(2025, 12, 15));
    assertThat(payment.getLoanAccount().getAccountNumber()).isEqualTo(LOAN_ID);
  }

  @Test
  void orderByPaymentDateDescIsChronologicalOnTypedDates() {
    LoanAccount loan = loanAccountRepository.findByAccountNumber(LOAN_ID).orElseThrow();
    entityManager.persistAndFlush(seedPayment("PMT-TEST-0001", loan, LocalDate.of(2026, 2, 15)));

    List<Payment> payments =
        repository.findByLoanAccountAccountNumberOrderByPaymentDateDesc(LOAN_ID);

    assertThat(payments)
        .extracting(Payment::getPaymentDate)
        .containsExactly(
            LocalDate.of(2026, 2, 15), LocalDate.of(2025, 12, 15), LocalDate.of(2025, 11, 15));
    assertThat(repository.findByLoanAccountIdOrderByPaymentDateDesc(loan.getId()))
        .hasSize(3);
  }

  private static Payment seedPayment(String externalId, LoanAccount loan, LocalDate date) {
    Payment pmt = new Payment();
    pmt.setExternalId(externalId);
    pmt.setLoanAccount(loan);
    pmt.setPaymentDate(date);
    pmt.setTotalAmount(new BigDecimal("1487.02"));
    pmt.setPrincipalAmount(new BigDecimal("460.00"));
    pmt.setInterestAmount(new BigDecimal("1027.02"));
    pmt.setEscrowAmount(BigDecimal.ZERO);
    pmt.setLateFee(BigDecimal.ZERO);
    pmt.setType("REGULAR");
    pmt.setStatus("POSTED");
    return pmt;
  }
}
