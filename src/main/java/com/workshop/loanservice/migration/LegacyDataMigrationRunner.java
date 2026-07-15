package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("legacy-migration-run")
public class LegacyDataMigrationRunner implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(LegacyDataMigrationRunner.class);

    private final LegacyDataMigrationService migrationService;

    public LegacyDataMigrationRunner(LegacyDataMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        LegacyDataMigrationResult result = migrationService.migrate();
        LOGGER.info(
                "Legacy data migration completed: borrowers={}, loanProducts={}, "
                        + "loanAccounts={}, payments={}, alreadyMigrated={}",
                result.borrowers(),
                result.loanProducts(),
                result.loanAccounts(),
                result.payments(),
                result.alreadyMigrated()
        );
    }
}
