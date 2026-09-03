package com.workshop.loanservice.schema;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executes {@code schema-modern.sql} and {@code data-modern-reference.sql} against an isolated
 * in-memory H2 database (no Spring context, no {@code application.properties}) and proves that
 * the DDL is valid for the H2 version on the classpath and that its FK / CHECK / reference-table
 * constraints are actually enforced.
 */
class ModernSchemaTest {

    private static final String URL = "jdbc:h2:mem:modern-test;DB_CLOSE_DELAY=-1";

    private static final List<String> CORE_TABLES = List.of(
            "ADDRESS", "BORROWER", "LOAN_PRODUCT", "PROPERTY", "LOAN_ACCOUNT", "PAYMENT");

    private static final List<String> REFERENCE_TABLES = List.of(
            "LOAN_STATUS", "PROPERTY_TYPE", "PAYMENT_TYPE", "PAYMENT_STATUS", "EMPLOYMENT_STATUS",
            "PRODUCT_TYPE", "RATE_TYPE", "BORROWER_STATUS", "BORROWER_RECORD_TYPE");

    private static JdbcDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void createSchema() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL(URL);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-modern.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("data-modern-reference.sql"));
        }
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void dropDatabase() {
        jdbc.execute("SHUTDOWN");
    }

    /** Each test starts from empty core tables; reference rows are left in place. */
    @BeforeEach
    void clearCoreTables() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : CORE_TABLES) {
            jdbc.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // -------------------------------------------------------------------------
    // Structure
    // -------------------------------------------------------------------------

    @Test
    void allCoreAndReferenceTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'",
                String.class);
        assertThat(tables).containsAll(CORE_TABLES).containsAll(REFERENCE_TABLES);
        assertThat(tables).hasSize(CORE_TABLES.size() + REFERENCE_TABLES.size());
    }

    @Test
    void loanAccountAndPaymentForeignKeysExist() {
        assertThat(foreignKeyTarget("LOAN_ACCOUNT", "BORROWER_ID")).isEqualTo("BORROWER.ID");
        assertThat(foreignKeyTarget("LOAN_ACCOUNT", "PRODUCT_ID")).isEqualTo("LOAN_PRODUCT.ID");
        assertThat(foreignKeyTarget("LOAN_ACCOUNT", "PROPERTY_ID")).isEqualTo("PROPERTY.ID");
        assertThat(foreignKeyTarget("PAYMENT", "LOAN_ACCOUNT_ID")).isEqualTo("LOAN_ACCOUNT.ID");
    }

    @Test
    void referenceTablesAreSeeded() {
        assertThat(codes("BORROWER_STATUS")).contains("ACT");
        assertThat(codes("BORROWER_RECORD_TYPE")).contains("PRI");
        assertThat(codes("EMPLOYMENT_STATUS")).contains("EMPLOYED", "SELF_EMPLOYED", "RETIRED");
        assertThat(codes("PRODUCT_TYPE")).containsExactlyInAnyOrder("FXD", "ARM", "FHA", "VA");
        assertThat(codes("RATE_TYPE")).containsExactlyInAnyOrder("FIXED", "VARIABLE");
        assertThat(codes("LOAN_STATUS")).contains("ACT");
        assertThat(codes("PROPERTY_TYPE")).contains("SFR", "CND", "TWN");
        assertThat(codes("PAYMENT_TYPE")).contains("REG");
        assertThat(codes("PAYMENT_STATUS")).contains("PST");
    }

    // -------------------------------------------------------------------------
    // Happy path: a full graph loads
    // -------------------------------------------------------------------------

    @Test
    void fullGraphCanBeInserted() {
        long loanId = insertLoanAccount();
        insertPayment(loanId, "1487.02", "456.78", "674.69", "355.55");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT b.last_name FROM loan_account l JOIN borrower b ON b.id = l.borrower_id WHERE l.id = ?",
                String.class, loanId)).isEqualTo("Mitchell");
    }

    // -------------------------------------------------------------------------
    // Negative paths: constraints are enforced
    // -------------------------------------------------------------------------

    @Test
    void borrowerWithUnknownStatusCodeIsRejected() {
        long addressId = insertAddress();
        assertThatThrownBy(() -> insertBorrower(addressId, 745, "EMPLOYED", "ZZZ", "PRI"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_BORROWER_STATUS");
    }

    @Test
    void borrowerWithUnknownEmploymentStatusIsRejected() {
        long addressId = insertAddress();
        assertThatThrownBy(() -> insertBorrower(addressId, 745, "SELF-EMP", "ACT", "PRI"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_BORROWER_EMP_STATUS");
    }

    @Test
    void loanAccountWithUnknownStatusCodeIsRejected() {
        long borrowerId = insertBorrower(insertAddress(), 745, "EMPLOYED", "ACT", "PRI");
        long productId = insertProduct();
        long propertyId = insertProperty(insertAddress());
        assertThatThrownBy(() -> insertLoanAccount(borrowerId, productId, propertyId, "XXX"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_LOAN_STATUS");
    }

    @Test
    void paymentWithUnknownStatusCodeIsRejected() {
        long loanId = insertLoanAccount();
        assertThatThrownBy(() -> insertPayment(loanId, "REG", "BOGUS", "100.00", "50.00", "50.00", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_PAYMENT_STATUS");
    }

    @ParameterizedTest
    @ValueSource(ints = {900, 299, 851})
    void creditScoreOutsideRangeIsRejected(int creditScore) {
        long addressId = insertAddress();
        assertThatThrownBy(() -> insertBorrower(addressId, creditScore, "EMPLOYED", "ACT", "PRI"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_BORROWER_CREDIT_SCORE");
    }

    @Test
    void creditScoreBoundariesAndNullAreAccepted() {
        insertBorrower(insertAddress(), 300, "EMPLOYED", "ACT", "PRI", "B-1");
        insertBorrower(insertAddress(), 850, "EMPLOYED", "ACT", "PRI", "B-2");
        insertBorrower(insertAddress(), null, "EMPLOYED", "ACT", "PRI", "B-3");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM borrower", Integer.class)).isEqualTo(3);
    }

    @Test
    void paymentSplitNotMatchingTotalIsRejected() {
        long loanId = insertLoanAccount();
        assertThatThrownBy(() -> insertPayment(loanId, "1487.02", "456.78", "1074.69", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_PAYMENT_SPLIT");
    }

    @Test
    void lateFeeIsNotPartOfTheSplit() {
        long loanId = insertLoanAccount();
        insertPayment(loanId, "REG", "PST", "1077.05", "295.82", "781.23", "0.00", "47.50");
        assertThat(jdbc.queryForObject("SELECT late_fee_amount FROM payment", String.class)).isEqualTo("47.50");
    }

    @Test
    void paymentForNonExistentLoanIsRejected() {
        assertThatThrownBy(() -> insertPayment(9999L, "100.00", "50.00", "50.00", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_PAYMENT_LOAN");
    }

    @Test
    void loanAccountForNonExistentBorrowerIsRejected() {
        long productId = insertProduct();
        long propertyId = insertProperty(insertAddress());
        assertThatThrownBy(() -> insertLoanAccount(9999L, productId, propertyId, "ACT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_LOAN_BORROWER");
    }

    @Test
    void deletingLoanWithPaymentsIsRestricted() {
        long loanId = insertLoanAccount();
        insertPayment(loanId, "100.00", "50.00", "50.00", "0.00");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM loan_account WHERE id = ?", loanId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_PAYMENT_LOAN");
    }

    @Test
    void lowercaseStateCodeIsRejected() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO address (line1, city, state_code, postal_code) VALUES ('1 Main', 'Town', 'il', '00000')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_ADDRESS_STATE");
    }

    @Test
    void duplicateLegacyBorrowerIdIsRejected() {
        insertBorrower(insertAddress(), 700, "EMPLOYED", "ACT", "PRI", "B-10001");
        long addressId = insertAddress();
        assertThatThrownBy(() -> insertBorrower(addressId, 700, "EMPLOYED", "ACT", "PRI", "B-10001"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String foreignKeyTarget(String table, String column) {
        List<String> targets = jdbc.queryForList(
                "SELECT pk.table_name || '.' || pk.column_name "
                        + "FROM information_schema.referential_constraints rc "
                        + "JOIN information_schema.key_column_usage fk "
                        + "  ON fk.constraint_name = rc.constraint_name AND fk.constraint_schema = rc.constraint_schema "
                        + "JOIN information_schema.key_column_usage pk "
                        + "  ON pk.constraint_name = rc.unique_constraint_name AND pk.constraint_schema = rc.unique_constraint_schema "
                        + " AND pk.ordinal_position = fk.position_in_unique_constraint "
                        + "WHERE fk.table_name = ? AND fk.column_name = ?",
                String.class, table, column);
        assertThat(targets).as("FK on %s.%s", table, column).hasSize(1);
        return targets.get(0);
    }

    private static List<String> codes(String referenceTable) {
        return jdbc.queryForList("SELECT code FROM " + referenceTable, String.class);
    }

    private static long insertAddress() {
        jdbc.update("INSERT INTO address (line1, line2, city, state_code, postal_code) "
                + "VALUES ('742 Elm Street', 'Apt 3B', 'Springfield', 'IL', '62701')");
        return maxId("address");
    }

    private static long insertBorrower(long addressId, Integer creditScore, String employmentCode,
                                       String statusCode, String recordTypeCode) {
        return insertBorrower(addressId, creditScore, employmentCode, statusCode, recordTypeCode, "B-10001");
    }

    private static long insertBorrower(long addressId, Integer creditScore, String employmentCode,
                                       String statusCode, String recordTypeCode, String legacyId) {
        jdbc.update("INSERT INTO borrower (legacy_borrower_id, first_name, last_name, middle_initial, "
                        + "ssn_encrypted, ssn_last4, date_of_birth, mailing_address_id, phone_number, email_address, "
                        + "credit_score, employment_status_code, annual_income, status_code, record_type_code, "
                        + "created_at, updated_at) VALUES (?, 'James', 'Mitchell', 'R', 'ENC_XXX_001', '0142', "
                        + "DATE '1978-03-15', ?, '217-555-0142', 'j.mitchell@email.com', ?, ?, 92500.00, ?, ?, "
                        + "TIMESTAMP '2019-01-15 00:00:00', TIMESTAMP '2025-11-03 00:00:00')",
                legacyId, addressId, creditScore, employmentCode, statusCode, recordTypeCode);
        return maxId("borrower");
    }

    private static long insertProduct() {
        jdbc.update("INSERT INTO loan_product (product_code, description, product_type_code, term_months, "
                + "rate_type_code, min_amount, max_amount, is_active, effective_date, expiry_date) "
                + "VALUES ('FXD30', '30-Year Fixed Rate Mortgage', 'FXD', 360, 'FIXED', 50000.00, 1500000.00, "
                + "TRUE, DATE '2020-01-01', NULL)");
        return maxId("loan_product");
    }

    private static long insertProperty(long addressId) {
        jdbc.update("INSERT INTO property (address_id, property_type_code, appraised_value) VALUES (?, 'SFR', 345000.00)",
                addressId);
        return maxId("property");
    }

    private static long insertLoanAccount() {
        long addressId = insertAddress();
        long borrowerId = insertBorrower(addressId, 745, "EMPLOYED", "ACT", "PRI");
        return insertLoanAccount(borrowerId, insertProduct(), insertProperty(addressId), "ACT");
    }

    private static long insertLoanAccount(long borrowerId, long productId, long propertyId, String statusCode) {
        jdbc.update("INSERT INTO loan_account (account_number, borrower_id, product_id, property_id, "
                        + "original_amount, current_balance, interest_rate, term_months, monthly_payment_amount, "
                        + "origination_date, maturity_date, first_payment_date, next_payment_date, status_code, "
                        + "delinquency_days, escrow_balance, loan_to_value_pct, created_at, updated_at) "
                        + "VALUES ('LN-2019-00142', ?, ?, ?, 285000.00, 271432.56, 4.750, 360, 1487.02, "
                        + "DATE '2019-02-15', DATE '2049-02-15', DATE '2019-03-15', DATE '2026-01-15', ?, "
                        + "0, 3245.80, 82.50, TIMESTAMP '2019-02-01 00:00:00', TIMESTAMP '2025-12-01 00:00:00')",
                borrowerId, productId, propertyId, statusCode);
        return maxId("loan_account");
    }

    private static void insertPayment(long loanId, String total, String principal, String interest, String escrow) {
        insertPayment(loanId, "REG", "PST", total, principal, interest, escrow, "0.00");
    }

    private static void insertPayment(long loanId, String typeCode, String statusCode,
                                      String total, String principal, String interest, String escrow) {
        insertPayment(loanId, typeCode, statusCode, total, principal, interest, escrow, "0.00");
    }

    private static void insertPayment(long loanId, String typeCode, String statusCode, String total,
                                      String principal, String interest, String escrow, String lateFee) {
        jdbc.update("INSERT INTO payment (legacy_payment_id, loan_account_id, payment_date, total_amount, "
                        + "principal_amount, interest_amount, escrow_amount, late_fee_amount, payment_type_code, "
                        + "status_code, received_date, processed_date, created_at, updated_at) "
                        + "VALUES ('PMT-2025120001', ?, DATE '2025-12-15', CAST(? AS DECIMAL(15,2)), "
                        + "CAST(? AS DECIMAL(15,2)), CAST(? AS DECIMAL(15,2)), CAST(? AS DECIMAL(15,2)), "
                        + "CAST(? AS DECIMAL(15,2)), ?, ?, DATE '2025-12-14', DATE '2025-12-15', "
                        + "TIMESTAMP '2025-12-15 00:00:00', TIMESTAMP '2025-12-15 00:00:00')",
                loanId, total, principal, interest, escrow, lateFee, typeCode, statusCode);
    }

    private static long maxId(String table) {
        return jdbc.queryForObject("SELECT MAX(id) FROM " + table, Long.class);
    }
}
