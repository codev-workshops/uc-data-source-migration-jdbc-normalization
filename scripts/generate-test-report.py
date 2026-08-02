#!/usr/bin/env python3
"""Renders reports/TEST_REPORT.md from the Surefire, Failsafe and JaCoCo output.

Maven leaves its results as XML nobody reads. This turns the last run into one page: what each
suite covers, whether it passed, how long it took, and the coverage that came out of it.
"""

import csv
import glob
import os
import xml.etree.ElementTree as ElementTree
from datetime import datetime, timezone

REPORT = "reports/TEST_REPORT.md"

# Why each suite exists, so the report explains the run rather than just counting it.
PURPOSE = {
    "LegacyValueParserTest": "Legacy string values: dates, comma amounts, integers, and the malformed ones",
    "CodeTranslatorTest": "Legacy codes to canonical values, and back to the v1 display labels",
    "V1FormatTest": "The exact v1 output formats: MM/DD/YYYY, money scale, composed addresses",
    "PageRequestsTest": "v2 paging limits and the sort allow list, including injection attempts",
    "NoDynamicSqlSourceGuardTest": "Static guard: no assembled SQL, no raw JDBC, no PII in log statements",
    "LoanServiceApplicationTests": "The context starts",
    "MigrationIT": "Backfill correctness: counts, types, foreign keys, preserved hashes, idempotency",
    "MigrationBadDataIT": "Unparseable and dangling rows: rejection, strict vs lenient, re-run safety",
    "V1GoldenContractLegacyIT": "v1 responses byte-for-byte, served from the legacy store",
    "V1GoldenContractModernIT": "v1 responses byte-for-byte, served from the migrated store",
    "V1GoldenContractDualReadIT": "v1 responses byte-for-byte, with shadow-read comparison on",
    "LoanV2ApiIT": "v2 paging, keyset cursors, sorting, ISO dates, and no SSN in the payload",
    "SqlInjectionIT": "Injection payloads through paths, sorts and paging parameters",
    "ObservabilityIT": "Prometheus exposure, pool and JVM metrics, and the actuator lockdown",
    "PaymentPostingConcurrencyIT": "Concurrent posting: no lost updates, duplicates rejected, throughput",
    "DualWritePaymentIT": "Dual write: the payment lands in both stores and reconciles",
    "ProductCatalogCacheIT": "The one cache: product ids served from Caffeine, and invalidated correctly",
}


def suites(directory):
    found = []
    for path in sorted(glob.glob(os.path.join(directory, "TEST-*.xml"))):
        root = ElementTree.parse(path).getroot()
        found.append({
            "name": root.get("name", "").split(".")[-1],
            "tests": int(root.get("tests", 0)),
            "failures": int(root.get("failures", 0)),
            "errors": int(root.get("errors", 0)),
            "skipped": int(root.get("skipped", 0)),
            "time": float(root.get("time", 0)),
        })
    return found


def table(rows):
    lines = ["| Suite | What it covers | Tests | Failures | Errors | Skipped | Time (s) |",
             "|---|---|---:|---:|---:|---:|---:|"]
    for row in rows:
        lines.append("| `{name}` | {purpose} | {tests} | {failures} | {errors} | {skipped} | {time:.2f} |".format(
            purpose=PURPOSE.get(row["name"], ""), **row))
    return "\n".join(lines)


def totals(rows):
    return {key: sum(row[key] for row in rows) for key in ("tests", "failures", "errors", "skipped")}


def coverage():
    path = "target/site/jacoco/jacoco.csv"
    if not os.path.exists(path):
        return None
    covered = missed = branch_covered = branch_missed = 0
    with open(path, newline="") as handle:
        for row in csv.DictReader(handle):
            missed += int(row["INSTRUCTION_MISSED"])
            covered += int(row["INSTRUCTION_COVERED"])
            branch_missed += int(row["BRANCH_MISSED"])
            branch_covered += int(row["BRANCH_COVERED"])
    instructions = covered + missed
    branches = branch_covered + branch_missed
    return {
        "instructions": 100.0 * covered / instructions if instructions else 0,
        "branches": 100.0 * branch_covered / branches if branches else 0,
    }


def main():
    unit = suites("target/surefire-reports")
    integration = suites("target/failsafe-reports")
    unit_totals = totals(unit)
    integration_totals = totals(integration)
    failed = (unit_totals["failures"] + unit_totals["errors"]
              + integration_totals["failures"] + integration_totals["errors"])

    out = ["# Functional test report", "",
           "Generated {} by `scripts/run-tests.sh`.".format(
               datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")), "",
           "**{}** — {} unit tests and {} integration tests.".format(
               "All green" if failed == 0 else "{} FAILING".format(failed),
               unit_totals["tests"], integration_totals["tests"]), ""]

    stats = coverage()
    if stats:
        out += ["Coverage: {:.1f}% of instructions, {:.1f}% of branches (JaCoCo, "
                "`target/site/jacoco/index.html`).".format(stats["instructions"], stats["branches"]), ""]

    out += ["## Unit tests", "", table(unit), "",
            "## Integration tests", "", table(integration), "",
            "## What is deliberately not covered here", "",
            "- Throughput and latency: see `reports/LOAD_TEST_REPORT.md`.",
            "- The 500k-row scale analysis: see `docs/ARCHITECTURE_ANALYSIS.md`.",
            "- Static and runtime injection review: see `reports/SECURITY_REPORT.md`.", ""]

    os.makedirs("reports", exist_ok=True)
    with open(REPORT, "w") as handle:
        handle.write("\n".join(out))
    print("Wrote " + REPORT)


if __name__ == "__main__":
    main()
