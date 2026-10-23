package com.workshop.loanservice.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workshop.loanservice.validation.ReconciliationQueries.Check;
import com.workshop.loanservice.validation.ReconciliationQueries.Mismatch;
import java.io.IOException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Runs {@code data/validation/reconciliation_queries.sql} against both datasources after the startup
 * migration and reports any check whose legacy and modern results differ.
 */
@SpringBootTest
class ReconciliationQueriesTest {

    @Autowired
    @Qualifier("modernDataSource")
    private DataSource modernDataSource;

    @Autowired
    @Qualifier("legacyDataSource")
    private DataSource legacyDataSource;

    private JdbcTemplate modern;
    private JdbcTemplate legacy;
    private List<Check> checks;

    @BeforeEach
    void setUp() throws IOException {
        modern = new JdbcTemplate(modernDataSource);
        legacy = new JdbcTemplate(legacyDataSource);
        checks = ReconciliationQueries.load(ReconciliationQueries.DEFAULT_SCRIPT);
    }

    @Test
    void scriptCoversCountsSumsAndForeignKeys() {
        assertThat(checks).extracting(Check::name).containsExactly(
                "row_counts", "borrower_income_total", "loan_amount_totals", "payment_amount_totals",
                "payment_totals_by_loan", "loan_values_by_account", "borrower_keys", "product_keys",
                "loan_foreign_keys", "payment_foreign_keys", "orphan_references");
    }

    @Test
    void migratedSeedDataReconcilesWithoutMismatches() {
        List<Mismatch> mismatches = ReconciliationQueries.run(checks, legacy, modern);

        assertThat(mismatches).as(mismatches.toString()).isEmpty();
    }

    @Test
    @DirtiesContext
    void driftedAmountsAndOrphanRowsAreReportedPerCheck() {
        modern.update("UPDATE payments SET total_amount = total_amount + 1 WHERE external_id = 'PMT-2025120001'");
        legacy.update("INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_TYP_CD, PMT_STAT_CD)"
                + " VALUES ('PMT-ORPHAN', 'LN-MISSING', '01/15/2026', '100.00', 'REG', 'PST')");

        List<Mismatch> mismatches = ReconciliationQueries.run(checks, legacy, modern);

        assertThat(mismatches).extracting(Mismatch::check).containsExactlyInAnyOrder(
                "row_counts", "payment_amount_totals", "payment_totals_by_loan",
                "payment_foreign_keys", "orphan_references");
        assertThat(mismatches).filteredOn(m -> m.check().equals("payment_totals_by_loan")).singleElement()
                .satisfies(m -> {
                    assertThat(m.onlyInLegacy()).containsExactlyInAnyOrder(
                            List.of("LN-2019-00142", "2", "2974.04"), List.of("LN-MISSING", "1", "100"));
                    assertThat(m.onlyInModern()).containsExactly(List.of("LN-2019-00142", "2", "2975.04"));
                });
        assertThat(mismatches).filteredOn(m -> m.check().equals("orphan_references")).singleElement()
                .satisfies(m -> assertThat(m.onlyInLegacy()).containsExactly(List.of("payment_without_loan", "1")));
    }

    @Test
    void checksWithoutBothQueriesAreRejected() {
        assertThatThrownBy(() -> ReconciliationQueries.parse("-- @check half\n-- @legacy\nSELECT 1;\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("half");
    }
}
