package com.workshop.loanservice.domain;

/**
 * Loan status (CDW_LN_ACCT.LN_STAT_CD -> loan_accounts.status).
 */
public enum LoanStatus implements CodedValue {
  ACTIVE("ACT", "ACTIVE", "Active"),
  CLOSED("CLO", "CLOSED", "Closed"),
  DEFAULT("DFT", "DEFAULT", "Default"),
  FORBEARANCE("FRB", "FORBEARANCE", "Forbearance");

  private final String legacyCode;
  private final String value;
  private final String label;

  LoanStatus(String legacyCode, String value, String label) {
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
