package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each test adds problem rows to the seeded legacy tables and runs the migration itself.
 */
@SpringBootTest(properties = "loan.migration.run-on-startup=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MigrationEdgeCaseTest {

    @Autowired
    private LegacyToModernMigrator migrator;

    @Autowired
    @Qualifier("legacyJdbcTemplate")
    private JdbcTemplate legacy;

    @Autowired
    @Qualifier("modernJdbcTemplate")
    private JdbcTemplate modern;

    @Test
    void rejectsBadRowsAndCascadesToTheirChildren() {
        borrower("B-90001", "Bad", "02/29/2023", "ACT");
        loan("LN-9999-00001", "B-90001", "100,000.00");
        payment("PMT-CASCADE-1", "LN-9999-00001", "12/01/2025", "100.00", "REG", "PST");
        loan("LN-9999-00002", "B-10001", "12345678901.00");
        payment("PMT-CASCADE-2", "LN-9999-00002", "12/01/2025", "100.00", "REG", "PST");
        payment("PMT-ORPHAN", "LN-DOES-NOT-EXIST", "12/01/2025", "10.00", "REG", "PST");
        payment("PMT-NO-DATE", "LN-2019-00142", "  ", "10.00", "REG", "PST");
        payment("PMT-EXPONENT", "LN-2019-00142", "12/01/2025", "1.5E3", "REG", "PST");
        payment("PMT-BAD-CODE", "LN-2019-00142", "12/01/2025", "10.00", "XYZ", "PST");

        MigrationReport report = migrator.migrate();

        assertInserted(report, 5, 5, 5, 10);
        assertThat(reasons(report, "borrowers")).containsExactly(
                "B-90001: BORR_DOB_DT='02/29/2023': not a valid MM/DD/YYYY date");
        assertThat(reasons(report, "loan_accounts")).containsExactly(
                "LN-9999-00001: BORR_ID='B-90001': borrower B-90001 was rejected",
                "LN-9999-00002: LN_ORIG_AMT='12345678901.00': too large for DECIMAL(12, 2)");
        assertThat(reasons(report, "payments")).hasSize(6)
                .contains("PMT-CASCADE-1: LN_ACCT_NBR='LN-9999-00001': loan account LN-9999-00001 was rejected",
                        "PMT-CASCADE-2: LN_ACCT_NBR='LN-9999-00002': loan account LN-9999-00002 was rejected",
                        "PMT-ORPHAN: LN_ACCT_NBR='LN-DOES-NOT-EXIST': orphaned reference: no loan account with this key",
                        "PMT-EXPONENT: PMT_AMT='1.5E3': not a monetary amount",
                        "PMT-BAD-CODE: PMT_TYP_CD='XYZ': unknown code",
                        "PMT-NO-DATE: PMT_DT='  ': missing required value");

        List<ReconciliationCheck> checks = migrator.reconcile(report);
        assertThat(checks).allSatisfy(check -> assertThat(check.passed()).as(check.toString()).isTrue());
        assertThat(check(checks, "payments row count").legacyValue()).isEqualTo("10 (16 rows - 6 rejected/duplicate)");
        assertThat(check(checks, "payments.loan_account_id mapping").legacyValue()).isEqualTo("10 links (1 orphaned in legacy)");
    }

    @Test
    void normalizesPaddedAndLowercaseValuesAndReportsDuplicateKeys() {
        // Widened so a raw extract can carry the padding that VARCHAR(5) would otherwise reject on insert.
        legacy.execute("ALTER TABLE CDW_BORR_MSTR ALTER COLUMN BORR_STAT_CD VARCHAR(10)");
        String firstPayment = legacy.queryForObject("SELECT PMT_SEQ_NBR FROM CDW_PMT_HIST LIMIT 1", String.class);
        borrower(" B-10001 ", "Impostor", "01/01/1980", "ACT");
        borrower("B-90002", "  Zoe\u0000\t", "02/29/2024", " act ");
        borrower("B-90003", "Lower", "01/01/1990", "active  ");
        payment(" " + firstPayment + "\t", "LN-2019-00142", "12/01/2025", "9.99", "REG", "PST");
        payment("PMT-EDGE-1", " LN-2019-00142 ", "12/15/2025", "$1,234.56", " reg ", " nsf ");
        payment("PMT-EDGE-2", "LN-2019-00142", "12/16/2025", "(25.00)", "Reg", "rev");

        MigrationReport report = migrator.migrate();

        assertInserted(report, 7, 5, 5, 12);
        assertThat(report.get("borrowers").getDuplicates()).singleElement()
                .satisfies(d -> assertThat(d.legacyKey()).isEqualTo("B-10001"));
        assertThat(report.get("payments").getDuplicates()).singleElement()
                .satisfies(d -> assertThat(d.legacyKey()).isEqualTo(firstPayment));
        report.getTables().values().forEach(table -> assertThat(table.getRejections()).as(table.getTable()).isEmpty());

        assertThat(modern.queryForObject("SELECT first_name FROM borrowers WHERE external_id = 'B-10001'", String.class))
                .isEqualTo("James");
        Map<String, Object> zoe = modern.queryForMap(
                "SELECT first_name, date_of_birth, status FROM borrowers WHERE external_id = 'B-90002'");
        assertThat(zoe.get("FIRST_NAME")).isEqualTo("Zoe");
        assertThat(((java.sql.Date) zoe.get("DATE_OF_BIRTH")).toLocalDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(zoe.get("STATUS")).isEqualTo("ACTIVE");
        assertThat(modern.queryForObject("SELECT status FROM borrowers WHERE external_id = 'B-90003'", String.class))
                .isEqualTo("ACTIVE");

        Map<String, Object> edge = modern.queryForMap("""
                SELECT p.total_amount, p.type, p.status, a.account_number
                FROM payments p JOIN loan_accounts a ON a.id = p.loan_account_id WHERE p.external_id = 'PMT-EDGE-1'""");
        assertThat((BigDecimal) edge.get("TOTAL_AMOUNT")).isEqualByComparingTo("1234.56");
        assertThat(edge.get("TYPE")).isEqualTo("REGULAR");
        assertThat(edge.get("STATUS")).isEqualTo("NSF");
        assertThat(edge.get("ACCOUNT_NUMBER")).isEqualTo("LN-2019-00142");
        Map<String, Object> reversal = modern.queryForMap(
                "SELECT total_amount, type, status FROM payments WHERE external_id = 'PMT-EDGE-2'");
        assertThat((BigDecimal) reversal.get("TOTAL_AMOUNT")).isEqualByComparingTo("-25.00");
        assertThat(reversal.get("TYPE")).isEqualTo("REGULAR");
        assertThat(reversal.get("STATUS")).isEqualTo("REVERSED");

        assertThat(migrator.reconcile(report)).allSatisfy(check -> assertThat(check.passed()).as(check.toString()).isTrue());

        MigrationReport rerun = migrator.migrate();

        rerun.getTables().values().forEach(table -> {
            MigrationReport.TableReport first = report.get(table.getTable());
            assertThat(table.getInserted()).as(table.getTable()).isZero();
            assertThat(table.getSkippedExisting()).as(table.getTable()).isEqualTo(first.getInserted());
            assertThat(table.getDuplicates()).as(table.getTable()).isEqualTo(first.getDuplicates());
        });
        assertThat(modern.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(12);
        assertThat(migrator.reconcile(rerun)).allSatisfy(check -> assertThat(check.passed()).as(check.toString()).isTrue());
    }

    @Test
    void reportsDuplicateKeysInEveryTableAndCascadesFromRejectedFirstOccurrences() {
        product(" FXD30 ", "360");
        product("BAD1", "abc");
        borrower("B-90010", "Bad", "02/30/1990", "ACT");
        borrower(" B-90010", "Valid", "01/01/1990", "ACT");
        loan(" LN-2019-00142 ", "B-10001", "FXD30", "100,000.00");
        loan("LN-9999-00010", "B-90010", "FXD30", "100,000.00");
        loan("LN-9999-00011", "B-10001", "BAD1", "100,000.00");
        payment("PMT-CASCADE-10", "LN-9999-00010", "12/01/2025", "100.00", "REG", "PST");
        payment("PMT-CASCADE-11", "LN-9999-00011", "12/01/2025", "100.00", "REG", "PST");

        MigrationReport report = migrator.migrate();

        assertInserted(report, 5, 5, 5, 10);
        assertThat(keys(report.get("loan_products").getDuplicates())).containsExactly("FXD30");
        assertThat(keys(report.get("borrowers").getDuplicates())).containsExactly("B-90010");
        assertThat(keys(report.get("loan_accounts").getDuplicates())).containsExactly("LN-2019-00142");
        assertThat(reasons(report, "loan_products")).containsExactly("BAD1: PROD_TERM_MOS='abc': not an integer");
        assertThat(reasons(report, "borrowers")).containsExactly(
                "B-90010: BORR_DOB_DT='02/30/1990': not a valid MM/DD/YYYY date");
        assertThat(reasons(report, "loan_accounts")).containsExactly(
                "LN-9999-00010: BORR_ID='B-90010': borrower B-90010 was rejected",
                "LN-9999-00011: PROD_CD='BAD1': loan product BAD1 was rejected");
        assertThat(reasons(report, "payments")).containsExactly(
                "PMT-CASCADE-10: LN_ACCT_NBR='LN-9999-00010': loan account LN-9999-00010 was rejected",
                "PMT-CASCADE-11: LN_ACCT_NBR='LN-9999-00011': loan account LN-9999-00011 was rejected");
        assertThat(modern.queryForObject("SELECT COUNT(*) FROM borrowers WHERE external_id = 'B-90010'", Integer.class))
                .isZero();
        assertThat(modern.queryForObject("SELECT term_months FROM loan_products WHERE code = 'FXD30'", Integer.class))
                .isEqualTo(360);

        List<ReconciliationCheck> checks = migrator.reconcile(report);
        assertThat(checks).allSatisfy(check -> assertThat(check.passed()).as(check.toString()).isTrue());
        assertThat(check(checks, "borrowers row count").legacyValue()).isEqualTo("5 (7 rows - 2 rejected/duplicate)");
    }

    @Test
    void flagsUnexpectedDiscrepancies() {
        MigrationReport report = migrator.migrate();
        modern.update("DELETE FROM payments WHERE external_id = 'PMT-2025120003'");

        List<ReconciliationCheck> checks = migrator.reconcile(report);

        assertThat(checks).filteredOn(check -> !check.passed())
                .extracting(ReconciliationCheck::name)
                .contains("payments row count", "payments.total_amount total", "payments.loan_account_id mapping")
                .doesNotContain("borrowers row count", "loan_accounts row count");
        assertThat(check(checks, "payments.loan_account_id mapping").modernValue())
                .isEqualTo("9 matching, 1 unmapped, 0 unexpected, 0 orphaned");
    }

    private void borrower(String id, String firstName, String dateOfBirth, String status) {
        legacy.update("INSERT INTO CDW_BORR_MSTR (BORR_ID, BORR_FST_NM, BORR_LST_NM, BORR_DOB_DT, BORR_STAT_CD) "
                + "VALUES (?, ?, 'Test', ?, ?)", id, firstName, dateOfBirth, status);
    }

    private void product(String code, String termMonths) {
        legacy.update("INSERT INTO CDW_LN_PROD (PROD_CD, PROD_DESC_TXT, PROD_TYP_CD, PROD_TERM_MOS, PROD_RT_TYP, "
                + "PROD_STAT_CD) VALUES (?, 'Test product', 'FXD', ?, 'FIXED', 'ACT')", code, termMonths);
    }

    private void loan(String accountNumber, String borrowerId, String originalAmount) {
        loan(accountNumber, borrowerId, "FXD30", originalAmount);
    }

    private void loan(String accountNumber, String borrowerId, String productCode, String originalAmount) {
        legacy.update("INSERT INTO CDW_LN_ACCT (LN_ACCT_NBR, BORR_ID, PROD_CD, LN_ORIG_AMT, LN_CURR_BAL, LN_INT_RT, "
                + "LN_TERM_MOS, LN_PMT_AMT, LN_ORIG_DT, LN_MAT_DT, LN_STAT_CD) "
                + "VALUES (?, ?, ?, ?, '1,000', '4.000', '360', '100.00', '01/01/2020', '01/01/2050', 'ACT')",
                accountNumber, borrowerId, productCode, originalAmount);
    }

    private void payment(String id, String accountNumber, String date, String amount, String type, String status) {
        legacy.update("INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_TYP_CD, PMT_STAT_CD) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, accountNumber, date, amount, type, status);
    }

    private static void assertInserted(MigrationReport report, int borrowers, int products, int accounts, int payments) {
        assertThat(report.get("borrowers").getInserted()).isEqualTo(borrowers);
        assertThat(report.get("loan_products").getInserted()).isEqualTo(products);
        assertThat(report.get("loan_accounts").getInserted()).isEqualTo(accounts);
        assertThat(report.get("payments").getInserted()).isEqualTo(payments);
    }

    private static List<String> reasons(MigrationReport report, String table) {
        return report.get(table).getRejections().stream().map(r -> r.legacyKey() + ": " + r.reason()).toList();
    }

    private static List<String> keys(List<MigrationReport.Rejection> rows) {
        return rows.stream().map(MigrationReport.Rejection::legacyKey).toList();
    }

    private static ReconciliationCheck check(List<ReconciliationCheck> checks, String name) {
        return checks.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }
}
