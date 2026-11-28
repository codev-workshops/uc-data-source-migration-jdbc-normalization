package com.workshop.loanservice.modern.entity;

public enum PaymentStatus {
    POSTED("Posted"),
    REVERSED("Reversed"),
    NSF("Non-Sufficient Funds"),
    PENDING("Pending");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
