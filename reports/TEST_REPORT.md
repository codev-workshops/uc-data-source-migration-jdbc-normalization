# Functional test report

Generated 2026-08-02 08:39 UTC by `scripts/run-tests.sh`.

**All green** — 76 unit tests and 64 integration tests.

Coverage: 90.0% of instructions, 70.2% of branches (JaCoCo, `target/site/jacoco/index.html`).

## Unit tests

| Suite | What it covers | Tests | Failures | Errors | Skipped | Time (s) |
|---|---|---:|---:|---:|---:|---:|
| `LoanServiceApplicationTests` | The context starts | 1 | 0 | 0 | 0 | 5.62 |
| `PageRequestsTest` | v2 paging limits and the sort allow list, including injection attempts | 16 | 0 | 0 | 0 | 0.04 |
| `CodeTranslatorTest` | Legacy codes to canonical values, and back to the v1 display labels | 31 | 0 | 0 | 0 | 0.03 |
| `LegacyValueParserTest` | Legacy string values: dates, comma amounts, integers, and the malformed ones | 19 | 0 | 0 | 0 | 0.04 |
| `NoDynamicSqlSourceGuardTest` | Static guard: no assembled SQL, no raw JDBC, no PII in log statements | 3 | 0 | 0 | 0 | 0.03 |
| `V1FormatTest` | The exact v1 output formats: MM/DD/YYYY, money scale, composed addresses | 6 | 0 | 0 | 0 | 0.11 |

## Integration tests

| Suite | What it covers | Tests | Failures | Errors | Skipped | Time (s) |
|---|---|---:|---:|---:|---:|---:|
| `LoanV2ApiIT` | v2 paging, keyset cursors, sorting, ISO dates, and no SSN in the payload | 12 | 0 | 0 | 0 | 0.68 |
| `V1GoldenContractDualReadIT` | v1 responses byte-for-byte, with shadow-read comparison on | 6 | 0 | 0 | 0 | 0.89 |
| `V1GoldenContractLegacyIT` | v1 responses byte-for-byte, served from the legacy store | 6 | 0 | 0 | 0 | 5.93 |
| `V1GoldenContractModernIT` | v1 responses byte-for-byte, served from the migrated store | 6 | 0 | 0 | 0 | 0.73 |
| `MigrationBadDataIT` | Unparseable and dangling rows: rejection, strict vs lenient, re-run safety | 5 | 0 | 0 | 0 | 0.58 |
| `MigrationIT` | Backfill correctness: counts, types, foreign keys, preserved hashes, idempotency | 7 | 0 | 0 | 0 | 0.56 |
| `ProductCatalogCacheIT` | The one cache: product ids served from Caffeine, and invalidated correctly | 2 | 0 | 0 | 0 | 0.03 |
| `ObservabilityIT` | Prometheus exposure, pool and JVM metrics, and the actuator lockdown | 9 | 0 | 0 | 0 | 1.04 |
| `SqlInjectionIT` | Injection payloads through paths, sorts and paging parameters | 5 | 0 | 0 | 0 | 0.09 |
| `DualWritePaymentIT` | Dual write: the payment lands in both stores and reconciles | 2 | 0 | 0 | 0 | 0.60 |
| `PaymentPostingConcurrencyIT` | Concurrent posting: no lost updates, duplicates rejected, throughput | 4 | 0 | 0 | 0 | 2.01 |

## What is deliberately not covered here

- Throughput and latency: see `reports/LOAD_TEST_REPORT.md`.
- The 500k-row scale analysis: see `docs/ARCHITECTURE_ANALYSIS.md`.
- Static and runtime injection review: see `reports/SECURITY_REPORT.md`.
