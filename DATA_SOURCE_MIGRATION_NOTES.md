# Data Source Migration Notes

The loan service now reads from the normalized modern schema (`data/modern-schema/modern_tables.sql`).
The legacy CDW tables remain as the migration source and as a deprecated read path for rollback.

## Architecture

| Piece | Location |
|-------|----------|
| Modern data source (primary), JPA unit `modern` | `config/ModernDataSourceConfig` → `jdbc:h2:mem:loandb` |
| Legacy data source, JPA unit `legacy` | `config/LegacyDataSourceConfig` → `jdbc:h2:mem:legacydw` |
| Modern entities / repositories | `modern/entity`, `modern/repository` |
| Migration (JDBC) | `migration/LegacyToModernMigrator`, run by `migration/MigrationRunner` |
| API implementations | `service/ModernLoanService` (default), `service/LegacyLoanService` (deprecated) |

Each data source has its own `DataSource`, schema initializer, `EntityManagerFactory`,
transaction manager and `JdbcTemplate`. Modern beans are `@Primary`.

## Decisions

- **Typed entities.** `LocalDate`, `LocalDateTime`, `BigDecimal`, `Integer`, `Boolean`; status and type
  columns are enums (`LoanStatus`, `PaymentType`, `PaymentStatus`, `BorrowerStatus`) stored as strings.
  Enums carry the display label the API has always returned (e.g. `NSF` → `Non-Sufficient Funds`).
- **Relationships.** `LoanAccount` → `Borrower` / `LoanProduct` and `Payment` → `LoanAccount` are
  `@ManyToOne(LAZY)` with `@OneToMany` back-references. Repository queries use entity graphs so each
  endpoint is one SQL query (no N+1). `open-in-view` is off; the service is `@Transactional(readOnly = true)`.
- **Migration over JDBC.** The migrator reads raw legacy rows with a `JdbcTemplate`, so it doesn't depend
  on the legacy JPA entities. It parses values (`LegacyValueParser`), expands codes (`LegacyCodeMappings`)
  and resolves string foreign keys to modern surrogate keys.
- **Idempotent.** Rows whose natural key (`code`, `external_id`, `account_number`, payment `external_id`)
  already exists are skipped. Re-running inserts nothing.
- **Bad data doesn't stop the run.** Malformed values (e.g. `02/30/1990`, `12x,000`), unknown or missing
  status/type codes, and unresolved foreign keys reject only that record. Rejections are logged and
  returned in `MigrationReport`. Blank values become `NULL`. Columns with a schema default
  (`late_fee`, `escrow_balance`, `delinquency_days`, `created_at`, `updated_at`) get that default.
- **Reconciliation.** After migrating, `reconcile()` checks row counts, the totals of 13 amount columns,
  and the borrower/product/loan-account FK mappings, comparing legacy and modern. Results are logged
  at startup (`MISMATCH` at WARN).
- **Insertion order.** Legacy rows are read in table order so modern ids, and therefore list ordering,
  match what the legacy API returned.
- **Feature flag.** `loan.datasource.mode=modern|legacy` selects the `LoanService` implementation at startup.

## Schema changes vs. the original target DDL

- `payments.external_id VARCHAR(20) UNIQUE NOT NULL` was added. It keeps the legacy `PMT_SEQ_NBR`, which
  the API exposes as `paymentId`. It is also the natural key for idempotent migration (same pattern as
  `borrowers.external_id`).
- `property_type` stores the full description the API already returned (`Single Family Residence`,
  `Condominium`, `Multi-Family Residence`, `Townhouse`). `column_mappings.md` was updated to match.

## API compatibility

Golden files in `src/test/resources/golden` were captured from the unmodified legacy application.
`ModernGoldenApiTest` and `LegacyGoldenApiTest` replay every endpoint and compare.

Intentional differences:

| Difference | Legacy | Modern | Why |
|------------|--------|--------|-----|
| Decimal scale | Copied from the source string: `285000`, `0.00`, `4.750` | Column scale: `285000.00`, `0.00`, `4.750` | Values are numerically identical; the golden comparison compares numbers by value |
| Null amounts | Shown as `0` | Shown as `null` | No null amounts in the data. Modern no longer turns missing into zero (except columns with schema defaults) |

The date format (`MM/dd/yyyy`) is unchanged. DTO dates are now `LocalDate` with `@JsonFormat`.

## Cleanup

- Legacy entities, repositories and `LegacyLoanService` are marked `@Deprecated`.
- Once the legacy path is no longer needed for rollback, remove `LegacyDataSourceConfig`, the `entity`
  and `repository` packages, `LegacyLoanService`, and the legacy SQL scripts. Then replace the startup
  migration with seeding of the modern schema.

## Build fixes included

- `pom.xml`: `<relativeTo/>` → `<relativePath/>` (the POM did not parse).
- Added the missing `mvnw` / `mvnw.cmd` wrapper scripts.
- README: payments endpoint is `GET /api/loans/{loanId}/payments`.
