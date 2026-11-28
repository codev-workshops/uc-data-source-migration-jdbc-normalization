package com.workshop.loanservice.modern.entity;

public enum LoanStatus {
    ACTIVE("Active"),
    CLOSED("Closed"),
    DEFAULT("Default"),
    FORBEARANCE("Forbearance");

    private final String label;

    LoanStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
