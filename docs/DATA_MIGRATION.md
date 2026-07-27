# Data Migration: legacy CDW tables to the modern schema

`com.workshop.loanservice.migration.DataMigrationRunner` copies the four legacy tables into the
modern normalized tables. It is a standalone utility: it boots its own non-web Spring context and is
never triggered by normal web startup.

## Running it

```bash
./mvnw compile exec:java -Dexec.mainClass=com.workshop.loanservice.migration.DataMigrationRunner
```

`exec-maven-plugin` already defaults to that main class, so `./mvnw compile exec:java` is enough.
From a packaged jar:

```bash
./mvnw package
java -cp target/loan-service-1.0.0.jar -Dloader.main=com.workshop.loanservice.migration.DataMigrationRunner \
    org.springframework.boot.loader.launch.PropertiesLauncher
```

Any Spring property can be overridden on the command line, e.g. to point the migration at a
persistent modern database instead of the default in-memory one:

```bash
./mvnw compile exec:java -Dexec.args="--modern.datasource.url=jdbc:h2:file:./target/moderndb"
```

The runner exits `0` when every validation criterion passes and `1` otherwise (including when the
migration transaction rolls back), so it can gate a pipeline.

## Migration order

Foreign keys dictate the order; each step only runs after its parents exist:

1. `CDW_BORR_MSTR` -> `borrowers`
2. `CDW_LN_PROD` -> `loan_products`
3. `CDW_LN_ACCT` -> `loan_accounts` (needs borrowers + products)
4. `CDW_PMT_HIST` -> `payments` (needs loan accounts)

Transformations follow `data/mappings/column_mappings.md` exactly: `MM/DD/YYYY` strings become
`LocalDate`/`LocalDateTime`, amounts are parsed with `new BigDecimal(str.replace(",", "").trim())`
without any rounding or rescaling, and status/type codes are expanded with the expansions listed in
that document. Denormalized borrower columns on `CDW_LN_ACCT` (`BORR_FST_NM`, `BORR_LST_NM`,
`BORR_SSN_LST4`) are dropped; the borrower is resolved through the preserved legacy id only.

## The `migration_id_map` table

```sql
CREATE TABLE IF NOT EXISTS migration_id_map (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    entity_type   VARCHAR(50) NOT NULL,
    legacy_id     VARCHAR(100) NOT NULL,
    modern_id     BIGINT NOT NULL,
    migrated_at   TIMESTAMP NOT NULL,
    UNIQUE(entity_type, legacy_id)
)
```

It records which legacy record became which modern row. It is deliberately **not** part of
`data/modern-schema/modern_tables.sql` — the approved modern schema stays untouched, no table gains
a `legacy_id` column, and `payments.id` remains a plain auto-increment that never reuses legacy
values. The table lives in the **modern** datasource and is created by the runner at startup with
`CREATE TABLE IF NOT EXISTS`, on its own connection, before the migration transaction opens.

`MigrationIdMap` is the only access point:

| Method | Purpose |
|---|---|
| `findModernId(entityType, legacyId)` | modern primary key for a legacy id, if migrated |
| `exists(entityType, legacyId)` | has this legacy record already been migrated? |
| `record(entityType, legacyId, modernId, migratedAt)` | remember a freshly migrated record |

Entity types: `borrower` (`BORR_ID`), `loan_product` (`PROD_CD`), `loan_account` (`LN_ACCT_NBR`),
`payment` (`PMT_SEQ_NBR`, the primary key of `CDW_PMT_HIST`).

Reads and writes go through the modern JPA `EntityManager`, so they run on the same connection and
in the same transaction as the migrated entities.

## Transactions and rollback

`MigrationService.migrate()` is annotated `@Transactional("modernTransactionManager")`, so all four
tables migrate in a single modern-datasource transaction. If anything throws, the modern rows *and*
their `migration_id_map` entries roll back together — the two can never disagree, and a partially
migrated database is impossible. Per-record problems never throw: they are logged as warnings and
the record is skipped.

## Idempotency

Before inserting, every record of every one of the four entity types is looked up in
`migration_id_map`; if it is already there the record is skipped, otherwise it is inserted and the
mapping recorded. Re-running the migration against an already migrated database therefore inserts
nothing, errors on nothing, and leaves the counts unchanged. Foreign keys are resolved through
`migration_id_map.findModernId(...)` first, cross-checked against the natural-key repository lookups
(`findByExternalId` / `findByCode` / `findByAccountNumber`); records are never matched by name,
address or any other non-id field.

Note that with the default in-memory H2 URLs both databases are recreated on every JVM start, so a
re-run only demonstrates idempotency against a persistent `modern.datasource.url`.

## Malformed records vs. mapping gaps

These are two different things and are handled differently.

**Malformed data is skipped.** Every record is transformed inside a try/catch; null required fields,
unparseable dates, amounts or integers, and unresolvable foreign keys log a WARN naming the legacy
primary key and the offending field, and skip that one record. Everything else keeps migrating.

**A code with no expansion is a mapping gap, not malformed data, and is never a reason to drop a
real record.** When `column_mappings.md` defines no expansion for a status/type code, the record
migrates with the raw legacy code stored as-is (e.g. `property_type = 'TWN'`), a WARN is logged and
the gap is listed in the report's *Codes migrated unexpanded* section so the mapping document can be
extended later. The one exception is `PROD_STAT_CD`, which maps to the boolean `is_active`: an
unexpanded code cannot be represented as a boolean, so the column is left null rather than guessed
— the record itself still migrates.

The report scans the whole legacy dataset for gaps, not just the rows inserted by the current run,
so the list stays complete on re-runs.

## Reading the validation report

The report is printed at the end of the run and has four sections:

- **Row counts** — per table: legacy source rows, rows migrated in this run, rows skipped because
  they were already migrated, rows skipped as malformed, and the total mapped in
  `migration_id_map`.
- **Skipped records** — one line per malformed record with the legacy primary key and reason.
- **Codes migrated unexpanded** — every legacy code in the dataset that `column_mappings.md` does
  not expand, with the record it appears on; these rows migrated with the raw code.
- **Aggregate amount checks** — for each DECIMAL-mapped column, the sum parsed from the legacy
  strings versus the sum of the migrated modern rows, compared with `BigDecimal.compareTo == 0` so
  scale differences do not matter. Only successfully migrated rows are included on both sides.
- **Validation criteria** — the explicit PASS/FAIL expectations: 5 borrowers, 5 loan products,
  5 loan accounts with correct `borrower_id`/`product_id`, 10 payments with correct
  `loan_account_id`.

`OVERALL: PASS` requires every criterion and every amount check to pass; otherwise the runner exits
non-zero.

### Known mapping gap in the shipped seed data

`data/mappings/column_mappings.md` line 70 expands only `SFR` and `CND` for `PROP_TYP_CD`, while
loan account `LN-2021-00567` carries `TWN`. That row migrates with `property_type = 'TWN'` and the
gap is reported; deciding what `TWN` should expand to is a mapping-document change, not a migration
one. It is the only gap in the current seed data.
