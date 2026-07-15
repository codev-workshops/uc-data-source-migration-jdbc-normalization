package com.workshop.loanservice.migration;

public record LegacyDataMigrationResult(
        int borrowers,
        int loanProducts,
        int loanAccounts,
        int payments,
        boolean alreadyMigrated
) {
}
