package com.workshop.loanservice.verification;

import com.workshop.loanservice.migration.MigrationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * TEST/VERIFICATION-ONLY startup migration, gated by the {@code verification} Spring profile.
 *
 * <p>Deliberately lives under {@code src/test} and is a {@link TestConfiguration} (never scanned by
 * the production context), so the normal {@code LoanServiceApplication} startup path NEVER runs the
 * migration. It only activates when a test explicitly imports this class and runs under
 * {@code @ActiveProfiles("verification")}.
 *
 * <p>As an {@link ApplicationRunner} it runs after the legacy seed ({@code spring.sql.init}) and the
 * modern DDL ({@code modernDataSourceInitializer}) have been applied, and before any test method
 * executes, so the modern datasource is populated before the verification assertions run.
 */
@TestConfiguration
public class VerificationMigrationConfig {

    @Bean
    @Profile("verification")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationRunner verificationStartupMigration(MigrationService migrationService) {
        return args -> {
            migrationService.initializeTracking();
            migrationService.migrate();
        };
    }
}
