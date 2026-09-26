package com.workshop.loanservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.entity.LegacyBorrower;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/** Runs against the Flyway-migrated legacy CDW_BORR_MSTR table and its V3 seed rows. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("repo-test")
class LegacyBorrowerRepositoryTest {

  @Autowired private LegacyBorrowerRepository repository;

  @Test
  void findByIdReturnsStringTypedCreditScore() {
    LegacyBorrower borrower = repository.findById("B-10001").orElseThrow();

    assertThat(borrower.getCreditScore()).isEqualTo("745");
    assertThat(borrower.getMiddleInitial()).isEqualTo("R");
  }

  @Test
  void findByStatusCodeReturnsSeededBorrowers() {
    assertThat(repository.findByStatusCode("ACT")).hasSize(5);
    assertThat(repository.findByStatusCode("CLO")).isEmpty();
  }

  @Test
  void findByLastNameIgnoreCaseMatchesRegardlessOfCase() {
    assertThat(repository.findByLastNameIgnoreCase("MITCHELL"))
        .extracting(LegacyBorrower::getBorrowerId)
        .containsExactly("B-10001");
  }
}
