package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Optionally runs the legacy → modern migration once at application startup.
 *
 * Disabled by default; enable with {@code loanservice.migrate-on-startup=true}.
 * Kept opt-in so the default boot (and the golden tests, which exercise the
 * legacy read path) are unaffected.
 */
@Component
@ConditionalOnProperty(name = "loanservice.migrate-on-startup", havingValue = "true")
public class MigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final MigrationService migrationService;

    public MigrationRunner(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        MigrationResult result = migrationService.migrate();
        log.info("Startup migration finished: {}", result);
    }
}
