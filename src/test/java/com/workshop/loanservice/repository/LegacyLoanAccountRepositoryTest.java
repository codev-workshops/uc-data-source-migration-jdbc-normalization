package com.workshop.loanservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/** Runs against the Flyway-migrated legacy CDW_LN_ACCT table and its V3 seed rows. */
/**
 * @deprecated Exercises the deprecated legacy CDW_* data path; superseded by the normalized test
 *     suites and {@link com.workshop.loanservice.service.NormalizedLoanService}.
 */
@Deprecated
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("repo-test")
class LegacyLoanAccountRepositoryTest {

  @Autowired private LegacyLoanAccountRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void findByBorrowerIdReturnsOnlyThatBorrowersLoans() {
    LegacyLoanAccount extra = seedLoan("LN-TEST-00001", "B-10001", "FXD15", "CLO");
    entityManager.persistAndFlush(extra);

    List<LegacyLoanAccount> loans = repository.findByBorrowerId("B-10001");

    assertThat(loans)
        .extracting(LegacyLoanAccount::getLoanAccountNumber)
        .containsExactlyInAnyOrder("LN-2019-00142", "LN-TEST-00001");
  }

  @Test
  void findByBorrowerIdReturnsEmptyForUnknownBorrower() {
    assertThat(repository.findByBorrowerId("B-99999")).isEmpty();
  }

  @Test
  void findByStatusCodeMatchesRawLegacyCode() {
    entityManager.persistAndFlush(seedLoan("LN-TEST-00002", "B-10002", "FXD30", "CLO"));

    assertThat(repository.findByStatusCode("ACT")).hasSize(5);
    assertThat(repository.findByStatusCode("CLO"))
        .extracting(LegacyLoanAccount::getLoanAccountNumber)
        .containsExactly("LN-TEST-00002");
  }

  @Test
  void findByProductCodeReturnsAllLoansOnProduct() {
    assertThat(repository.findByProductCode("FXD30"))
        .extracting(LegacyLoanAccount::getLoanAccountNumber)
        .containsExactlyInAnyOrder("LN-2019-00142", "LN-2021-00567");
    assertThat(repository.findByProductCode("NOPE")).isEmpty();
  }

  private static LegacyLoanAccount seedLoan(
      String number, String borrowerId, String productCode, String status) {
    LegacyLoanAccount acct = new LegacyLoanAccount();
    acct.setLoanAccountNumber(number);
    acct.setBorrowerId(borrowerId);
    acct.setBorrowerFirstName("Test");
    acct.setBorrowerLastName("Borrower");
    acct.setProductCode(productCode);
    acct.setOriginalAmount("100,000");
    acct.setCurrentBalance("90,000");
    acct.setInterestRate("5.000");
    acct.setMonthlyPayment("500.00");
    acct.setOriginationDate("01/01/2024");
    acct.setStatusCode(status);
    acct.setPropertyType("SFR");
    return acct;
  }
}
