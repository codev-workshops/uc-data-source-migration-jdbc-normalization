# Data-Source Migration Notes

How the loan-service was migrated from the legacy CDW data source (all-string
tables `CDW_*`) to the modern normalized schema, and the decisions made along the
way. This implements Tasks 1–5 of [`docs/MIGRATION_TASKS.md`](docs/MIGRATION_TASKS.md).
Testing is described in [`TESTING_STRATEGY.md`](TESTING_STRATEGY.md).

## Architecture: dual data source + runtime feature flag

Both data sources are configured and live **simultaneously**; there is no
big-bang cutover. A runtime feature flag selects which one every read uses, so the
app can be flipped between legacy and modern (and back) without a redeploy — this
is the "dual-read mode" from Task 1's bonus.

```
                         ┌──────────────────────┐
GET /api/...  ──▶ LoanService ─▶ DataSourceSelector (AtomicReference<DataSource>)
                         │                 │
                         │        legacy ◀─┴─▶ modern
                         ▼                 ▼
              LegacyLoanDataProvider   ModernLoanDataProvider
                  (CDW_* tables,          (normalized tables,
                   string→type parse)      typed columns)
                         │                 │
                 legacyDataSource      modernDataSource
                 (jdbc:h2:mem:legacydw) (jdbc:h2:mem:moderndw)
```

- **`LegacyDataSourceConfig` / `ModernDataSourceConfig`** each define their own
  `DataSource`, `EntityManagerFactory`, `TransactionManager`, and
  `DataSourceInitializer`, scoped to their own entity package
  (`…​.entity` / `…​.modern.entity`).
- **`LoanService`** is a thin facade. For every call it asks `DataSourceSelector`
  which source is active and delegates to the matching `LoanDataProvider`. The two
  providers implement the same interface and return the same DTOs, so the REST
  controllers and the API contract are unchanged.
- **`DataSourceSelector`** holds the active source in an `AtomicReference` so
  switching is thread-safe. The initial value comes from the
  `loanservice.datasource` property (default `legacy`):

  ```properties
  loanservice.datasource=legacy   # or: modern
  ```

- **`DataSourceAdminController`** exposes the flag at runtime:

  ```
  GET  /api/admin/datasource            -> {"active":"legacy"}
  PUT  /api/admin/datasource/{source}   -> {"active":"modern"}   (legacy|modern)
                                        -> 400 {"error":"Unknown data source: …"}
  ```

  Parsing is case- and whitespace-insensitive; unknown values are rejected and the
  active source is left unchanged.

## ETL migration (`DataMigrationService`)

On startup the service runs an `ApplicationRunner` that ETLs the legacy tables
into the modern tables, inside a single modern-data-source transaction.

- **Extract/Transform/Load** in FK-safe order: borrowers → loan products → loan
  accounts → payments.
- **Type conversions** from the legacy all-`VARCHAR` columns to real types:
  - amounts/decimals: strip thousands separators, parse → `BigDecimal`
    (`"285,000"` → `285000`);
  - integers: parse → `Integer` (credit score, term months);
  - dates: parse `MM/DD/YYYY` → `LocalDate` / `LocalDateTime`;
  - blank/`null` strings convert to `null` rather than throwing.
- **Code expansion** to the modern enum-like form, per `column_mappings.md`:
  status `ACT`→`ACTIVE`, `INA`→`INACTIVE`, `CLO`→`CLOSED`, `DFT`→`DEFAULT`,
  `FRB`→`FORBEARANCE`; payment type `REG`→`REGULAR`, `EXT`→`EXTRA`,
  `PRT`→`PARTIAL`, `PRE`→`PREPAYMENT`; payment status `PST`→`POSTED`,
  `REV`→`REVERSED`, `NSF`→`NSF`, `PND`→`PENDING`. Property codes expand straight to
  the full display label — `SFR`→`Single Family Residence`, `CND`→`Condominium`,
  `TWN`→`Townhouse`, `MFR`→`Multi-Family Residence` — so the read path passes them
  through. Unknown codes pass through unchanged.
- **Foreign-key resolution.** Legacy rows reference each other by business key
  (borrower id, product code, loan account number). The ETL looks up the modern
  surrogate `id` for each reference and wires the real FK. Because the legacy CDW
  tables have **no** foreign-key constraints, the ETL validates every reference and
  **fails loudly** (`IllegalStateException`) if a loan points at a missing
  borrower/product or a payment points at a missing loan — rather than writing
  orphaned rows.
- **Duplicate detection.** Duplicate legacy business keys are rejected as a
  data-quality guard (in practice the legacy primary keys already prevent them).
- **Idempotency.** If the modern tables already contain data the migration is
  skipped, so restarts never duplicate rows. It can be disabled entirely with
  `loanservice.migrate-on-startup=false` (used by tests to invoke `migrate()` on
  demand against controlled data).
- **Row-count validation.** After loading, the ETL asserts the modern row counts
  equal the legacy counts (the `5/5/5/10` dataset), catching any silently dropped
  row.

## Contract-preservation decisions

The migration must keep the REST responses byte-for-byte stable (Tasks 3–4). The
non-obvious decisions:

### `legacy_sequence_number` — preserving `paymentId` (schema addition)

The API contract requires `PaymentDto.paymentId` to be the legacy `PMT_SEQ_NBR`
string (e.g. `PMT-2025120001`). The modern `payments` table uses an
auto-generated numeric surrogate `id`, which cannot reproduce that string.

`data/mappings/column_mappings.md` (payments section) already anticipates this —
the `PMT_SEQ_NBR` row reads:

> `PMT_SEQ_NBR` | VARCHAR(20) | `id` | BIGINT | **Auto-generated; legacy ID stored if needed**

Acting on that "legacy ID stored if needed" guidance, a dedicated column was
added to the modern schema:

```sql
legacy_sequence_number VARCHAR(20) UNIQUE
```

- Added to `data/modern-schema/modern_tables.sql`, `src/main/resources/schema-modern.sql`,
  and the modern `Payment` entity.
- Populated by the ETL from `PMT_SEQ_NBR`, and returned by
  `ModernLoanDataProvider` as `paymentId`.
- `UNIQUE` because the legacy sequence number is itself a unique business key.

This is a purely additive change (the auto-generated `id` remains the primary key)
and it resolves the "payment ID gap" flagged during the safety-net iteration.

### Dates stay `MM/DD/YYYY` strings

The modern schema stores real `DATE`/`TIMESTAMP` columns, but the API has always
returned dates as `MM/DD/YYYY` strings. `ModernLoanDataProvider` re-serializes
`LocalDate` back to `MM/dd/yyyy` so the contract is unchanged. (The legacy provider
passes the already-formatted strings through.)

### Payment ordering

Payments for a loan are returned newest-first. The modern repository reproduces the
legacy `ORDER BY PMT_DT DESC` via
`findByLoanAccount_AccountNumberOrderByPaymentDateDesc`, so
`LN-2019-00142` yields `PMT-2025120001` then `PMT-2025110001` on both sources.

### Display strings

Modern tables store expanded codes (`ACTIVE`, `REGULAR`, `POSTED`); both providers
translate them to the human-readable API form (`"Active"`, `"Regular"`,
`"Posted"`). `property_type` is stored in the full display form
(`"Single Family Residence"`) so it is a read-time pass-through — the mapping doc's
terse `"Single Family"` example would have broken the contract.

### Numeric representation

Amounts are `BigDecimal`. The string-typed legacy source serializes `285000`
while the modern `DECIMAL(…,2)` column serializes `285000.00`; these are the same
value. The contract suite compares numbers **by value** (JSONAssert), so this
representational difference is accepted while any real value drift fails.

## Acceptance gate

The migration is accepted by the parameterized `ApiContractTest` running the
**identical** golden-master assertions against **both** the legacy and modern
sources (STRICT, numeric-aware JSON, empty accepted-difference allow-list), plus
the real-data correctness tests described in `TESTING_STRATEGY.md`. Coverage is
enforced at a 95% line minimum by JaCoCo (`mvn verify`); the suite is at ~99.5%.

## Configuration & cleanup (Task 5 deliverables)

- **`application.properties` now defaults to the modern schema**
  (`loanservice.datasource=modern`). The migration is complete, so the app reads
  from the normalized tables by default. The legacy CDW source stays fully wired
  as a **runtime fallback** and can be re-selected without a restart via
  `PUT /api/admin/datasource/legacy` (or by setting the property back to `legacy`).
- **Legacy entities/repositories are intentionally retained, not removed or
  `@Deprecated`.** `MIGRATION_TASKS.md` Task 5 offers "remove **or** flag legacy
  entities as deprecated", but the chosen dual-read architecture (the bonus
  feature-flag) keeps `LegacyLoanDataProvider` and the legacy repositories as a
  first-class, supported runtime path — deprecating code that is still actively
  selectable would be misleading. They should be removed only once the feature
  flag is retired and legacy is decommissioned.

## Task-by-task status (`docs/MIGRATION_TASKS.md`)

| Task | Status | Where |
|------|--------|-------|
| 1 — Modern schema & entities | Done | `modern/entity/*`, `modern/repository/*`, `Legacy/ModernDataSourceConfig` |
| 2 — Data migration script | Done | `migration/DataMigrationService` (transforms, FK resolution, edge cases, validation) |
| 3 — Rewire to modern | Done | `LoanService` facade + `ModernLoanDataProvider` (no string parsing on the modern path); API contract unchanged |
| 4 — Validation tests | Done | `contract/ApiContractTest` (both sources), `integration/*` correctness tests |
| 5 — Documentation | Done | this file, `TESTING_STRATEGY.md`, `application.properties` default → modern, legacy-retention decision above |
| Bonus — Dual-read mode | Done | `DataSourceSelector` + `DataSourceAdminController` (see architecture section) |
| Bonus — Reconciliation queries | Done | `data/reconciliation/reconciliation_queries.sql` |
| Bonus — Performance comparison | Done | `mvn test -Pperformance` → `PerformanceComparisonTest` (see below) |

## Bonus: reconciliation queries

`data/reconciliation/reconciliation_queries.sql` cross-checks the migration
independently of the application code: row counts (`5/5/5/10`), summed financial
totals (comma-stripped legacy strings vs typed modern decimals), business-key set
equality (borrower/product/account/`PMT_SEQ_NBR`), and referential-integrity
orphan checks. Section 4 uses H2 `CREATE LINKED TABLE` so both databases can be
reconciled from a single connection, returning explicit deltas (all zero = clean).

## Bonus: performance comparison

`PerformanceComparisonTest` (JUnit, `@Tag("performance")`, excluded from the normal
build) benchmarks the **same** read operations against both providers over the
identical migrated `5/5/5/10` data set, on the same in-memory H2, with JIT warmup
and 20 000 timed iterations per operation. Run it and regenerate the numbers with:

```bash
mvn test -Pperformance      # prints a table and writes target/performance-comparison.md
```

This is a more controlled measure than timing whole test cases (which are
dominated by Spring/MockMvc startup): the DB round-trip is a shared constant, so
the delta isolates the schema/access-pattern difference.

Representative run (µs per call; `speedup` = legacy ÷ modern, >1 means modern
faster — absolute numbers are machine/JVM-dependent, the ratio is the signal):

| Operation | Legacy (µs/call) | Modern (µs/call) | Speedup |
|-----------|-----------------:|-----------------:|--------:|
| getAllLoans | 87.35 | 112.16 | 0.78x |
| getAllBorrowers | 37.65 | 41.89 | 0.90x |
| getLoanById | 25.47 | 60.11 | 0.42x |
| getPaymentsByLoan | 32.49 | 52.99 | 0.61x |
| getBorrowerById | 69.12 | 74.65 | 0.93x |
| **total** | **252.08** | **341.81** | **0.74x** |

**Honest interpretation.** For *these* access patterns the modern schema is
somewhat **slower**, not faster. The reason is structural, not about typing: the
legacy CDW tables are **denormalized** (borrower name/address are embedded in
`CDW_LN_ACCT`), so a loan read is a single-table fetch; the normalized modern
schema resolves real foreign keys, so the same read loads the `LoanAccount` plus
its `Borrower` and `LoanProduct` (and, for a borrower, the nested loan list) —
more round-trips. On this tiny dataset that join/lookup cost outweighs the
string→type parsing that the modern schema eliminates (`getLoanById` is the
extreme: 1 legacy query vs 3 modern entity loads).

The modern schema's payoff is **correctness, type-safety, referential integrity,
and maintainability**, not raw latency for full-object denormalized-style reads.
Where the typed/indexed schema is expected to win is filtered/range/aggregate
queries at scale — e.g. `WHERE origination_date > ?` or `SUM(original_amount)`,
which on legacy require per-row `REPLACE()`/`CAST()`/date-string parsing that
defeats indexes, whereas the modern columns are natively typed and indexed. That
workload is out of scope for the current five endpoints (all full-object reads),
so it is called out here rather than benchmarked.
