# Data Source Migration Notes — Legacy CDW → Modern Schema

Status: **complete**. The application serves from the modern normalized schema by
default (`datasource.mode=modern`), with the legacy CDW read path retained as a
dual-read fallback (`datasource.mode=legacy`). All 5 API endpoints are
byte-identical to the golden snapshots in `src/test/resources/golden/` in both
modes.

## Architecture & Patterns

### Sibling-package persistence units (dual datasources)
Two independent JPA persistence units live side by side:

- **Legacy** (`@Primary`): `com.workshop.loanservice.entity` /
  `...loanservice.repository`, backed by `spring.datasource.*`
  (H2 `legacydw`), configured in `LegacyDataSourceConfig`.
- **Modern**: `com.workshop.loanservice.modern.entity` /
  `...modern.repository`, backed by `modern.datasource.*` (H2 `moderndb`),
  configured in `ModernDataSourceConfig` with qualifier-wired
  `modernDataSource`, `modernEntityManagerFactory`, and
  `modernTransactionManager` beans. The modern schema
  (`schema-modern.sql`) is applied via an explicit `DataSourceInitializer`
  because Spring Boot's SQL init only covers the primary datasource.

### Golden-guarded cutover
Before any rewiring, the exact HTTP response bytes of every endpoint were
captured from the legacy-backed API into `src/test/resources/golden/`
(17 files). The cutover to the modern schema is only considered correct if
every endpoint remains **byte-identical** — enforced continuously by
`GoldenFileComparisonTest` (modern mode) and
`GoldenFileLegacyModeComparisonTest` (legacy mode).

### Dual-read flag (`datasource.mode`)
`LoanService` depends on a `LoanDataProvider` interface with two
implementations selected by plain Spring conditional wiring (no reflection):

- `ModernLoanDataProvider` — `@ConditionalOnProperty(name = "datasource.mode",
  havingValue = "modern", matchIfMissing = true)`; reads the modern
  repositories inside `modernTransactionManager` read-only transactions.
- `LegacyLoanDataProvider` — `havingValue = "legacy"`; the original
  string-parsing translation logic, moved verbatim out of `LoanService`.

Controllers and DTOs are unchanged; `LoanService` is now source-agnostic and
contains no string-to-type parsing.

### Startup migration (idempotent)
`MigrationStartupRunner` (an `ApplicationRunner`) runs
`LegacyToModernMigrationService.migrateAll()` at boot when the modern tables
are empty, so the app serves migrated data from the first request. The
migration itself is idempotent (natural-key duplicate checks), so re-running
is safe; the emptiness check just avoids redundant work. Tests that need
empty modern tables disable it with `migration.run-on-startup=false`.

### Migration service (Task 2 recap)
Order respects FKs: borrowers → products → accounts → payments. Malformed
records are validated **before** any duplicate check or save
(validate-then-skip), logged with the legacy record identifier, and counted in
per-entity `skippedRecords` counters; runs never abort. Reconciliation
expectations: 5 borrowers / 5 products / 5 accounts / 10 payments.

## Presentation formatting in the modern read path
The modern tables store proper types (DATE, DECIMAL, INTEGER), so no parsing
happens on read — only formatting so responses match the golden bytes:

- Dates rendered as `MM/dd/yyyy` (the legacy string format).
- Canonical stored codes expanded to display labels
  (`ACTIVE` → `Active`, `NSF` → `Non-Sufficient Funds`,
  `Single Family` → `Single Family Residence`, etc.).
- Original loan amounts: the legacy source records them in whole dollars
  (e.g. `"285,000"`), so integral values are rendered without a fractional
  part even though the modern column is `DECIMAL(12,2)`
  (`ModernLoanDataProvider#wholeDollarsWithoutCents`).

## Schema addition: `payments.payment_number`
The API exposes the legacy payment sequence number (`PMT_SEQ_NBR`, e.g.
`PMT-2025120001`) as `paymentId`. The modern payments table originally had no
such column, which would have made byte-identical responses impossible. A
nullable unique `payment_number VARCHAR(20)` column was added and is populated
from `PMT_SEQ_NBR` during migration. This is a deliberate, documented schema
extension — not an API difference (the API output is unchanged).

## Seeded defects found and fixed
1. **README vs code: payments endpoint path.** The README documents
   `GET /api/payments/loan/{loanId}`, but the actual controller mapping is
   `GET /api/loans/{id}/payments` (`LoanController#getPayments`). Golden files
   were captured against the real path.
2. **`pom.xml` repo-health issues** fixed on `priyal/fix-repo-health`
   (prerequisite branch) so the project builds cleanly with `./mvnw`.
3. **Legacy type-punning defects** absorbed by the migration parsers:
   comma-grouped currency strings (`"1,487.02"`), `MM/DD/YYYY` string dates,
   string integers, and cryptic status codes — all converted to native
   DECIMAL/DATE/INTEGER/expanded values with warn-and-null (or
   validate-then-skip for mandatory fields) handling for malformed input.

## Edge cases handled
- **Malformed records**: mandatory-field parse failures (e.g. a payment with
  `PMT_DT='NOT-A-DATE'`) are skipped with a warning and counted; the run
  continues (covered by `skipsMalformedPaymentWithWarningWithoutAbortingRun`).
- **Unresolvable FKs**: accounts/payments whose borrower/product/account
  cannot be resolved are skipped and counted, never saved partially.
- **Duplicate/idempotent runs**: natural-key matching (borrower external id,
  product code, account number; payments by loan+date+amount+type) means
  re-running migrates nothing twice.
- **Unknown codes**: kept as raw values with a warning rather than dropped.
- **Null optional values**: mapped to null with a warning, never silently.
- **Shared in-memory H2 across Spring test contexts**: `schema-legacy.sql`
  and `schema-modern.sql` now `DROP TABLE IF EXISTS` first so each context
  initializes from a clean, deterministic state.

## Legacy cleanup
Legacy entities and repositories are marked `@Deprecated` with Javadoc
pointing to their modern equivalents. They are **not deleted** because the
dual-read fallback (`datasource.mode=legacy`) and the migration service still
require them.

## Validation
- `./mvnw clean test` — 18/18 green, including both golden suites.
- Manual smoke: booted in each mode, hit all 5 endpoints, `cmp` against all
  17 golden files → zero byte differences in both modes.
- Bonus reconciliation SQL: `data/validation/reconciliation_queries.sql`
  (row counts, per-loan amount sums, orphaned-FK checks).

## Intentional differences
None at the API level. The only intentional divergence from the pre-existing
modern DDL is the additive `payments.payment_number` column described above.
