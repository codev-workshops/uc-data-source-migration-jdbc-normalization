package com.workshop.loanservice.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the startup migration against the seeded legacy data. */
@SpringBootTest
@Transactional
class DataMigrationServiceTest {

  @Autowired
  private DataMigrationService migrationService;
  @Autowired
  private MigrationReconciliationService reconciliationService;
  @Autowired
  private BorrowerRepository borrowerRepository;
  @Autowired
  private LoanProductRepository loanProductRepository;
  @Autowired
  private LoanAccountRepository loanAccountRepository;
  @Autowired
  private PaymentRepository paymentRepository;

  @Test
  void migratesExpectedRowCounts() {
    assertEquals(5, borrowerRepository.count());
    assertEquals(5, loanProductRepository.count());
    assertEquals(5, loanAccountRepository.count());
    assertEquals(10, paymentRepository.count());
  }

  @Test
  void reconciliationPasses() {
    ReconciliationReport report = reconciliationService.reconcile();
    assertTrue(report.passed(), report::summary);
  }

  @Test
  void rerunningMigrationSkipsAlreadyMigratedRows() {
    MigrationReport report = migrationService.migrate();

    for (TableMigrationResult table : report.tables()) {
      assertEquals(0, table.getMigrated(), table::toString);
      assertEquals(table.getSourceRows(), table.getSkippedDuplicates(), table::toString);
      assertTrue(table.getRejections().isEmpty(), table::toString);
    }
    assertEquals(10, paymentRepository.count());
  }

  @Test
  void storesTypedBorrowerValues() {
    Borrower borrower = borrowerRepository.findByExternalId("B-10001").orElseThrow();

    assertEquals(LocalDate.of(1978, 3, 15), borrower.getDateOfBirth());
    assertEquals(745, borrower.getCreditScore());
    assertEquals(0, new BigDecimal("92500").compareTo(borrower.getAnnualIncome()));
    assertEquals("ACTIVE", borrower.getStatus());
  }

  @Test
  void resolvesLoanAccountForeignKeys() {
    LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2019-00142")
        .orElseThrow();

    assertEquals("B-10001", account.getBorrower().getExternalId());
    assertEquals("FXD30", account.getProduct().getCode());
    assertEquals(0, new BigDecimal("285000").compareTo(account.getOriginalAmount()));
    assertEquals(new BigDecimal("4.750"), account.getInterestRate());
    assertEquals("Single Family", account.getPropertyType());
  }

  @Test
  void resolvesPaymentForeignKeys() {
    Payment payment = paymentRepository.findByExternalId("PMT-2025110003").orElseThrow();

    assertEquals("LN-2018-00089", payment.getLoanAccount().getAccountNumber());
    assertEquals(new BigDecimal("47.50"), payment.getLateFee());
    assertEquals("REGULAR", payment.getType());
    assertEquals("POSTED", payment.getStatus());
  }
}
