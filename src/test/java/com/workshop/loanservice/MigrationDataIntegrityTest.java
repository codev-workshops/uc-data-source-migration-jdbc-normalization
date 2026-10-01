package com.workshop.loanservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MigrationDataIntegrityTest {

  @Autowired private BorrowerRepository borrowerRepository;
  @Autowired private LoanProductRepository loanProductRepository;
  @Autowired private LoanAccountRepository loanAccountRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void borrowersMatchGolden() {
    assertAllRowsMatch(
        "borrowers.json",
        "external_id",
        id -> toMap(borrowerRepository.findByExternalId(id).orElseThrow()));
  }

  @Test
  void loanProductsMatchGolden() {
    assertAllRowsMatch(
        "loan_products.json",
        "code",
        code -> toMap(loanProductRepository.findByCode(code).orElseThrow()));
  }

  @Test
  void loanAccountsMatchGolden() {
    assertAllRowsMatch(
        "loan_accounts.json",
        "account_number",
        number -> toMap(loanAccountRepository.findByAccountNumber(number).orElseThrow()));
  }

  @Test
  void paymentsMatchGolden() {
    assertAllRowsMatch(
        "payments.json",
        "legacy_payment_id",
        id -> toMap(paymentRepository.findByLegacyPaymentId(id).orElseThrow()));
  }

  @Test
  void rowCountsMatchLegacySeedData() {
    Map<String, Integer> legacy = GoldenData.legacyRowCounts();
    assertEquals(
        Map.of("CDW_BORR_MSTR", 5, "CDW_LN_PROD", 5, "CDW_LN_ACCT", 5, "CDW_PMT_HIST", 10), legacy);

    assertCount(legacy.get("CDW_BORR_MSTR"), borrowerRepository.count(), "borrowers.json");
    assertCount(legacy.get("CDW_LN_PROD"), loanProductRepository.count(), "loan_products.json");
    assertCount(legacy.get("CDW_LN_ACCT"), loanAccountRepository.count(), "loan_accounts.json");
    assertCount(legacy.get("CDW_PMT_HIST"), paymentRepository.count(), "payments.json");
  }

  @Test
  void allForeignKeysResolve() {
    assertEquals(
        0,
        count(
            "SELECT COUNT(*) FROM loan_accounts la LEFT JOIN borrowers b ON la.borrower_id = b.id"
                + " WHERE b.id IS NULL"));
    assertEquals(
        0,
        count(
            "SELECT COUNT(*) FROM loan_accounts la LEFT JOIN loan_products p"
                + " ON la.product_id = p.id WHERE p.id IS NULL"));
    assertEquals(
        0,
        count(
            "SELECT COUNT(*) FROM payments pm LEFT JOIN loan_accounts la"
                + " ON pm.loan_account_id = la.id WHERE la.id IS NULL"));

    for (Map<String, Object> golden : GoldenData.rows("loan_accounts.json")) {
      LoanAccount acct =
          loanAccountRepository
              .findByAccountNumber((String) golden.get("account_number"))
              .orElseThrow();
      assertEquals(golden.get("borrower_external_id"), acct.getBorrower().getExternalId());
      assertEquals(golden.get("product_code"), acct.getProduct().getCode());
    }
    for (Map<String, Object> golden : GoldenData.rows("payments.json")) {
      Payment pmt =
          paymentRepository.findByLegacyPaymentId((String) golden.get("legacy_payment_id")).get();
      assertEquals(golden.get("loan_account_number"), pmt.getLoanAccount().getAccountNumber());
    }
  }

  @Test
  void loanAccountsHaveNoDenormalizedBorrowerColumns() {
    List<String> columns =
        jdbcTemplate.queryForList(
            "SELECT LOWER(column_name) FROM information_schema.columns"
                + " WHERE LOWER(table_name) = 'loan_accounts'",
            String.class);
    assertTrue(columns.contains("borrower_id"));
    for (String denormalized :
        Set.of("first_name", "last_name", "borr_fst_nm", "borr_lst_nm", "borr_ssn_lst4")) {
      assertFalse(columns.contains(denormalized), "unexpected column " + denormalized);
    }
  }

  private void assertAllRowsMatch(
      String file, String naturalKey, Function<String, Map<String, Object>> loader) {
    List<Map<String, Object>> golden = GoldenData.rows(file);
    assertFalse(golden.isEmpty(), file + " is empty");
    for (Map<String, Object> expected : golden) {
      String key = (String) expected.get(naturalKey);
      GoldenData.assertRowMatches(file + "[" + key + "]", expected, loader.apply(key));
    }
  }

  private static void assertCount(int legacyCount, long modernCount, String goldenFile) {
    assertEquals(legacyCount, modernCount, goldenFile + ": modern row count");
    assertEquals(legacyCount, GoldenData.rows(goldenFile).size(), goldenFile + ": golden rows");
  }

  private int count(String sql) {
    return jdbcTemplate.queryForObject(sql, Integer.class);
  }

  private static Map<String, Object> toMap(Borrower b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("external_id", b.getExternalId());
    m.put("first_name", b.getFirstName());
    m.put("last_name", b.getLastName());
    m.put("middle_initial", b.getMiddleInitial());
    m.put("ssn_hash", b.getSsnHash());
    m.put("date_of_birth", b.getDateOfBirth());
    m.put("address_line1", b.getAddressLine1());
    m.put("address_line2", b.getAddressLine2());
    m.put("city", b.getCity());
    m.put("state", b.getState());
    m.put("zip_code", b.getZipCode());
    m.put("phone", b.getPhone());
    m.put("email", b.getEmail());
    m.put("credit_score", b.getCreditScore());
    m.put("employment_status", b.getEmploymentStatus());
    m.put("annual_income", b.getAnnualIncome());
    m.put("status", b.getStatus());
    m.put("created_at", b.getCreatedAt());
    m.put("updated_at", b.getUpdatedAt());
    return m;
  }

  private static Map<String, Object> toMap(LoanProduct p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", p.getCode());
    m.put("name", p.getName());
    m.put("type", p.getType());
    m.put("term_months", p.getTermMonths());
    m.put("rate_type", p.getRateType());
    m.put("min_amount", p.getMinAmount());
    m.put("max_amount", p.getMaxAmount());
    m.put("is_active", p.getActive());
    m.put("effective_date", p.getEffectiveDate());
    m.put("expiration_date", p.getExpirationDate());
    return m;
  }

  private static Map<String, Object> toMap(LoanAccount a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("account_number", a.getAccountNumber());
    m.put("borrower_external_id", a.getBorrower().getExternalId());
    m.put("product_code", a.getProduct().getCode());
    m.put("original_amount", a.getOriginalAmount());
    m.put("current_balance", a.getCurrentBalance());
    m.put("interest_rate", a.getInterestRate());
    m.put("term_months", a.getTermMonths());
    m.put("monthly_payment", a.getMonthlyPayment());
    m.put("origination_date", a.getOriginationDate());
    m.put("maturity_date", a.getMaturityDate());
    m.put("first_payment_date", a.getFirstPaymentDate());
    m.put("next_payment_date", a.getNextPaymentDate());
    m.put("status", a.getStatus());
    m.put("delinquency_days", a.getDelinquencyDays());
    m.put("escrow_balance", a.getEscrowBalance());
    m.put("ltv_percent", a.getLtvPercent());
    m.put("property_address", a.getPropertyAddress());
    m.put("property_city", a.getPropertyCity());
    m.put("property_state", a.getPropertyState());
    m.put("property_zip", a.getPropertyZip());
    m.put("property_type", a.getPropertyType());
    m.put("appraised_value", a.getAppraisedValue());
    m.put("created_at", a.getCreatedAt());
    m.put("updated_at", a.getUpdatedAt());
    return m;
  }

  private static Map<String, Object> toMap(Payment p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("legacy_payment_id", p.getLegacyPaymentId());
    m.put("loan_account_number", p.getLoanAccount().getAccountNumber());
    m.put("payment_date", p.getPaymentDate());
    m.put("total_amount", p.getTotalAmount());
    m.put("principal_amount", p.getPrincipalAmount());
    m.put("interest_amount", p.getInterestAmount());
    m.put("escrow_amount", p.getEscrowAmount());
    m.put("late_fee", p.getLateFee());
    m.put("type", p.getType());
    m.put("status", p.getStatus());
    m.put("received_date", p.getReceivedDate());
    m.put("processed_date", p.getProcessedDate());
    m.put("created_at", p.getCreatedAt());
    m.put("updated_at", p.getUpdatedAt());
    return m;
  }
}
