package com.workshop.loanservice;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SchemaConstraintTest {

  private static final String LOAN_INSERT =
      "INSERT INTO loan_accounts (account_number, borrower_id, product_id, original_amount,"
          + " current_balance, interest_rate, term_months, monthly_payment, origination_date,"
          + " maturity_date) VALUES (?, ?, ?, 100000.00, 100000.00, 5.000, 360, 536.82,"
          + " DATE '2025-01-01', DATE '2055-01-01')";

  private static final String PAYMENT_INSERT =
      "INSERT INTO payments (legacy_payment_id, loan_account_id, payment_date, total_amount,"
          + " type, status) VALUES (?, ?, ?, 100.00, 'REGULAR', 'POSTED')";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void orphanPaymentIsRejected() {
    assertViolation(
        () -> jdbcTemplate.update(PAYMENT_INSERT, "PMT-ORPHAN", 999_999L, "2025-12-01"));
  }

  @Test
  void paymentWithoutLoanAccountIsRejected() {
    assertViolation(() -> jdbcTemplate.update(PAYMENT_INSERT, "PMT-NULL-FK", null, "2025-12-01"));
  }

  @Test
  void paymentWithoutDateIsRejected() {
    assertViolation(
        () -> jdbcTemplate.update(PAYMENT_INSERT, "PMT-NO-DATE", existingLoanId(), null));
  }

  @Test
  void loanWithoutBorrowerIsRejected() {
    assertViolation(
        () -> jdbcTemplate.update(LOAN_INSERT, "LN-NULL-BORR", null, existingProductId()));
  }

  @Test
  void loanWithUnknownBorrowerIsRejected() {
    assertViolation(
        () -> jdbcTemplate.update(LOAN_INSERT, "LN-ORPHAN-BORR", 999_999L, existingProductId()));
  }

  @Test
  void loanWithUnknownProductIsRejected() {
    assertViolation(
        () -> jdbcTemplate.update(LOAN_INSERT, "LN-ORPHAN-PROD", existingBorrowerId(), 999_999L));
  }

  @Test
  void duplicateLoanAccountNumberIsRejected() {
    assertViolation(
        () ->
            jdbcTemplate.update(
                LOAN_INSERT, "LN-2019-00142", existingBorrowerId(), existingProductId()));
  }

  @Test
  void borrowerWithoutNameIsRejected() {
    assertViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO borrowers (external_id, first_name, last_name) VALUES (?, ?, ?)",
                "B-99999",
                null,
                "Nobody"));
  }

  @Test
  void duplicateBorrowerExternalIdIsRejected() {
    assertViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO borrowers (external_id, first_name, last_name) VALUES (?, ?, ?)",
                "B-10001",
                "Dup",
                "Licate"));
  }

  private static void assertViolation(Runnable insert) {
    assertThrows(DataIntegrityViolationException.class, insert::run);
  }

  private Long existingBorrowerId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM borrowers WHERE external_id = 'B-10001'", Long.class);
  }

  private Long existingProductId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM loan_products WHERE code = 'FXD30'", Long.class);
  }

  private Long existingLoanId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM loan_accounts WHERE account_number = 'LN-2019-00142'", Long.class);
  }
}
