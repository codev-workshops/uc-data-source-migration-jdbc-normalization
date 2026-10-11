package com.workshop.loanservice.domain;

/**
 * Borrower status (CDW_BORR_MSTR.BORR_STAT_CD -> borrowers.status).
 */
public enum BorrowerStatus implements CodedValue {
  ACTIVE("ACT", "ACTIVE", "Active"),
  INACTIVE("INA", "INACTIVE", "Inactive");

  private final String legacyCode;
  private final String value;
  private final String label;

  BorrowerStatus(String legacyCode, String value, String label) {
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
