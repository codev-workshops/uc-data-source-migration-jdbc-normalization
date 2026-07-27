package com.workshop.loanservice.migration;

import com.workshop.loanservice.LoanServiceApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Standalone entry point for the legacy to modern data migration.
 *
 * <pre>./mvnw compile exec:java -Dexec.mainClass=com.workshop.loanservice.migration.DataMigrationRunner</pre>
 *
 * <p>Boots its own non-web Spring context, so it never runs as part of normal web startup. Exits
 * with status 1 when any validation criterion fails or the migration transaction rolls back.
 */
public final class DataMigrationRunner {

    private DataMigrationRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(LoanServiceApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            MigrationService migrationService = context.getBean(MigrationService.class);
            migrationService.initializeTracking();
            MigrationReport report = migrationService.migrate();
            System.out.print(report.render());
            exitCode = report.passed() ? 0 : 1;
        } catch (RuntimeException e) {
            System.err.println("Migration failed and was rolled back: " + e);
            e.printStackTrace();
            exitCode = 1;
        }
        System.exit(exitCode);
    }
}
