package com.workshop.loanservice.domain;

/**
 * Payment type (CDW_PMT_HIST.PMT_TYP_CD -> payments.type).
 */
public enum PaymentType implements CodedValue {
  REGULAR("REG", "REGULAR", "Regular"),
  EXTRA("EXT", "EXTRA", "Extra"),
  PARTIAL("PRT", "PARTIAL", "Partial"),
  PREPAYMENT("PRE", "PREPAYMENT", "Prepayment");

  private final String legacyCode;
  private final String value;
  private final String label;

  PaymentType(String legacyCode, String value, String label) {
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
