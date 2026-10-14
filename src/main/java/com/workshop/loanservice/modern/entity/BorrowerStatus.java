package com.workshop.loanservice.modern.entity;

/** Borrower status stored in {@code borrowers.status}. */
public enum BorrowerStatus {
    ACTIVE("ACT"),
    INACTIVE("INA");

    private final String legacyCode;

    BorrowerStatus(String legacyCode) {
        this.legacyCode = legacyCode;
    }

    public String getLegacyCode() {
        return legacyCode;
    }
}
