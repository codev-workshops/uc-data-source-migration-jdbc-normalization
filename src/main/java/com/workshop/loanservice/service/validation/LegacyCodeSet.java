package com.workshop.loanservice.service.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Exhaustive dictionaries for every legacy code column that is expanded during migration.
 *
 * <p>Expanded values are the exact strings exposed by the current API so that the contract does not
 * change; the modern schema's upper-case enum values are documented in {@code
 * data/mappings/column_mappings.md}.
 */
public enum LegacyCodeSet {
  /** {@code CDW_BORR_MSTR.BORR_STAT_CD}. */
  BORR_STAT_CD(entries("ACT", "Active", "INA", "Inactive")),

  /** {@code CDW_LN_ACCT.LN_STAT_CD}. */
  LN_STAT_CD(entries("ACT", "Active", "CLO", "Closed", "DFT", "Default", "FRB", "Forbearance")),

  /** {@code CDW_PMT_HIST.PMT_TYP_CD}. */
  PMT_TYP_CD(entries("REG", "Regular", "EXT", "Extra", "PRT", "Partial", "PRE", "Prepayment")),

  /** {@code CDW_PMT_HIST.PMT_STAT_CD}. */
  PMT_STAT_CD(
      entries("PST", "Posted", "REV", "Reversed", "NSF", "Non-Sufficient Funds", "PND", "Pending")),

  /** {@code CDW_LN_ACCT.PROP_TYP_CD}. */
  PROP_TYP_CD(
      entries(
          "SFR",
          "Single Family Residence",
          "CND",
          "Condominium",
          "MFR",
          "Multi-Family Residence",
          "TWN",
          "Townhouse")),

  /** {@code CDW_LN_PROD.PROD_STAT_CD}. */
  PROD_STAT_CD(entries("ACT", "Active", "INA", "Inactive"));

  private final Map<String, String> expansions;

  LegacyCodeSet(Map<String, String> expansions) {
    this.expansions = Collections.unmodifiableMap(expansions);
  }

  /** Returns the expanded value for {@code code}, if it is part of this dictionary. */
  public Optional<String> expand(String code) {
    return Optional.ofNullable(expansions.get(code));
  }

  /** Returns the full code-to-expansion dictionary. */
  public Map<String, String> expansions() {
    return expansions;
  }

  private static Map<String, String> entries(String... pairs) {
    Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put(pairs[i], pairs[i + 1]);
    }
    return map;
  }
}
