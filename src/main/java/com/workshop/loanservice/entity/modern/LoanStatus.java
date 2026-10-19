package com.workshop.loanservice.entity.modern;

/** Lifecycle status of a loan account, stored by name in {@code loan_accounts.status}. */
public enum LoanStatus {
    ACTIVE("Active"),
    CLOSED("Closed"),
    DEFAULT("Default"),
    FORBEARANCE("Forbearance"),
    /** Legacy code with no modern mapping; the original code is kept in {@code legacy_status_code}. */
    UNKNOWN("Unknown");

    private final String displayName;

    LoanStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
