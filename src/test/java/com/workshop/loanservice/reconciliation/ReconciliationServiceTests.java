package com.workshop.loanservice.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReconciliationServiceTests {

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    void legacyAndModernDataSourcesAgree() {
        ReconciliationReport report = reconciliationService.reconcile();

        assertThat(report.getMismatches()).isEmpty();
        assertThat(report.isMatched()).isTrue();
    }
}
