package com.workshop.loanservice.modern.entity;

public enum PaymentType {
    REGULAR("Regular"),
    EXTRA("Extra"),
    PARTIAL("Partial"),
    PREPAYMENT("Prepayment");

    private final String label;

    PaymentType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
