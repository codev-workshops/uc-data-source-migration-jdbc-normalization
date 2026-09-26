package com.workshop.loanservice.repository.normalized;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.entity.normalized.Borrower;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/** Runs against the Flyway-migrated normalized {@code borrower} table. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("repo-test")
class BorrowerRepositoryTest {

  @Autowired private BorrowerRepository repository;

  @Test
  void findByIdReturnsTypedCreditScore() {
    Borrower borrower = repository.findById("B-10001").orElseThrow();

    assertThat(borrower.getCreditScore()).isEqualTo(745);
    assertThat(borrower.getMiddleInitial()).isEqualTo("R");
  }

  @Test
  void findByStatusCodeReturnsMigratedBorrowers() {
    assertThat(repository.findByStatusCode("ACT")).hasSize(5);
    assertThat(repository.findByStatusCode("CLO")).isEmpty();
  }

  @Test
  void findByLastNameIgnoreCaseMatchesRegardlessOfCase() {
    assertThat(repository.findByLastNameIgnoreCase("mitchell"))
        .extracting(Borrower::getBorrowerId)
        .containsExactly("B-10001");
  }
}
