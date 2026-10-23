# Data Source Migration Notes

Migration of `loan-service` from the legacy all-`VARCHAR` CDW tables to the normalized modern schema
(`data/modern-schema/modern_tables.sql`), with the REST contract unchanged.

## Architecture

| Concern | Legacy (deprecated) | Modern (primary) |
|---------|---------------------|------------------|
| Tables | `CDW_BORR_MSTR`, `CDW_LN_PROD`, `CDW_LN_ACCT`, `CDW_PMT_HIST` | `borrowers`, `loan_products`, `loan_accounts`, `payments` |
| Entities | `entity.legacy.Legacy*` | `entity.modern.*` |
| Repositories | `repository.legacy.Legacy*Repository` | `repository.modern.*Repository` |
| DataSource config | `LegacyDataSourceConfig` (`legacy.datasource.*`, `legacy.sql.init.*`) | `ModernDataSourceConfig` (`spring.datasource.*`, `@Primary`) |
| H2 database | `jdbc:h2:mem:legacydw` | `jdbc:h2:mem:modern` |
| DDL | `schema-legacy.sql` + `data-legacy.sql` | `schema-modern.sql` (`ddl-auto=validate`) |

Each datasource has its own `EntityManagerFactory` and `TransactionManager`, and Spring Data repositories are
bound to them by package, so both sources coexist while the migration runs. The modern source is `@Primary`,
so anything that injects an unqualified `DataSource`/`EntityManager` uses the modern schema.

### Dual read (`loan-service.read-source`)

`LoanService` (used by the controllers) delegates to a `LoanDataSource`; exactly one implementation is created
at startup via `@ConditionalOnProperty`:

| `loan-service.read-source` | Bean | Reads | Transaction manager |
|----------------------------|------|-------|---------------------|
| `modern` (default, also when unset) | `ModernLoanDataSource` | `repository.modern.*` | `transactionManager` (primary) |
| `legacy` | `LegacyLoanDataSource` | `repository.legacy.Legacy*Repository` | `legacyTransactionManager` |

Switch by restart, e.g. `LOAN_SERVICE_READ_SOURCE=legacy` or `--loan-service.read-source=legacy`. A restart
was chosen over `@RefreshScope` because that needs Spring Cloud Context and would swap the source mid-traffic.
`LegacyLoanDataSource` reuses `LegacyValueParser` (trim, comma-stripped amounts, `MM/DD/YYYY` dates) and
`LegacyCodeMapper` (status/property/payment codes) - the same rules as the migration - and builds the DTOs
through the shared `ApiFormats`, so both sources return the same JSON. Like the modern path it takes
`borrowerName` from the borrower master, maps unmapped loan status codes to the raw code, and sorts payments by
the parsed date (not the `VARCHAR`). A malformed credit score is returned as `null`, as the migration stores it;
rows the migration would reject (malformed required values, unknown payment codes, a `BORR_ID`/`PROD_CD` with
no master row) raise `LegacyDataException` instead of being rendered.

## Migration flow

`DataMigrationRunner` (`CommandLineRunner`, `migration.run-on-startup=true`) takes a ShedLock lease
(`legacy-to-modern-migration` in the modern `shedlock` table, `migration.lock-at-most-for=PT30M`) and calls
`DataMigrationService.migrate()` in one transaction. Another instance sharing the database that finds the lease
taken logs `Legacy -> modern migration SKIPPED` and starts without migrating, instead of failing on unique keys;
the lease is released once the transaction has committed or rolled back, and expires on its own if the holder dies.

1. Read every row through the `Legacy*Repository` interfaces.
2. Insert in FK order: borrowers → loan_products → loan_accounts → payments.
3. Resolve FKs by business key: `BORR_ID` → `borrowers.external_id`, `PROD_CD` → `loan_products.code`,
   `LN_ACCT_NBR` → `loan_accounts.account_number`.
4. Reconcile and log a `MigrationReport`. With `migration.fail-on-error=true`, a failed report throws
   `MigrationFailedException` inside the transaction, so the whole run rolls back (no partial import) and startup
   aborts. With `false`, valid rows are committed and failures are only reported.

All string-to-type conversion lives in `migration.LegacyValueParser` and `migration.LegacyCodeMapper`.
`LoanService` only formats typed values for the existing DTOs.

## Transformation decisions

- **Amounts**: thousands separators stripped, parsed to `BigDecimal` (never `double`).
- **Dates**: strict `MM/dd/uuuu` (rejects e.g. `02/30/2020`); legacy `*_CRET_DT`/`*_UPDT_DT` become midnight timestamps.
- **Codes**: expanded to enums stored as `VARCHAR` (`@Enumerated(STRING)`): `BorrowerStatus`, `LoanStatus`,
  `PaymentType`, `PaymentStatus`. Display labels the API has always returned (e.g. `Non-Sufficient Funds`) are
  kept on the enum (`getDisplayName()`), not in the service.
- **`ACT`/`INA`**: borrower → `ACTIVE`/`INACTIVE`; product → `is_active` `true`/`false`. Blank falls back to the modern column default.
- **Property type**: stored expanded (`Single Family Residence`, ...). Unknown codes are kept verbatim, matching the legacy API.
- **Code matching**: codes are trimmed and uppercased before lookup. Legacy matched the loan status exactly, so
  `clo` now shows as `Closed` (legacy: `clo`). Intentional; the seed data is all uppercase.
- **Blank loan status**: stored as `NULL`, rendered as `Unknown` by the API. Legacy also showed `Unknown` for
  `NULL`, but an empty or whitespace-only code was shown as-is; it now shows `Unknown` too.
- **Unmapped loan status codes** (e.g. `PIF`): stored as `UNKNOWN`, with the original code in the added
  `loan_accounts.legacy_status_code VARCHAR(5)` column. The API shows the original code, as the legacy API did,
  and the migration logs a warning (without the code).
- **Denormalized borrower name on `CDW_LN_ACCT`**: dropped. `borrowerName` is now built from the borrower FK;
  the migration logs a warning (account number and `BORR_ID` only) if the legacy copy disagreed with the borrower master.
- **`ssn_hash`**: copied as stored (`BORR_SSN_ENCR` → `ssn_hash`), **not re-encrypted**. `column_mappings.md`
  recommends re-encryption, but this service has no key management (no legacy decryption key, no target key or
  KMS), so it is a known follow-up (see below). The modern column therefore holds the legacy ciphertext and must
  be treated as legacy-encrypted data until that follow-up lands.
- **Payment IDs**: the modern DDL only had a surrogate `id`, but the API exposes `PMT_SEQ_NBR` as `paymentId`.
  Added `payments.external_id VARCHAR(20) UNIQUE NOT NULL` (in both `modern_tables.sql` and `schema-modern.sql`).

## Error handling

- Null/blank optional values → `NULL`; blank required values → row rejected.
- Malformed **optional** dates and integers (e.g. `BORR_DOB_DT`, `BORR_CRDT_SCR`, `PMT_RECV_DT`) → stored as `NULL`
  and reported as a warning, so one unused bad value does not reject a borrower and every loan and payment linked to it.
- Malformed required values, malformed amounts (strict because they are reconciled), unknown payment type/status
  codes, unresolved FKs → `LegacyDataException`, row rejected and reported with its business key. Other rows still
  migrate; with `migration.fail-on-error=true` (default) the run then rolls back and startup aborts.
- Error and warning messages name the column and business key but never echo the raw value, since the report is
  logged and the columns may hold PII.
- Values that do not fit the target column (`@Column` length / `DECIMAL` precision and scale, checked by
  `ModernEntityInspector`) → row rejected before flush, instead of a database error aborting the transaction.
- Duplicates: a business key already migrated in this run is skipped. A key that already exists in modern is not
  re-inserted; the stored row is compared field by field with the transformed legacy row (amounts by value, FKs by
  id, audit timestamps ignored) and any difference is a reconciliation error that names the differing fields but
  not their values. Re-running is therefore idempotent and
  still detects drift.
- `schema-modern.sql` uses `IF NOT EXISTS`, so it can run on every start against a persistent database;
  `ddl-auto=validate` is forwarded to the modern persistence unit so schema drift fails startup.

## Reconciliation

The `MigrationReport` checks, per run:

- Row counts: 5 borrowers, 5 products, 5 loan accounts, 10 payments.
- Sums: `borrowers.annual_income`; `loan_accounts.original_amount/current_balance/monthly_payment/escrow_balance`;
  `payments.total/principal/interest/escrow/late_fee` (compared with `BigDecimal.compareTo`).
- FK consistency: every loan's borrower/product and every payment's loan resolve to the same business keys as in legacy.

The same checks are available as standalone SQL in `data/validation/reconciliation_queries.sql`, for running
against both databases at any time (e.g. while dual-reading, or after the CDW feed loads new rows). Each check
is a `-- @check` pair of an `@legacy` and a `@modern` query that return the same columns: row counts per table,
monetary sums (legacy amounts `TRIM`med, commas stripped, `CAST` to `DECIMAL`), per-loan payment totals,
per-loan amounts/rate/origination date, the business keys (`BORR_ID`->`external_id`, `PROD_CD`->`code`,
`LN_ACCT_NBR`->`account_number`, `PMT_SEQ_NBR`->`external_id`), the FK pairs resolved by business key, and
dangling legacy references. `ReconciliationQueriesTest` runs every pair against `legacyDataSource` and
`modernDataSource` and reports the rows found on only one side per check (`ReconciliationQueries.run`).

## API compatibility

- Controllers and DTOs are unchanged. Dates are still rendered as `MM/DD/YYYY`; `propertyAddress` is rebuilt as
  `address, city, ST zip`; borrower `fullName` keeps the `First M. Last` format.
- Golden files in `src/test/resources/golden/` were captured from the legacy implementation before the switch and
  `ModernApiGoldenFileTest` (modern read source) and `LegacyApiGoldenFileTest` (`read-source=legacy`) check all
  18 endpoint responses against them.
- **Representational difference**: legacy whole-dollar amounts such as `"285,000"` used to serialize as
  `285000`; from `DECIMAL(12,2)` they serialize as `285000.00`. The values are numerically identical (and identical
  JSON numbers per RFC 8259), so the golden test compares numbers by value and everything else exactly. This only
  affects `originalAmount`; every other field is byte-identical for the seed data.
- **Intentional behaviour differences** (not visible with the seed data):
  - **Payment order**: legacy sorted `PMT_DT` as `MM/DD/YYYY` text, so `/payments` put `01/15/2026` after
    `11/15/2025`. The `DATE` column now sorts newest first chronologically.
  - **`borrowerName`** comes from `borrowers`, so it changes where the `CDW_LN_ACCT` copy disagreed with
    `CDW_BORR_MSTR` (e.g. "Jim Mitchel" vs "James Mitchell").

## Tests

- `ModernApiGoldenFileTest`, `LegacyApiGoldenFileTest` – all `LoanController`/`BorrowerController` endpoints vs
  pre-migration JSON, served by the modern and the legacy `LoanDataSource` (shared `ApiGoldenFileTestSupport`).
- `ReconciliationQueriesTest` – `reconciliation_queries.sql` passes after the migration and reports drifted
  amounts and orphan payments per check.
- `SchemaQueryBenchmarkTest` – the performance comparison below; skipped unless `-Dbenchmark=true`.
- `DataMigrationServiceTest` – counts, monetary totals (legacy side summed in SQL as an independent oracle),
  FK resolution, idempotent re-run, rejection of malformed/orphan/oversized rows, fail-on-error rollback,
  drift detection on existing rows, re-runnable schema script, lenient optional fields, unmapped loan status,
  redacted warnings, cross-year payment order.
- `LegacyValueParserTest`, `LegacyCodeMapperTest`, `ModernEntityInspectorTest` – transformation rules and redaction.
- `DataMigrationRunnerTest` – migration skipped while another instance holds the lock; lock released on success and failure.
- `ModernSchemaSyncTest` – `schema-modern.sql` matches `data/modern-schema/modern_tables.sql` (ignoring comments
  and `IF NOT EXISTS`).
- `LoanServiceApplicationTests` – context load; `LoanService` has no `parse*`/`expand*` methods or legacy dependencies
  (legacy reads live only in `LegacyLoanDataSource`);
  H2 console off by default; the startup migration releases its lock and the lock excludes a second holder.

## Performance comparison (legacy VARCHAR vs modern typed schema)

### Methodology

`SchemaQueryBenchmarkTest` (JUnit, no extra dependency) creates two fresh in-memory H2 databases from
`schema-legacy.sql` and `schema-modern.sql`, and `BenchmarkDataGenerator` loads the same seeded synthetic
portfolio into both: 10,000 borrowers, 5 products, 20,000 loans, 240,000 payments (12 per loan). Legacy rows
use the CDW formats (`"271,432.56"`, `"11/15/2025"`, `ACT`/`DFT`); modern rows use typed values. Both databases
are `ANALYZE`d. For each query the harness first asserts that legacy and modern return the same rows
(numbers by value, legacy dates as ISO), then runs 5 warm-up and 25 measured iterations through `JdbcTemplate`,
reading every column of every row, and reports the median and p95 wall time plus each query's H2 `EXPLAIN`
plan. The legacy queries are written the way they must be against `VARCHAR`s to be correct: amounts
`CAST(REPLACE(..., ',', '') AS DECIMAL)`, dates rearranged from `MM/DD/YYYY` to sortable `YYYYMMDD` text.

```bash
mvn -B test -Dtest=SchemaQueryBenchmarkTest -Dbenchmark=true
# optional: -Dbenchmark.loans=20000 -Dbenchmark.paymentsPerLoan=12 -Dbenchmark.warmup=5 -Dbenchmark.iterations=25
# report with query plans: target/benchmark/schema-query-benchmark.md
```

### Results

H2 2.2.224 in-memory, Java 17.0.19, single run on the Devin VM (absolute times vary by machine; the ratios
and plans are the meaningful part):

| Query | Rows | Legacy median (p95) ms | Modern median (p95) ms | Speedup | Modern plan |
|---|---:|---:|---:|---:|---|
| Filter loans by status | 733 | 1.70 (3.30) | 0.54 (0.89) | 3.1x | `idx_loan_accounts_status` |
| Join loans -> borrowers -> products (one state) | 2,051 | 26.89 (31.63) | 11.37 (14.66) | 2.4x | `BIGINT` PK joins |
| Aggregate payment totals per loan | 20,000 | 197.67 (231.77) | 128.71 (151.70) | 1.5x | `payments(loan_account_id)` index, no casts |
| Payments in a date range (one month) | 1 | 66.36 (83.32) | 7.20 (7.37) | 9.2x | `idx_payments_date` range |
| Payment history of one loan (`/payments`) | 12 | 11.10 (11.22) | 0.15 (0.22) | 72.4x | unique `account_number` + `payments(loan_account_id)` |
| Numeric range filter (balance > 500,000) | 3,445 | 5.21 (5.31) | 1.63 (1.67) | 3.2x | scan of a `DECIMAL` column |

### Findings

- **Indexes** (`schema-modern.sql`): `idx_loan_accounts_status` and `idx_payments_date` are used directly.
  `payments` lookups by loan use the index H2 creates for the `loan_account_id` FK (`idx_payments_loan` covers
  the same column), and `account_number`/`external_id`/`code` lookups use their `UNIQUE` indexes. The legacy
  tables only have their `VARCHAR` primary keys; `CDW_PMT_HIST` has none on `LN_ACCT_NBR` or `PMT_DT`, so every
  legacy payment query is a full table scan - the 72x on the endpoint behind `/api/loans/{id}/payments`.
- **Typed columns**: a date or amount predicate on legacy wraps the column in `SUBSTRING`/`REPLACE`/`CAST`,
  which no index can serve and which is evaluated on every row; `DATE` and `DECIMAL` columns compare natively
  (and `DATE` sorts correctly, see "Payment order" above). Sums avoid a per-row string-to-decimal parse (1.5x
  even when both sides scan all 240,000 payments).
- **Joins**: `BIGINT` surrogate keys join faster than `VARCHAR` business keys (2.4x). The state filter itself
  is not indexed on either side (`borrowers.state` has no index); add one if state filters become common.
- **Status filter**: legacy is only 3x slower here because `CDW_LN_ACCT` is small and the code comparison is
  a plain string compare; the gap grows with table size since legacy stays a scan.

## Production follow-ups (out of scope for the workshop)

- The migration runs on every startup, loads all four legacy tables into memory and runs as one transaction.
  For real volumes, run it as a one-off job (start once with the defaults, then run the service with
  `migration.run-on-startup=false`) or batch it.
- **Re-encrypt `ssn_hash`** instead of copying it: decrypt `BORR_SSN_ENCR` with the legacy key and encrypt with a
  managed key (KMS/Vault) in `DataMigrationService.toBorrower`. The drift check compares `ssnHash` on re-runs, so
  the re-encryption must be deterministic, or that field must be compared after decryption.

Done in this PR: multi-instance runs are serialised with ShedLock (see "Migration flow"), and the H2 console,
which exposes both databases, is off by default and only enabled by the `dev` profile
(`mvn spring-boot:run -Dspring-boot.run.profiles=dev`, see `application-dev.properties`).
- Adopt a versioned schema migration tool (Flyway or Liquibase). `schema-modern.sql` only creates missing tables,
  so a column added later (like `legacy_status_code`) would not reach an existing persistent database and
  `ddl-auto=validate` would fail startup. Not an issue today: the modern database is in-memory.

## Decommissioning legacy

Legacy entities and repositories are `@Deprecated` and used only by `DataMigrationService` and
`LegacyLoanDataSource`. Once the CDW feed is retired: delete `entity.legacy`, `repository.legacy`,
`LegacyDataSourceConfig`, `LegacyLoanDataSource`, `loan-service.read-source`, the `legacy.*` properties,
`schema-legacy.sql`/`data-legacy.sql`, `DataMigrationRunner`/`DataMigrationService`, and point
`spring.datasource.url` at the persistent modern database.

## Other fixes

- `pom.xml`: `<relativeTo/>` → `<relativePath/>` (Maven refused to parse the POM).
