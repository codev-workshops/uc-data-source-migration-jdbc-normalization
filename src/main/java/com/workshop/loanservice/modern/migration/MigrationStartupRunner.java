package com.workshop.loanservice.modern.migration;

import com.workshop.loanservice.modern.repository.ModernBorrowerRepository;
import com.workshop.loanservice.modern.repository.ModernLoanAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Populates the modern schema from the legacy CDW tables at application
 * startup when the modern tables are empty, so the app serves migrated data
 * from its first request. The migration itself is idempotent, so re-running
 * against populated tables would also be safe; the emptiness check just
 * avoids redundant work on every boot.
 *
 * <p>Disable with {@code migration.run-on-startup=false} (used by tests that
 * exercise the migration service against empty modern tables).
 */
@Component
@ConditionalOnProperty(name = "migration.run-on-startup", havingValue = "true", matchIfMissing = true)
public class MigrationStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationStartupRunner.class);

    private final LegacyToModernMigrationService migrationService;
    private final ModernBorrowerRepository modernBorrowerRepository;
    private final ModernLoanAccountRepository modernLoanAccountRepository;

    public MigrationStartupRunner(LegacyToModernMigrationService migrationService,
                                  ModernBorrowerRepository modernBorrowerRepository,
                                  ModernLoanAccountRepository modernLoanAccountRepository) {
        this.migrationService = migrationService;
        this.modernBorrowerRepository = modernBorrowerRepository;
        this.modernLoanAccountRepository = modernLoanAccountRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (modernBorrowerRepository.count() > 0 || modernLoanAccountRepository.count() > 0) {
            log.info("Modern tables already populated; skipping startup migration");
            return;
        }
        log.info("Modern tables empty; running legacy-to-modern migration");
        migrationService.migrateAll();
    }
}
