package com.workshop.loanservice.migration;

import java.util.List;
import java.util.stream.Collectors;

/** Result of comparing the legacy tables with the migrated modern tables. */
public record ReconciliationReport(List<Check> checks) {

  /** A single legacy-vs-modern comparison. */
  public record Check(String name, Object legacy, Object modern, boolean passed) {

    @Override
    public String toString() {
      return String.format("[%s] %s: legacy=%s modern=%s",
          passed ? "OK" : "MISMATCH", name, legacy, modern);
    }
  }

  public boolean passed() {
    return checks.stream().allMatch(Check::passed);
  }

  public String summary() {
    return checks.stream()
        .map(Check::toString)
        .collect(Collectors.joining("\n  ",
            "Reconciliation " + (passed() ? "PASSED" : "FAILED") + ":\n  ", ""));
  }
}
