package com.workshop.loanservice.service.migration;

import com.workshop.loanservice.service.MigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Populates the modern schema from the legacy CDW tables once the application context
 * (including SQL schema/data initialization) is ready.
 */
@Component
public class MigrationStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationStartupRunner.class);

    private final MigrationService migrationService;

    public MigrationStartupRunner(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running legacy -> modern migration at startup");
        MigrationSummary summary = migrationService.migrate();
        summary.getTables().forEach((table, counts) ->
                log.info("  {}: legacy={} inserted={} skipped={}",
                        table, counts.legacy(), counts.inserted(), counts.skipped()));
    }
}
