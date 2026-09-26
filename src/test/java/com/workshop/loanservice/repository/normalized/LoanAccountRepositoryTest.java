package com.workshop.loanservice.repository.normalized;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.entity.normalized.Borrower;
import com.workshop.loanservice.entity.normalized.LoanAccount;
import com.workshop.loanservice.entity.normalized.LoanProduct;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs against the Flyway-migrated normalized {@code loan_account} table, populated from the legacy
 * seed rows by V4 and constrained by V5.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("repo-test")
class LoanAccountRepositoryTest {

  @Autowired private LoanAccountRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void findByBorrowerBorrowerIdResolvesThroughAssociation() {
    Borrower borrower = entityManager.find(Borrower.class, "B-10001");
    LoanProduct product = entityManager.find(LoanProduct.class, "FXD15");
    entityManager.persistAndFlush(seedLoan("LN-TEST-00001", borrower, product, "CLO"));

    assertThat(repository.findByBorrowerBorrowerId("B-10001"))
        .extracting(LoanAccount::getLoanAccountNumber)
        .containsExactlyInAnyOrder("LN-2019-00142", "LN-TEST-00001");
    assertThat(repository.findByBorrowerBorrowerId("B-99999")).isEmpty();
  }

  @Test
  void findByStatusCodeMatchesMigratedCodes() {
    assertThat(repository.findByStatusCode("ACT")).hasSize(5);
    assertThat(repository.findByStatusCode("CLO")).isEmpty();
  }

  @Test
  void findByLoanProductProductCodeResolvesThroughAssociation() {
    assertThat(repository.findByLoanProductProductCode("FXD30"))
        .extracting(LoanAccount::getLoanAccountNumber)
        .containsExactlyInAnyOrder("LN-2019-00142", "LN-2021-00567");
    assertThat(repository.findByLoanProductProductCode("NOPE")).isEmpty();
  }

  @Test
  void migratedRowsCarryTypedColumnsAndEagerAssociations() {
    LoanAccount loan = repository.findById("LN-2019-00142").orElseThrow();

    assertThat(loan.getOriginalAmount()).isEqualByComparingTo("285000.00");
    assertThat(loan.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
    assertThat(loan.getBorrower().getLastName()).isEqualTo("Mitchell");
    assertThat(loan.getLoanProduct().getDescription()).isEqualTo("30-Year Fixed Rate Mortgage");
    assertThat(loan.getProductCode()).isEqualTo("FXD30");
  }

  private static LoanAccount seedLoan(
      String number, Borrower borrower, LoanProduct product, String status) {
    LoanAccount acct = new LoanAccount();
    acct.setLoanAccountNumber(number);
    acct.setBorrower(borrower);
    acct.setLoanProduct(product);
    acct.setOriginalAmount(new BigDecimal("100000.00"));
    acct.setCurrentBalance(new BigDecimal("90000.00"));
    acct.setInterestRate(new BigDecimal("5.000"));
    acct.setMonthlyPayment(new BigDecimal("500.00"));
    acct.setOriginationDate(LocalDate.of(2024, 1, 1));
    acct.setStatusCode(status);
    acct.setPropertyType("SFR");
    return acct;
  }
}
