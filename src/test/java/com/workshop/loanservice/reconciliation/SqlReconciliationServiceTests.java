package com.workshop.loanservice.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SqlReconciliationServiceTests {

    @Autowired
    private SqlReconciliationService sqlReconciliationService;

    @Test
    void everyQueryPairAgreesAcrossTheTwoDataSources() {
        ReconciliationReport report = sqlReconciliationService.reconcile();

        assertThat(report.getMismatches()).isEmpty();
        assertThat(report.isMatched()).isTrue();
    }

    @Test
    void coversCounts_totals_codeExpansion_rowLevelValues_andIntegrity() {
        assertThat(sqlReconciliationService.checkNames()).contains(
                "borrowers.row_count",
                "payments.row_count",
                "loan_accounts.amount_totals",
                "payments.by_type_and_status",
                "loan_accounts.by_key",
                "payments.orphans");
    }
}
