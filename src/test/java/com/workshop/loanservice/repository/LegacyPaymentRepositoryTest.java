package com.workshop.loanservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.entity.LegacyPayment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/** Runs against the Flyway-migrated legacy CDW_PMT_HIST table and its V3 seed rows. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("repo-test")
class LegacyPaymentRepositoryTest {

  private static final String LOAN_ID = "LN-2019-00142";

  @Autowired private LegacyPaymentRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void findByLoanAccountNumberReturnsSeededPayments() {
    assertThat(repository.findByLoanAccountNumber(LOAN_ID))
        .extracting(LegacyPayment::getPaymentSequenceNumber)
        .containsExactlyInAnyOrder("PMT-2025120001", "PMT-2025110001");
    assertThat(repository.findByLoanAccountNumber("LN-NOPE")).isEmpty();
  }

  @Test
  void orderByPaymentDateDescIsLexicographicOnLegacyStringDates() {
    // Chronologically the newest payment, but "02/..." sorts before "11/..." and "12/..." as text.
    entityManager.persistAndFlush(seedPayment("PMT-TEST-0001", "02/15/2026"));

    List<LegacyPayment> payments = repository.findByLoanAccountNumberOrderByPaymentDateDesc(LOAN_ID);

    assertThat(payments)
        .extracting(LegacyPayment::getPaymentDate)
        .containsExactly("12/15/2025", "11/15/2025", "02/15/2026");
  }

  private static LegacyPayment seedPayment(String sequence, String date) {
    LegacyPayment pmt = new LegacyPayment();
    pmt.setPaymentSequenceNumber(sequence);
    pmt.setLoanAccountNumber(LOAN_ID);
    pmt.setPaymentDate(date);
    pmt.setTotalAmount("1,487.02");
    pmt.setPrincipalAmount("460.00");
    pmt.setInterestAmount("1,027.02");
    pmt.setEscrowAmount("0.00");
    pmt.setLateFee("0.00");
    pmt.setTypeCode("REG");
    pmt.setStatusCode("PST");
    return pmt;
  }
}
