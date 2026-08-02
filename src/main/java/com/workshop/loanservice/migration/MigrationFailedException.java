package com.workshop.loanservice.migration;

/** Raised in strict mode when a run rejected at least one row. */
public class MigrationFailedException extends RuntimeException {

    private final transient MigrationReport report;

    public MigrationFailedException(MigrationReport report) {
        super("Migration rejected " + report.getRejections().size() + " row(s): " + report.summary());
        this.report = report;
    }

    public MigrationReport getReport() {
        return report;
    }
}
