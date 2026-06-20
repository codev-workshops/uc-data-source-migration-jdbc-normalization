# Data Source Migration Notes — Legacy CDW → Modern Normalized Schema

This document records the design, decisions, patterns, and verification for the
migration of the loan service from the legacy all-VARCHAR CDW schema to a modern,
normalized, strongly-typed schema. The overriding constraint was: **the public
REST API contract must remain byte-for-byte identical.**

## 1. Goals & constraints

- Move from denormalized, string-typed `CDW_*` tables to normalized tables with
  proper types (`DATE`, `DECIMAL`, `INTEGER`, `BOOLEAN`) and foreign keys.
- Keep both schemas available: modern is primary; legacy is the migration source
  and a reversible read-time fallback (dual-read).
- Preserve the API contract exactly: number scale, `MM/DD/YYYY` dates, expanded
  human-readable statuses/types, null fields, list ordering, HTTP/error behavior,
  payment identifiers, and current routes.

## 2. Architecture

```
Controllers ─► LoanService (facade)
                  │  selects provider via DataSourceModeHolder (dual-read flag)
                  ├─► ModernLoanDataProvider ─► modern repositories ─► moderndb (PRIMARY)
                  └─► LegacyLoanDataProvider ─► legacy repositories ─► legacydw

MigrationRunner (ApplicationRunner) ─► DataMigrationService
    reads legacy repositories, writes modern repositories (idempotent, transactional)
```

- **Two datasources**, configured programmatically:
  - `config/LegacyDataSourceConfig` — `jdbc:h2:mem:legacydw`, initialized with
    `schema-legacy.sql` + `data-legacy.sql`. Own EMF + `legacyTransactionManager`.
    Scans `legacy.{entity,repository}`.
  - `config/ModernDataSourceConfig` — `jdbc:h2:mem:moderndb` (**`@Primary`**),
    initialized with `schema-modern.sql`. Own EMF + `modernTransactionManager`
    (`@Primary`). Scans `modern.{entity,repository}`.
  - Schema/data are initialized via `DataSourceInitializer` beans, so Boot's
    single-datasource auto-init (`spring.sql.init.*`) is intentionally unused.
- **Separate persistence models** in separate packages (`legacy.*`, `modern.*`)
  so the two mappings never collide and the legacy model can be deprecated
  independently.

## 3. Modern schema decisions

- Normalized: `borrowers`, `loan_products`, `loan_accounts`, `payments`.
- `loan_accounts` references `borrowers` and `loan_products` by FK
  (`@ManyToOne`), eliminating the legacy denormalized borrower columns.
- Surrogate `BIGINT` auto-increment PKs; **stable business keys** kept as unique
  columns: `borrowers.external_id`, `loan_products.code`,
  `loan_accounts.account_number`, `payments.external_id`.
- `payments.external_id` preserves the legacy `PMT_SEQ_NBR` so the API can keep
  returning the original payment identifier (e.g. `PMT-2025120001`).
- Repositories expose business-key lookups (`findByExternalId`, `findByCode`,
  `findByAccountNumber`, `findByBorrower_ExternalIdOrderByIdAsc`,
  `findByLoanAccount_AccountNumberOrderByPaymentDateDesc`) and deterministic
  ordering (`findAllByOrderByIdAsc`) so list order is stable.

## 4. Migration design (patterns)

- **Ordered**: `borrowers → loan_products → loan_accounts → payments`, so foreign
  keys resolve by business key as each phase completes.
- **Idempotent**: each record is looked up by business key first; if present it is
  **skipped**. Re-running (including on every restart) produces no duplicates.
- **Transactional**: the whole run executes in one `modernTransactionManager`
  transaction (`@Transactional(transactionManager = "modernTransactionManager")`).
- **Resilient + auditable**: per-record conversion failures and missing references
  are captured in a `MigrationReport` (per-table `inserted/updated/skipped/failed`
  plus `Failure{table, businessKey, field, invalidValue, message}`) instead of
  aborting. Valid records still commit.
- **Strict conversion** (`migration/TypeConverter`): dates/timestamps
  (`MM/dd/uuuu`, `STRICT`), decimals (strips thousands separators, preserves
  scale), integers, and code expansion (loan status, payment type/status,
  property type, product active). Any bad/unknown value raises a
  `ConversionException` carrying the offending `field` + `invalidValue`.

## 5. Contract-preservation decisions (the tricky bits)

All formatting lives in `ModernLoanDataProvider` (presentation only):

| Concern | Legacy behavior | How modern reproduces it |
|---|---|---|
| Dates | raw `MM/DD/YYYY` strings passed through | `LocalDate` formatted with `MM/dd/uuuu` |
| `originalAmount` | `"285,000"` → `285000` (scale 0) | stored `DECIMAL(12,2)`; rendered via `stripTrailingZeros()` so whole dollars print without decimals |
| `currentBalance` / rate | source scale echoed (`142567.90`, `4.250`) | column scales (`DECIMAL(12,2)`, `DECIMAL(5,3)`) match the source scales |
| `borrowerName` | first+last from denormalized loan columns | first+last from the `borrowers` FK (seed values are identical) |
| `fullName` | first + `" M."` + last | same, from `borrowers.middle_initial` |
| statuses/types | code expansion (`ACT`→`Active`, `SFR`→…) | converter stores canonical (`ACTIVE`), provider expands to display (`Active`) |
| `paymentId` | `PMT_SEQ_NBR` | `payments.external_id` |
| list ordering | incidental insert order | explicit `ORDER BY id` |
| not-found | raw `RuntimeException` → HTTP 500 | same exception/message preserved |
| unknown-loan payments | `200 []` | same (empty list) |

### Known assumption — `originalAmount`
The contract renders whole-dollar original amounts without decimals. **All source
original amounts are whole dollars.** `wholeDollar()` strips the scale the
`DECIMAL(12,2)` column adds back, leaving genuinely fractional values untouched.
If a fractional original amount is ever introduced, the contract and this
formatting must be revisited.

## 6. Dual-read feature flag (bonus 1)

- `loanservice.datasource.mode` (`MODERN` default / `LEGACY`) sets the initial
  mode; `DataSourceModeHolder` holds it (volatile) and is switchable at runtime.
- `LoanService` is a thin facade that delegates to the active `LoanDataProvider`.
- Admin endpoint (kept under `/api/admin` so the public contract is unaffected):
  - `GET /api/admin/datasource-mode` → `{ "mode": "MODERN" }`
  - `PUT /api/admin/datasource-mode/{MODERN|LEGACY}` → flips at runtime
- A test (`DualReadModeTest`) asserts **modern and legacy return byte-identical
  JSON** for all five endpoints, proving the dual-read paths are equivalent.

> Note: the dual-read fallback intentionally keeps the legacy parsing code alive
> (encapsulated in `LegacyLoanDataProvider`). It is **not** used on the default
> modern path; flip the flag to `LEGACY` to exercise it.

- Invalid mode values are rejected with **HTTP 400** and leave the active mode
  unchanged.

### 6.1 Rollback procedure
The dual-read flag *is* the rollback mechanism — no redeploy or data restore is
needed:

1. **Instant runtime rollback:** `PUT /api/admin/datasource-mode/LEGACY`. The read
   path immediately serves from the legacy CDW tables (byte-identical responses,
   per `DualReadModeTest`). Flip back with `.../MODERN`.
2. **Persistent rollback:** set `loanservice.datasource.mode=LEGACY` in
   `application.properties` (or `-Dloanservice.datasource.mode=LEGACY`) and
   restart, so the app boots in legacy mode.
3. **Safety of the underlying data:** the legacy datasource is never written to by
   the application; the migration only *reads* legacy and *writes* modern. So the
   legacy data remains a pristine source of truth at all times.
4. **Re-running the migration is safe:** it is idempotent (skips existing records
   by business key), so a restart never duplicates data. To rebuild the modern
   side from scratch, restart the app (the in-memory modern DB is recreated and
   re-migrated); for a persistent DB, `TRUNCATE` the modern tables and restart.

## 7. Reconciliation (bonus 2)

`data/reconciliation/reconciliation_queries.sql` contains per-database queries
(the two H2 DBs are separate) for: row counts, monetary totals (legacy amounts
are `REPLACE`+`CAST`-cleaned), business-key coverage, FK integrity (modern),
status-code distribution, and a per-row spot-check extract to diff. Expected
result on seed data: counts and totals match; FK-integrity queries return 0 rows.

Programmatic equivalents are asserted in `ModernSchemaIntegrationTest`
(`modernRowCountsReconcileWithLegacy`, `everyLegacyBusinessKeyExistsInModern`).

## 8. Performance comparison (bonus 3)

`PerformanceComparisonTest` benchmarks the legacy (parse-on-read) vs modern
(typed) read paths (10k iterations, after warmup). Representative local results
**after the N+1 fix** (LAZY associations + fetch-join read queries):

| Operation | Legacy µs/op | Modern µs/op | Speedup |
|---|---|---|---|
| `getAllLoans` | ~89 | ~46 | ~1.95x |
| `getBorrowerById` | ~106 | ~70 | ~1.51x |
| `getPaymentsByLoan` | ~32 | ~27 | ~1.18x |

**Interpretation (honest):** the modern path is faster across the board. The win
comes from (1) no per-read VARCHAR→type parsing and (2) collapsing each endpoint
to a single fetch-joined query. The gap was initially smaller (the modern path was
even marginally slower) because the relationships defaulted to `EAGER` and
`getAllLoans` issued a select per loan for borrower/product (classic N+1); see
§8.1. Absolute numbers vary by machine; the comparison is the point.

### 8.1 N+1 elimination
`LoanAccount.borrower/product` and `Payment.loanAccount` are mapped
`@ManyToOne(fetch = LAZY)`. The read queries in `LoanAccountRepository` and
`PaymentRepository` use JPQL `join fetch`, so every endpoint loads its full object
graph in one query. Verified with SQL logging (`--spring.jpa.show-sql=true`):
`GET /api/loans` issues **1** statement (previously ~11), `.../payments` **1**,
`/api/borrowers` **1**, `/api/borrowers/{id}` **2** (borrower + its loans).

## 9. Cleanup / deprecation

- Legacy entities and repositories are annotated `@Deprecated` with javadoc
  pointing to the modern model. They remain on the classpath because they back
  the migration source and the legacy dual-read fallback. Classes that
  legitimately use them (`DataMigrationService`, `LegacyLoanDataProvider`) carry
  a scoped `@SuppressWarnings("deprecation")`.

## 10. Tests (contract gate)

`mvn test` runs the full suite (all green):

- `ApiContractRegressionTest` — byte-identical golden-file checks (the contract gate)
- `ModernSchemaIntegrationTest` — repository business-key lookups, reconciliation, idempotency
- `DataMigrationServiceTest` — idempotency, malformed-data, missing-reference, successful inserts
- `TypeConverterTest` — strict parsing + contextual errors
- `DualReadModeTest` — modern/legacy parity + runtime toggle
- `PerformanceComparisonTest` — functional parity + benchmark logging
