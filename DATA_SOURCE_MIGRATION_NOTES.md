# Data Source Migration Notes

How the loan service moved off the legacy CDW warehouse onto the normalized schema,
and which decisions the code encodes.

## 1. Two data sources

The application runs two independent H2 databases, wired explicitly because Spring
Boot's single-data-source auto-configuration cannot describe two:

| | Legacy | Modern |
|---|---|---|
| JDBC URL | `jdbc:h2:mem:legacydw` | `jdbc:h2:mem:moderndw` |
| Config class | `config/LegacyDataSourceConfig` | `config/ModernDataSourceConfig` (`@Primary`) |
| Entities | `entity.legacy` (`CDW_*`, all `VARCHAR`) | `entity.modern` (typed, normalized) |
| Repositories | `repository.legacy` | `repository.modern` |
| Transaction manager | `legacyTransactionManager` | `modernTransactionManager` (`@Primary`) |
| Initialised with | `schema-legacy.sql` + `data-legacy.sql` | `schema-modern.sql` |

Each config class owns its `DataSource`, `EntityManagerFactory`, transaction manager,
and `DataSourceInitializer`, and binds `@EnableJpaRepositories` to its own repository
package, so a repository can only ever reach its own database.
`spring.sql.init.mode=never` keeps Boot's shared initializer out of the way.

Consequences worth stating explicitly, both of which shaped the design below:

- There is **no cross-database `JOIN`**, so reconciliation happens in the application.
- There is **no shared transaction**, so the migration cannot be atomic across the two
  databases; it is idempotent and restartable instead.

## 2. Entity and column mapping

| Legacy | Modern | Notes |
|---|---|---|
| `CDW_BORR_MSTR.BORR_ID` | `borrowers.external_id` | legacy key kept; API still returns it as `id` |
| `CDW_LN_PROD.PROD_CD` | `loan_products.code` | resolved to `loan_accounts.product_id` |
| `CDW_LN_ACCT.LN_ACCT_NBR` | `loan_accounts.account_number` | API path variable |
| `CDW_LN_ACCT.BORR_ID` | `loan_accounts.borrower_id` | surrogate FK |
| `CDW_LN_ACCT.BORR_FST_NM/BORR_LST_NM/BORR_SSN_LST4` | — | dropped; read through `borrowers` |
| `CDW_PMT_HIST.PMT_SEQ_NBR` | `payments.external_id` | see §5 |
| `CDW_PMT_HIST.LN_ACCT_NBR` | `payments.loan_account_id` | surrogate FK |

Relationships: `LoanAccount → Borrower` and `LoanAccount → LoanProduct`
(`@ManyToOne`), `Borrower → LoanAccount` and `LoanAccount → Payment`
(`@OneToMany`), `Payment → LoanAccount` (`@ManyToOne`).

## 3. Type conversions

All parsing lives in `migration/LegacyTypeConverter`, so the migration and the legacy
read path interpret CDW values identically:

- `MM/DD/YYYY` strings → `LocalDate` (`*_CRET_DT`/`*_UPDT_DT` → `LocalDateTime` at start of day)
- `"285,000"` / `"1,487.02"` → `BigDecimal`
- credit score, term, delinquency days → `Integer`
- `PROD_STAT_CD` `ACT`/`INA` → `loan_products.is_active` `true`/`false`
- status codes → canonical values: `ACT`→`ACTIVE`, `CLO`→`CLOSED`, `DFT`→`DEFAULT`, `FRB`→`FORBEARANCE`
- payment types → `REG`→`REGULAR`, `EXT`→`EXTRA`, `PRT`→`PARTIAL`, `PRE`→`PREPAYMENT`
- payment statuses → `PST`→`POSTED`, `REV`→`REVERSED`, `NSF`→`NSF`, `PND`→`PENDING`
- property types → `SFR`→`Single Family Residence`, `CND`→`Condominium`, `MFR`→`Multi-Family Residence`, `TWN`→`Townhouse`

Unrecognised codes pass through unchanged rather than being silently dropped, so bad
data shows up in the reconciliation report instead of disappearing.

## 4. Migration

`migration/MigrationService` runs in dependency order — borrowers, products, loan
accounts, payments — resolving `BORR_ID`, `PROD_CD`, and `LN_ACCT_NBR` to surrogate
keys as it goes. `MigrationRunner` invokes it on startup (the databases are in-memory,
so every boot re-migrates); switch off with
`loanservice.migration.run-on-startup=false`.

Robustness properties:

- **Idempotent / restartable.** Every row is guarded by an existence check on its
  business key, so a rerun after a partial failure inserts only what is missing. Each
  table is migrated in its own modern transaction (`TransactionTemplate` over
  `modernTransactionManager`) — deliberately *not* one transaction spanning both
  databases, which two independent data sources cannot provide.
- **Row-level rejection.** Unparseable values or unresolvable foreign keys reject that
  one row with a reason; the run continues.
- **Reported.** `MigrationReport` carries migrated / skipped-existing / rejected counts
  per table and is logged at the end of a run. Against the seed data:
  5 borrowers, 5 products, 5 loan accounts, 10 payments, 0 rejected.

## 5. `payments.external_id`

The API returns `paymentId` = `PMT-2025120001`, i.e. the legacy `PMT_SEQ_NBR`, and the
modern schema as originally specified had nowhere to put it — exposing the new
`BIGINT` surrogate key instead would have broken every caller holding a payment id. So
`payments` gained `external_id VARCHAR(20) UNIQUE NOT NULL`, the surrogate key stays
internal, and the same reasoning is why `borrowers.external_id`,
`loan_products.code`, and `loan_accounts.account_number` are preserved.

## 6. API compatibility

Reads go through `provider/LoanDataProvider`, selected by
`DataSourceModeSelector` — `loanservice.datasource.mode` (`modern` by default,
`legacy` to fall back) supplies only the initial value, and the mode can be
switched at runtime:

```
GET  /api/admin/datasource-mode      -> {"mode":"modern","availableModes":["legacy","modern"]}
PUT  /api/admin/datasource-mode      {"mode":"legacy"}
```

`LoanService` resolves the provider per call from an `AtomicReference`, so a
switch takes effect on the next read and no single request is ever half-served
from two databases. An unknown mode is a 400 and leaves the active one
untouched. The controllers and the DTOs are unchanged.

The modern schema stores canonical values (`ACTIVE`, `REGULAR`, real dates), while the
API keeps emitting exactly what it always did; `provider/PresentationFormat` reapplies
that presentation and **both** providers format through it, so the two paths cannot
drift:

- dates rendered `MM/DD/YYYY`
- statuses title-cased (`ACTIVE` → `Active`); `NSF` → `Non-Sufficient Funds`
- `originalAmount` rendered without cents (`285000`, not `285000.00`) because CDW held
  whole dollars; balances, payments, and fees keep two decimals; rates keep three
- `paymentId`, borrower `id`, and `loanAccountNumber` remain the legacy business keys

`ApiGoldenFileTests` asserts byte equality against
`src/test/resources/golden/*.json`, captured from the running application *before* the
read path was switched, for all five endpoints.

## 7. Reconciliation

`reconciliation/ReconciliationService` (exposed at `GET /api/admin/reconciliation`)
compares the two data sources in the application, since they cannot be joined in SQL:

- row counts per table, legacy vs modern
- for every endpoint, and for every loan, borrower, and payment set, the serialised
  DTOs from both providers compared as JSON

Any difference is reported as a mismatch with both values. This is stronger than
comparing aggregates: it is the API-compatibility guarantee itself.

## 8. Intentional differences

- The modern `payments` read is ordered by `payment_date DESC, external_id DESC`. The
  legacy query ordered by a `MM/DD/YYYY` *string*, which sorts by month rather than
  chronologically; the seed data has one payment per loan per month, so the two orders
  agree today, but the modern ordering is the correct one and will diverge on data
  where the legacy sort was simply wrong.
- Property types are stored in the descriptive form the API returns
  (`Single Family Residence`), not the abbreviated form in
  `data/mappings/column_mappings.md` (`Single Family`).
- Legacy timestamps are date-only, so `created_at`/`updated_at` land at midnight;
  rows with no legacy timestamp get the migration time.
- Missing rows still produce a `RuntimeException` (HTTP 500), matching the previous
  behaviour rather than quietly improving it to a 404.
- `schema-legacy.sql` now begins with `DROP TABLE IF EXISTS` so the in-memory legacy
  database can be initialised more than once per JVM (several Spring contexts across
  the test suite).
