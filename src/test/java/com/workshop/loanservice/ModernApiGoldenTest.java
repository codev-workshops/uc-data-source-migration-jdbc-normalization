package com.workshop.loanservice;

import com.workshop.loanservice.migration.MigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Golden-file regression for the public API reading from the modern data source
 * ({@code loanservice.datasource=modern}). The modern schema is populated by
 * running the migration first, then the same golden files validate that the
 * modern read path is output-identical to the legacy baseline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "loanservice.datasource=modern")
class ModernApiGoldenTest extends AbstractApiGoldenTest {

    @Autowired
    private MigrationService migrationService;

    @BeforeEach
    void migrateData() {
        migrationService.migrate();
    }
}
