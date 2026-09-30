package com.workshop.loanservice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.workshop.loanservice.migration.MigrationReport.QuarantinedRow;
import com.workshop.loanservice.migration.MigrationReport.TableCounts;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.MigrationQuarantine;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.entity.PaymentStatus;
import com.workshop.loanservice.modern.entity.PaymentType;
import com.workshop.loanservice.modern.entity.QuarantineReason;
import com.workshop.loanservice.modern.repository.AddressRepository;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.MigrationQuarantineRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.modern.repository.PropertyRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs the migrator against the real legacy seed and the modern schema in one H2 database. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.jpa.show-sql=false",
      "spring.sql.init.schema-locations="
          + "classpath:schema-legacy.sql,classpath:schema-modern.sql",
      "spring.sql.init.data-locations="
          + "classpath:data-legacy.sql,classpath:data-modern-reference.sql"
    })
@AutoConfigureTestDatabase
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LegacyToModernMigratorTest {

  private static final String SPLIT_ANOMALY_LOAN = "LN-2019-00142";
  // 5 mailing addresses + 2 property addresses: B-10001 and B-10004 have a line2 ("Apt 3B",
  // "Suite 12") that CDW_LN_ACCT has no column for, so exact five-field dedup keeps them apart.
  private static final long EXPECTED_ADDRESSES = 7;

  @Autowired private LegacyToModernMigrator migrator;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private AddressRepository addressRepository;
  @Autowired private BorrowerRepository borrowerRepository;
  @Autowired private LoanProductRepository loanProductRepository;
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private LoanAccountRepository loanAccountRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private MigrationQuarantineRepository quarantineRepository;

  @Test
  void seedLoadQuarantinesOnlyTheTwoSplitAnomalies() {
    MigrationReport report = migrator.migrate();

    assertThat(report.counts(LegacyToModernMigrator.BORROWER_TABLE))
        .isEqualTo(new TableCounts(5, 5, 0));
    assertThat(report.counts(LegacyToModernMigrator.PRODUCT_TABLE))
        .isEqualTo(new TableCounts(5, 5, 0));
    assertThat(report.counts(LegacyToModernMigrator.LOAN_TABLE))
        .isEqualTo(new TableCounts(5, 5, 0));
    assertThat(report.counts(LegacyToModernMigrator.PAYMENT_TABLE))
        .isEqualTo(new TableCounts(10, 8, 2));
    assertThat(report.nameDivergences()).isEmpty();

    assertThat(report.quarantinedRows())
        .extracting(QuarantinedRow::sourceKey, QuarantinedRow::reason, QuarantinedRow::field)
        .containsExactlyInAnyOrder(
            tuple("PMT-2025120001", QuarantineReason.PAYMENT_SPLIT_MISMATCH, "PMT_AMT"),
            tuple("PMT-2025110001", QuarantineReason.PAYMENT_SPLIT_MISMATCH, "PMT_AMT"));
    assertThat(report.quarantinedRows())
        .allSatisfy(row -> assertThat(row.detail()).contains("1487.02", "1887.02", "-400.00"));

    assertThat(quarantineRepository.findAll())
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row.getSourceTable()).isEqualTo(LegacyToModernMigrator.PAYMENT_TABLE);
              assertThat(row.getReasonCode()).isEqualTo(QuarantineReason.PAYMENT_SPLIT_MISMATCH);
              assertThat(row.getSourceRow())
                  .contains("\"loanAccountNumber\":\"" + SPLIT_ANOMALY_LOAN + "\"")
                  .contains("\"totalAmount\":\"1,487.02\"");
              assertThat(row.getCreatedAt()).isNotNull();
            });

    assertThat(addressRepository.count()).isEqualTo(EXPECTED_ADDRESSES);
    assertThat(propertyRepository.count()).isEqualTo(5);
    assertThat(paymentRepository.count()).isEqualTo(8);
    assertThat(paymentRepository.findByLegacyPaymentId("PMT-2025120001")).isEmpty();
    assertThat(paymentRepository.findByLegacyPaymentId("PMT-2025120002")).isPresent();
    LoanAccount anomalyLoan =
        loanAccountRepository.findByAccountNumber(SPLIT_ANOMALY_LOAN).orElseThrow();
    assertThat(paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(anomalyLoan.getId()))
        .isEmpty();

    assertThat(loanProductRepository.findByProductCode("FXD30").orElseThrow().getExpiryDate())
        .isNull();
    Borrower selfEmployed = borrowerRepository.findByLegacyBorrowerId("B-10003").orElseThrow();
    assertThat(selfEmployed.getEmploymentStatus().getCode()).isEqualTo("SELF_EMPLOYED");
    assertThat(borrowerRepository.findByLegacyBorrowerId("B-10001").orElseThrow().getSsnLast4())
        .isEqualTo("0142");
  }

  @Test
  void schemaStillRejectsSplitMismatchWhenLoaderIsBypassed() {
    migrator.migrate();
    TransactionTemplate tx = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> paymentRepository.saveAndFlush(splitMismatchPayment())))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("CK_PAYMENT_SPLIT");
  }

  @Test
  void malformedRowsAreQuarantinedAndGoodRowsStillLoad() {
    cloneLegacyRow("CDW_BORR_MSTR", "BORR_ID", "B-10002",
        Map.of("BORR_ID", "B-99001", "BORR_CRDT_SCR", "900"));
    cloneLegacyRow("CDW_LN_ACCT", "LN_ACCT_NBR", "LN-2020-00398",
        Map.of("LN_ACCT_NBR", "LN-ORPHAN", "BORR_ID", "B-99999"));
    cloneLegacyRow("CDW_LN_ACCT", "LN_ACCT_NBR", "LN-2020-00398",
        Map.of("LN_ACCT_NBR", "LN-BAD-BAL", "LN_CURR_BAL", "abc"));
    cloneLegacyRow("CDW_LN_ACCT", "LN_ACCT_NBR", "LN-2020-00398",
        Map.of("LN_ACCT_NBR", "LN-BAD-STAT", "LN_STAT_CD", "XYZ"));
    cloneLegacyRow("CDW_PMT_HIST", "PMT_SEQ_NBR", "PMT-2025120002",
        Map.of("PMT_SEQ_NBR", "PMT-BAD-DATE", "PMT_DT", "13/40/2025"));
    cloneLegacyRow("CDW_PMT_HIST", "PMT_SEQ_NBR", "PMT-2025120002",
        Map.of("PMT_SEQ_NBR", "PMT-ORPHAN", "LN_ACCT_NBR", "LN-ORPHAN"));

    MigrationReport report = migrator.migrate();

    assertThat(report.quarantinedRows())
        .extracting(QuarantinedRow::sourceKey, QuarantinedRow::reason, QuarantinedRow::field)
        .containsExactlyInAnyOrder(
            tuple("B-99001", QuarantineReason.CONSTRAINT_VIOLATION, null),
            tuple("LN-ORPHAN", QuarantineReason.ORPHAN_REFERENCE, "BORR_ID"),
            tuple("LN-BAD-BAL", QuarantineReason.MALFORMED_VALUE, "LN_CURR_BAL"),
            tuple("LN-BAD-STAT", QuarantineReason.UNKNOWN_CODE, "LN_STAT_CD"),
            tuple("PMT-BAD-DATE", QuarantineReason.MALFORMED_VALUE, "PMT_DT"),
            tuple("PMT-ORPHAN", QuarantineReason.ORPHAN_REFERENCE, "LN_ACCT_NBR"),
            tuple("PMT-2025120001", QuarantineReason.PAYMENT_SPLIT_MISMATCH, "PMT_AMT"),
            tuple("PMT-2025110001", QuarantineReason.PAYMENT_SPLIT_MISMATCH, "PMT_AMT"));
    assertThat(quarantineRepository.count()).isEqualTo(8);

    assertThat(report.counts(LegacyToModernMigrator.BORROWER_TABLE))
        .isEqualTo(new TableCounts(6, 5, 1));
    assertThat(report.counts(LegacyToModernMigrator.LOAN_TABLE))
        .isEqualTo(new TableCounts(8, 5, 3));
    assertThat(report.counts(LegacyToModernMigrator.PAYMENT_TABLE))
        .isEqualTo(new TableCounts(12, 8, 4));
    assertThat(borrowerRepository.count()).isEqualTo(5);
    assertThat(loanAccountRepository.count()).isEqualTo(5);
    assertThat(propertyRepository.count()).isEqualTo(5);
    assertThat(addressRepository.count()).isEqualTo(EXPECTED_ADDRESSES);
    assertThat(paymentRepository.count()).isEqualTo(8);
  }

  private Payment splitMismatchPayment() {
    Payment payment = new Payment();
    payment.setLegacyPaymentId("PMT-BYPASS");
    payment.setLoanAccount(loanAccountRepository.findByAccountNumber(SPLIT_ANOMALY_LOAN).get());
    payment.setPaymentDate(LocalDate.of(2025, 12, 15));
    payment.setTotalAmount(new BigDecimal("1487.02"));
    payment.setPrincipalAmount(new BigDecimal("456.78"));
    payment.setInterestAmount(new BigDecimal("1074.69"));
    payment.setEscrowAmount(new BigDecimal("355.55"));
    payment.setPaymentType(entityManager.getReference(PaymentType.class, "REG"));
    payment.setStatus(entityManager.getReference(PaymentStatus.class, "PST"));
    payment.setCreatedAt(LocalDateTime.of(2025, 12, 15, 0, 0));
    payment.setUpdatedAt(LocalDateTime.of(2025, 12, 15, 0, 0));
    return payment;
  }

  private void cloneLegacyRow(
      String table, String keyColumn, String sourceKey, Map<String, String> overrides) {
    Map<String, Object> row =
        new LinkedHashMap<>(
            jdbc.queryForMap(
                "SELECT * FROM " + table + " WHERE " + keyColumn + " = ?", sourceKey));
    row.putAll(overrides);
    String columns = String.join(", ", row.keySet());
    String placeholders = String.join(", ", Collections.nCopies(row.size(), "?"));
    jdbc.update(
        "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")",
        row.values().toArray());
  }
}
