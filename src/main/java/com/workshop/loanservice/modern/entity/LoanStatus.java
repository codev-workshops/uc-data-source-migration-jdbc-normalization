package com.workshop.loanservice.modern.entity;

/**
 * Loan account status stored in {@code loan_accounts.status}. The label is the text the API
 * returns.
 */
public enum LoanStatus implements LegacyCoded {
    ACTIVE("ACT", "Active"),
    CLOSED("CLO", "Closed"),
    DEFAULT("DFT", "Default"),
    FORBEARANCE("FRB", "Forbearance");

    private final String legacyCode;
    private final String label;

    LoanStatus(String legacyCode, String label) {
        this.legacyCode = legacyCode;
        this.label = label;
    }

    @Override
    public String getLegacyCode() {
        return legacyCode;
    }

    public String getLabel() {
        return label;
    }
}
