# Data Source Migration Notes

How loan-service moved from the legacy CDW tables to the normalized schema, the decisions made
along the way, and the differences that remain. The task list is in
[MIGRATION_TASKS.md](MIGRATION_TASKS.md).

## Result

| | Before | After |
|---|---|---|
| Tables | `CDW_BORR_MSTR`, `CDW_LN_PROD`, `CDW_LN_ACCT`, `CDW_PMT_HIST` | `borrowers`, `loan_products`, `loan_accounts`, `payments` |
| Column types | Everything `VARCHAR`; dates as `MM/DD/YYYY`, amounts like `"285,000"` | `DATE`, `TIMESTAMP`, `DECIMAL`, `INTEGER`, `BOOLEAN` |
| Relationships | Borrower name copied into each loan row, no foreign keys | Foreign keys from loans to borrowers/products and from payments to loans |
| Service layer | Parsed strings and expanded codes on every request | Maps typed entities to DTOs |
| Data sources | Legacy, then legacy + modern during the migration | One, configured by Spring Boot |
| API | 5 endpoints | Same endpoints and response shapes |

## How it was done

Each step was one PR into the `migration-main` integration branch, reviewed and verified
before the next one started.

| PR | Step |
|----|------|
| [#137](https://github.com/codev-workshops/uc-data-source-migration-jdbc-normalization/pull/137) | Fixed the build (`<relativePath/>` in `pom.xml`, Maven wrapper) and the README's payments endpoint |
| [#138](https://github.com/codev-workshops/uc-data-source-migration-jdbc-normalization/pull/138) | Saved the legacy API responses as 17 golden files, plus `ApiGoldenTest` |
| [#139](https://github.com/codev-workshops/uc-data-source-migration-jdbc-normalization/pull/139) | Modern schema, typed entities and repositories on a second data source |
| [#140](https://github.com/codev-workshops/uc-data-source-migration-jdbc-normalization/pull/140) | Re-runnable legacy → modern data copy with validation |
| [#141](https://github.com/codev-workshops/uc-data-source-migration-jdbc-normalization/pull/141) | `ModernLoanService`; controllers switched to the modern schema |
| [#142](https://github.com/codev-workshops/uc-data-source-migration-jdbc-normalization/pull/142) | Legacy code, data source and data copy removed; schema seeded from `data-modern.sql` |

## Decisions

### Snapshot the API before changing anything
Before any schema work, every endpoint response was saved under `src/test/resources/golden/`.
`ApiGoldenTest` compares the live API with those files. It passed unchanged at every later step,
which is the evidence that the contract held. Numbers are compared by value (see
[Differences](#differences)); everything else, including array order, must match exactly.

### Keep the legacy payment id
The API returns the legacy `PMT_SEQ_NBR` (e.g. `PMT-2025120001`) as `paymentId`, but the target
`payments` table only had a generated numeric `id`. Without the legacy id, `paymentId` would have
changed to `"1"`, `"2"` and so on. So `schema-modern.sql` adds one column to the target design:

```sql
legacy_payment_id VARCHAR(20) UNIQUE
```

During the migration it also made re-runs safe: two payments on the same loan, date and amount
can't be told apart without it. Borrowers, products and loans didn't need anything extra; their
legacy ids live on as `external_id`, `code` and `account_number`.

### One mapping from codes to labels
The database stores enum names (`ACTIVE`, `NSF`, `REGULAR`), and the API shows labels
(`Active`, `Non-Sufficient Funds`, `Regular`). Each enum (`LoanStatus`, `PaymentStatus`,
`PaymentType`) holds its own label, so there is one place to change it. While the data copy
existed, the enums also held the legacy codes (`ACT`, `NSF`, `REG`). Those were removed with
the copy.

### Two data sources during the migration, one after
While both schemas existed, a second data source (`DataSourceConfig`) kept the modern entities
and repositories separate from the legacy ones. This allowed migrating and comparing without
touching the running API. Once nothing read the legacy tables, the configuration was deleted.
Spring Boot now creates the single data source, JPA setup and repositories from `application.yml`.

### A re-runnable data copy, then a seed file
`LegacyDataMigrator` (#140) parsed and copied the legacy rows. It looked up existing rows by
their natural keys, so a second run updated rows instead of duplicating them. Bad rows were
skipped and reported instead of failing the whole run. It then checked row counts and money
totals between the two schemas. On the sample data it copied 5 products, 5 borrowers, 5 loans
and 10 payments with no problems.

Removing the legacy tables would also remove the copy's source. So the copied rows were exported
to `src/main/resources/data-modern.sql`, and the copy was deleted. The seed file refers to
related rows through natural keys (`(SELECT id FROM borrowers WHERE external_id = 'B-10001')`),
so it doesn't depend on generated ids. The migrator and its tests are in the `migration-main`
history (#140) if a real legacy extract ever has to be loaded.

### Tests reset their own data
Repository tests empty the tables in `@BeforeEach` and check they are empty, rather than relying
on transaction rollback. `ApiGoldenTest` and `ModernRepositoryTest` use their own named in-memory
databases, so Spring test contexts don't run the schema script against one shared database.

## Differences

| Area | Legacy | Now | Why |
|------|--------|-----|-----|
| Number scale | `285000`, `0` | `285000.00`, `0.00` | `DECIMAL(12,2)` columns keep two decimals. Same value; `ApiGoldenTest` compares numbers by value. |
| Unknown ids | HTTP 500 | HTTP 500 (unchanged) | Kept as-is so the migration changes nothing in the API. Returning 404 is a separate change. |

Dates (`MM/dd/yyyy`), labels, field names, ordering and `paymentId` values are unchanged.

## What was removed

- Legacy entities `Legacy*`, their repositories and the old `LoanService`
- `DataSourceConfig`, `schema-legacy.sql`, `data-legacy.sql`
- The data copy (`LegacyDataMigrator`, `LegacyValueParser`, `MigrationReport`,
  `MigrationRunner`) and its tests

Kept for reference: `data/legacy-schema/`, `data/modern-schema/` and `data/mappings/`.
`src/main/resources/schema-modern.sql` is the schema the app runs.
