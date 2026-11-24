package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "loan.migration.run-on-startup=false")
@ActiveProfiles("test")
@DirtiesContext
class MalformedLegacyDataMigrationTest {

    @Autowired
    private LegacyToModernMigrator migrator;

    @Autowired
    @Qualifier("legacyJdbcTemplate")
    private JdbcTemplate legacy;

    @Test
    void rejectsBadRowsAndMigratesTheRest() {
        legacy.update("INSERT INTO CDW_BORR_MSTR (BORR_ID, BORR_FST_NM, BORR_LST_NM, BORR_DOB_DT, BORR_STAT_CD) "
                + "VALUES ('B-90001', 'Bad', 'Date', '02/30/1990', 'ACT')");
        legacy.update("INSERT INTO CDW_LN_ACCT (LN_ACCT_NBR, BORR_ID, PROD_CD, LN_ORIG_AMT, LN_CURR_BAL, LN_INT_RT, "
                + "LN_TERM_MOS, LN_PMT_AMT, LN_ORIG_DT, LN_MAT_DT, LN_STAT_CD) VALUES ('LN-9999-00001', 'B-10001', "
                + "'FXD30', '12x,000', '1,000', '4.000', '360', '100.00', '01/01/2020', '01/01/2050', 'ACT')");
        legacy.update("INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_TYP_CD, PMT_STAT_CD) "
                + "VALUES ('PMT-ORPHAN', 'LN-DOES-NOT-EXIST', '12/01/2025', '10.00', 'REG', 'PST')");

        MigrationReport report = migrator.migrate();

        assertThat(report.get("loan_products").getInserted()).isEqualTo(5);
        assertThat(report.get("borrowers").getInserted()).isEqualTo(5);
        assertThat(report.get("loan_accounts").getInserted()).isEqualTo(5);
        assertThat(report.get("payments").getInserted()).isEqualTo(10);

        assertThat(report.get("borrowers").getRejections()).singleElement()
                .satisfies(r -> assertThat(r.legacyKey()).isEqualTo("B-90001"))
                .satisfies(r -> assertThat(r.reason()).contains("BORR_DOB_DT"));
        assertThat(report.get("loan_accounts").getRejections()).singleElement()
                .satisfies(r -> assertThat(r.reason()).contains("LN_ORIG_AMT"));
        assertThat(report.get("payments").getRejections()).singleElement()
                .satisfies(r -> assertThat(r.reason()).contains("no migrated loan account"));

        assertThat(migrator.reconcile())
                .filteredOn(check -> !check.passed())
                .extracting(ReconciliationCheck::name)
                .contains("borrowers row count", "loan_accounts row count", "payments row count",
                        "payments.total_amount total", "payments.loan_account_id mapping");
    }
}
