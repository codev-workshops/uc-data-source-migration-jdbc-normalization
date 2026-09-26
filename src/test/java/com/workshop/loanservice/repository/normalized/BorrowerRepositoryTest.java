package com.workshop.loanservice.repository.normalized;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.entity.normalized.Borrower;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/** Runs against the Flyway-migrated normalized {@code borrower} table. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("repo-test")
class BorrowerRepositoryTest {

  @Autowired private BorrowerRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void findByExternalIdReturnsMigratedRowWithGeneratedIdAndTypedColumns() {
    Borrower borrower = repository.findByExternalId("B-10001").orElseThrow();

    assertThat(borrower.getId()).isNotNull();
    assertThat(borrower.getCreditScore()).isEqualTo(745);
    assertThat(borrower.getAnnualIncome()).isEqualByComparingTo("92500.00");
    assertThat(borrower.getMiddleInitial()).isEqualTo("R");
    assertThat(borrower.getStatus()).isEqualTo("ACTIVE");
    assertThat(borrower.getCreatedAt()).isEqualTo(LocalDateTime.of(2019, 1, 15, 0, 0));
    assertThat(repository.findByExternalId("B-99999")).isEmpty();
  }

  @Test
  void findByStatusMatchesExpandedValues() {
    assertThat(repository.findByStatus("ACTIVE")).hasSize(5);
    assertThat(repository.findByStatus("ACT")).isEmpty();
  }

  @Test
  void findByLastNameIgnoreCaseMatchesRegardlessOfCase() {
    assertThat(repository.findByLastNameIgnoreCase("mitchell"))
        .extracting(Borrower::getExternalId)
        .containsExactly("B-10001");
  }

  @Test
  void saveGeneratesSurrogateId() {
    Borrower borrower = new Borrower();
    borrower.setExternalId("B-TEST-0001");
    borrower.setFirstName("Test");
    borrower.setLastName("Borrower");
    borrower.setCreditScore(700);
    borrower.setStatus("ACTIVE");

    Borrower saved = entityManager.persistFlushFind(borrower);

    assertThat(saved.getId()).isNotNull();
    assertThat(repository.findByExternalId("B-TEST-0001")).map(Borrower::getId).contains(saved.getId());
  }
}
