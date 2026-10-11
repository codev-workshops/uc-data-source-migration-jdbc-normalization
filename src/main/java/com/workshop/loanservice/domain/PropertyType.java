package com.workshop.loanservice.domain;

/**
 * Property type (CDW_LN_ACCT.PROP_TYP_CD -> loan_accounts.property_type).
 *
 * <p>Stored values follow column_mappings.md (SFR -> Single Family, CND -> Condominium); the
 * mapping leaves MFR/TWN as "etc.", so they follow the same pattern. Labels preserve the
 * legacy API wording.
 */
public enum PropertyType implements CodedValue {
  SINGLE_FAMILY("SFR", "Single Family", "Single Family Residence"),
  CONDOMINIUM("CND", "Condominium", "Condominium"),
  MULTI_FAMILY("MFR", "Multi-Family", "Multi-Family Residence"),
  TOWNHOUSE("TWN", "Townhouse", "Townhouse");

  private final String legacyCode;
  private final String value;
  private final String label;

  PropertyType(String legacyCode, String value, String label) {
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
