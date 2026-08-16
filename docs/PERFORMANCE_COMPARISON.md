# Performance: legacy VARCHAR-everything vs typed modern schema

Produced by `SchemaPerformanceBenchmarkTests`:

```bash
./mvnw test -Pbenchmark
```

The benchmark builds two throwaway H2 databases — one with the CDW schema, one with
the modern schema — loads 20,000 borrowers, 20,000 loans and 100,000 payments into
both, and asks each the same eight questions. Every query pair returns the same
answer; only the schema differs, so the difference in time is the schema's cost.

Timings below are the median of 5 runs after 2 warmups, from one run on the
development machine.

| Query | Legacy (ms) | Modern (ms) | Speed-up |
| --- | --- | --- | --- |
| Filter on an amount range | 5.50 | 1.69 | 3.3x |
| Filter on a date range | 31.91 | 4.10 | 7.8x |
| Sum payment amounts | 13.96 | 6.50 | 2.1x |
| Aggregate by status | 8.41 | 6.27 | 1.3x |
| Indexed lookup of a status subset | 1.88 | 0.20 | 9.4x |
| Top 10 balances | 5.90 | 3.01 | 2.0x |
| Payments joined to their loan | 42.48 | 15.99 | 2.7x |
| Borrower credit-score band | 3.36 | 1.25 | 2.7x |

## What the numbers say

The modern schema wins every query, by 1.3x to 9.4x, and the size of the win tracks
how much work the legacy schema has to redo per row:

- **Per-row conversion dominates.** `WHERE CAST(REPLACE(LN_CURR_BAL, ',', '') AS
  DECIMAL)` has to strip separators and parse a string for all 20,000 rows before it
  can compare anything. The modern column is already a `DECIMAL`.
- **Date filtering is the worst case (7.8x).** `PARSEDATETIME(PMT_DT, 'MM/dd/yyyy')`
  is a format-driven parse per row over 100,000 payments; the modern column is a
  `DATE` the engine compares directly.
- **Conversion also destroys indexability (9.4x).** The modern status filter uses
  `idx_loan_accounts_status` and touches only matching rows. No index can help the
  legacy equivalent when the predicate wraps the column in an expression — and CDW
  has no indexes to begin with.
- **Joins pay twice (2.7x).** Legacy joins `VARCHAR` account numbers, so every
  comparison is a string comparison against an unindexed column; the modern join is
  on `BIGINT` surrogate keys with an index on the foreign key.
- **Aggregation narrows the gap (1.3x).** Grouping visits every row either way, so
  conversion cost is a smaller share of the total.

The effect grows with row count: these are per-row costs, so the legacy penalty
scales linearly while the indexed modern paths do not.

## Caveats

- H2 in-memory, single-threaded, on one machine — the ratios are the point, not the
  absolute milliseconds.
- `QUERY_CACHE_SIZE=0` is set on both connections. Without it H2 returns the
  previous result for a repeated unchanged query and every measurement is a cache
  hit (the first version of this benchmark measured exactly that: ~0.04 ms for
  everything, no difference between schemas).
- Synthetic data is uniform, so selectivity is even across the set; skewed real data
  would change the index-driven cases most.
- Writes are not measured. The modern schema is slower on insert — it enforces
  foreign keys and maintains six indexes, and the migration pays that cost once.
