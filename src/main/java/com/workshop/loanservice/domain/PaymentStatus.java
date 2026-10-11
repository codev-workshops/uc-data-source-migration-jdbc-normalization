package com.workshop.loanservice.domain;

/**
 * Payment status (CDW_PMT_HIST.PMT_STAT_CD -> payments.status).
 */
public enum PaymentStatus implements CodedValue {
  POSTED("PST", "POSTED", "Posted"),
  REVERSED("REV", "REVERSED", "Reversed"),
  NSF("NSF", "NSF", "Non-Sufficient Funds"),
  PENDING("PND", "PENDING", "Pending");

  private final String legacyCode;
  private final String value;
  private final String label;

  PaymentStatus(String legacyCode, String value, String label) {
    this.legacyCode = legacyCode;
    this.value = value;
    this.label = label;
  }

  @Override
  public String legacyCode() {
    return legacyCode;
  }

  @Override
  public String value() {
    return value;
  }

  @Override
  public String label() {
    return label;
  }
}
