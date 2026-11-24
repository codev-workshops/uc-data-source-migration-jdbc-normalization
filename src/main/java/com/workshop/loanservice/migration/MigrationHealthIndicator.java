package com.workshop.loanservice.migration;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /actuator/health/migration}: DOWN when the startup migration failed or
 * reconciliation found a mismatch. Rejected and duplicate legacy rows are expected
 * and only reported as details.
 */
@Component
public class MigrationHealthIndicator implements HealthIndicator {

    private final MigrationStatus status;

    public MigrationHealthIndicator(MigrationStatus status) {
        this.status = status;
    }

    @Override
    public Health health() {
        return switch (status.getState()) {
            case NOT_RUN -> Health.unknown().withDetail("state", "NOT_RUN").build();
            case RUNNING -> Health.outOfService().withDetail("state", "RUNNING").build();
            case FAILED -> Health.down().withDetail("state", "FAILED").withDetail("error", status.getError()).build();
            case COMPLETED -> completed();
        };
    }

    private Health completed() {
        Map<String, Object> tables = new LinkedHashMap<>();
        status.getReport().getTables().forEach((name, t) -> tables.put(name, Map.of(
                "read", t.getRead(),
                "inserted", t.getInserted(),
                "skippedExisting", t.getSkippedExisting(),
                "rejected", t.getRejections().size(),
                "duplicates", t.getDuplicates().size())));
        var mismatches = status.getMismatches().stream().map(ReconciliationCheck::toString).toList();
        return (mismatches.isEmpty() ? Health.up() : Health.down())
                .withDetail("state", "COMPLETED")
                .withDetail("tables", tables)
                .withDetail("mismatches", mismatches)
                .build();
    }
}
