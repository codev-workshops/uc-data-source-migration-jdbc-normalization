package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The startup migration has already run in this context.
 */
@SpringBootTest
@ActiveProfiles("test")
class LegacyToModernMigratorTest {

    @Autowired
    private LegacyToModernMigrator migrator;

    @Autowired
    @Qualifier("modernJdbcTemplate")
    private JdbcTemplate modern;

    @Test
    void migratesAllLegacyRows() {
        assertThat(count("loan_products")).isEqualTo(5);
        assertThat(count("borrowers")).isEqualTo(5);
        assertThat(count("loan_accounts")).isEqualTo(5);
        assertThat(count("payments")).isEqualTo(10);
    }

    @Test
    void reconciliationPasses() {
        assertThat(migrator.reconcile()).isNotEmpty().allSatisfy(check -> assertThat(check.passed()).as(check.toString()).isTrue());
    }

    @Test
    void convertsTypesAndExpandsCodes() {
        Map<String, Object> borrower = modern.queryForMap(
                "SELECT date_of_birth, credit_score, annual_income, status FROM borrowers WHERE external_id = 'B-10001'");
        assertThat(((java.sql.Date) borrower.get("DATE_OF_BIRTH")).toLocalDate()).isEqualTo(LocalDate.of(1978, 3, 15));
        assertThat(borrower.get("CREDIT_SCORE")).isEqualTo(745);
        assertThat((BigDecimal) borrower.get("ANNUAL_INCOME")).isEqualByComparingTo("92500");
        assertThat(borrower.get("STATUS")).isEqualTo("ACTIVE");

        Map<String, Object> loan = modern.queryForMap("""
                SELECT a.current_balance, a.interest_rate, a.status, a.property_type, b.external_id, p.code
                FROM loan_accounts a JOIN borrowers b ON b.id = a.borrower_id JOIN loan_products p ON p.id = a.product_id
                WHERE a.account_number = 'LN-2020-00398'""");
        assertThat((BigDecimal) loan.get("CURRENT_BALANCE")).isEqualByComparingTo("312876.43");
        assertThat((BigDecimal) loan.get("INTEREST_RATE")).isEqualByComparingTo("3.125");
        assertThat(loan.get("STATUS")).isEqualTo("ACTIVE");
        assertThat(loan.get("PROPERTY_TYPE")).isEqualTo("Condominium");
        assertThat(loan.get("EXTERNAL_ID")).isEqualTo("B-10002");
        assertThat(loan.get("CODE")).isEqualTo("FXD15");

        assertThat(modern.queryForObject("SELECT is_active FROM loan_products WHERE code = 'VA30'", Boolean.class)).isTrue();
        assertThat(modern.queryForObject(
                "SELECT late_fee FROM payments WHERE external_id = 'PMT-2025110003'", BigDecimal.class))
                .isEqualByComparingTo("47.50");
    }

    @Test
    void rerunningIsIdempotent() {
        MigrationReport report = migrator.migrate();

        report.getTables().values().forEach(table -> {
            assertThat(table.getInserted()).as(table.getTable()).isZero();
            assertThat(table.getSkippedExisting()).as(table.getTable()).isEqualTo(table.getRead());
        });
        assertThat(count("payments")).isEqualTo(10);
    }

    private int count(String table) {
        return modern.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
