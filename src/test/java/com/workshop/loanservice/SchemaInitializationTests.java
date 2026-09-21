package com.workshop.loanservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SchemaInitializationTests {

    private static final List<String> LEGACY_TABLES =
            List.of("CDW_BORR_MSTR", "CDW_LN_PROD", "CDW_LN_ACCT", "CDW_PMT_HIST");

    private static final List<String> MODERN_TABLES =
            List.of("BORROWERS", "LOAN_PRODUCTS", "LOAN_ACCOUNTS", "PAYMENTS");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void legacyAndModernTablesExistInSameDatabase() {
        for (String table : LEGACY_TABLES) {
            assertThat(tableExists(table)).as("legacy table %s", table).isTrue();
        }
        for (String table : MODERN_TABLES) {
            assertThat(tableExists(table)).as("modern table %s", table).isTrue();
        }
    }

    @Test
    void legacyDataIsStillPresent() {
        for (String table : LEGACY_TABLES) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            assertThat(count).as("row count of %s", table).isGreaterThan(0);
        }
    }

    @Test
    void modernTablesArePopulatedFromLegacyAtStartup() {
        Map<String, Integer> expected = Map.of(
                "BORROWERS", 5, "LOAN_PRODUCTS", 5, "LOAN_ACCOUNTS", 5, "PAYMENTS", 10);
        for (String table : MODERN_TABLES) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            assertThat(count).as("row count of %s", table).isEqualTo(expected.get(table));
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }
}
