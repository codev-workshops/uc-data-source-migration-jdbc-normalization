package com.workshop.loanservice.migration;

/** Thrown inside the migration transaction when validation fails, so no partial import is committed. */
public class MigrationFailedException extends RuntimeException {

    private final MigrationReport report;

    public MigrationFailedException(MigrationReport report) {
        super("Legacy -> modern migration failed validation; all changes were rolled back");
        this.report = report;
    }

    public MigrationReport getReport() {
        return report;
    }
}
