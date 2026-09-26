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
  @Autowired private BorrowerRepository borrowerRepository;
  @Autowired private LoanProductRepository productRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void findByBorrowerExternalIdResolvesThroughAssociation() {
    Borrower borrower = borrowerRepository.findByExternalId("B-10001").orElseThrow();
    LoanProduct product = productRepository.findByCode("FXD15").orElseThrow();
    entityManager.persistAndFlush(seedLoan("LN-TEST-00001", borrower, product, "CLOSED"));

    assertThat(repository.findByBorrowerExternalId("B-10001"))
        .extracting(LoanAccount::getAccountNumber)
        .containsExactlyInAnyOrder("LN-2019-00142", "LN-TEST-00001");
    assertThat(repository.findByBorrowerId(borrower.getId())).hasSize(2);
    assertThat(repository.findByBorrowerExternalId("B-99999")).isEmpty();
  }

  @Test
  void findByStatusMatchesExpandedValues() {
    assertThat(repository.findByStatus("ACTIVE")).hasSize(5);
    assertThat(repository.findByStatus("ACT")).isEmpty();
  }

  @Test
  void findByProductCodeResolvesThroughAssociation() {
    assertThat(repository.findByProductCode("FXD30"))
        .extracting(LoanAccount::getAccountNumber)
        .containsExactlyInAnyOrder("LN-2019-00142", "LN-2021-00567");
    assertThat(repository.findByProductCode("NOPE")).isEmpty();
  }

  @Test
  void findByAccountNumberCarriesTypedColumnsAndAssociations() {
    LoanAccount loan = repository.findByAccountNumber("LN-2019-00142").orElseThrow();

    assertThat(loan.getId()).isNotNull();
    assertThat(loan.getOriginalAmount()).isEqualByComparingTo("285000.00");
    assertThat(loan.getInterestRate()).isEqualByComparingTo("4.750");
    assertThat(loan.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
    assertThat(loan.getStatus()).isEqualTo("ACTIVE");
    assertThat(loan.getPropertyType()).isEqualTo("Single Family");
    assertThat(loan.getDelinquencyDays()).isZero();
    assertThat(loan.getBorrower().getExternalId()).isEqualTo("B-10001");
    assertThat(loan.getBorrower().getLastName()).isEqualTo("Mitchell");
    assertThat(loan.getProduct().getCode()).isEqualTo("FXD30");
    assertThat(loan.getProduct().getName()).isEqualTo("30-Year Fixed Rate Mortgage");
    assertThat(loan.getProduct().getIsActive()).isTrue();
    assertThat(repository.findByAccountNumber("LN-NOPE")).isEmpty();
  }

  private static LoanAccount seedLoan(
      String number, Borrower borrower, LoanProduct product, String status) {
    LoanAccount acct = new LoanAccount();
    acct.setAccountNumber(number);
    acct.setBorrower(borrower);
    acct.setProduct(product);
    acct.setOriginalAmount(new BigDecimal("100000.00"));
    acct.setCurrentBalance(new BigDecimal("90000.00"));
    acct.setInterestRate(new BigDecimal("5.000"));
    acct.setMonthlyPayment(new BigDecimal("500.00"));
    acct.setOriginationDate(LocalDate.of(2024, 1, 1));
    acct.setStatus(status);
    acct.setDelinquencyDays(0);
    acct.setPropertyType("Single Family");
    return acct;
  }
}
