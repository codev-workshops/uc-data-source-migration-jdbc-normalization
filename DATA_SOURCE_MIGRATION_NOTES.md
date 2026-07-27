# Data Source Migration Notes — Legacy CDW to Modern Normalized Schema

Single reference for the legacy→modern data source migration of `loan-service`. Every rationale
below comes from the work delivered in PR #49 (modern entities + second datasource), PR #51
(standalone migration + `migration_id_map`), PR #52 (modern dual-read mode) and PR #53 (structural
JSON parity verification), and from the source files on this branch.

---

## 1. Overview

**Legacy schema.** The application originally read four CDW (Corporate Data Warehouse) tables —
`CDW_BORR_MSTR`, `CDW_LN_PROD`, `CDW_LN_ACCT`, `CDW_PMT_HIST` — documented in
`data/legacy-schema/cdw_tables.sql` and mapped column-by-column in
`data/mappings/column_mappings.md`. Every column is `VARCHAR`, so nothing is typed by the database:
dates are `MM/DD/YYYY` strings (`03/15/1978`), amounts are comma-formatted strings (`"285,000"`,
`"271,432.56"`), and integers such as credit scores and term months are strings too. Column names
are cryptic abbreviations (`BORR_FST_NM`, `LN_ORIG_AMT`, `PROP_APRS_VAL`). The model is
denormalized: `CDW_LN_ACCT` duplicates borrower data onto every loan account
(`BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4`) instead of referencing the borrower master, and
there are no foreign keys. Status and type values are short codes (`ACT`, `CLO`, `DFT`, `FRB`,
`SFR`, `CND`, `REG`, `PST`) that the service had to expand at read time.

**Modern schema.** `data/modern-schema/modern_tables.sql` defines the normalized target:
`borrowers`, `loan_products`, `loan_accounts`, `payments`. Types are real types — `DATE` for dates,
`DECIMAL(p,s)` for money and rates, `INTEGER` for counts/scores, `BOOLEAN` for
`loan_products.is_active`, `TIMESTAMP` for `created_at`/`updated_at`. Primary keys are
auto-increment `BIGINT` surrogate keys, with the legacy identifier preserved as a natural key
(`borrowers.external_id`, `loan_products.code`, `loan_accounts.account_number`). Relationships are
enforced by foreign keys (`loan_accounts.borrower_id`, `loan_accounts.product_id`,
`payments.loan_account_id`) with supporting indexes, and status values are expanded, self-describing
words (`ACTIVE`, `CLOSED`, `DEFAULT`, `FORBEARANCE`, `POSTED`, `REGULAR`, …). The JPA entities in
`com.workshop.loanservice.modern.entity` mirror this DDL one-to-one.

**Comparison.** The legacy schema pushes all typing, parsing and validation into application code:
`LoanService` had to strip commas, parse `MM/DD/YYYY`, and expand codes on every request, and any
bad string surfaced only at read time. The modern schema pushes that work to migration time — data
is parsed once, stored typed, and read back without parsing — while normalization removes the
duplicated borrower columns and foreign keys make loan→borrower/product and payment→loan
relationships structurally guaranteed rather than a naming convention. The trade-off is that the
modern schema must be populated by an explicit migration, and one legacy affordance is lost: a
`BOOLEAN` column cannot hold an unrecognized raw code the way a `VARCHAR` one can (see §5).

---

## 2. Architecture decisions

### Dual datasource

Two datasources coexist in the same application context (PR #49):

- `config/LegacyDataSourceConfig` — `legacyDataSource`, `legacyEntityManagerFactory`,
  `legacyTransactionManager`, bound to `spring.datasource.*` / `spring.jpa.*`, `@EnableJpaRepositories`
  over `com.workshop.loanservice.repository`. All three beans are `@Primary`.
- `config/ModernDataSourceConfig` — `modernDataSource`, `modernEntityManagerFactory`,
  `modernTransactionManager`, bound to `modern.datasource.*` / `modern.jpa.*`,
  `@EnableJpaRepositories` over `com.workshop.loanservice.modern.repository`, plus a
  `DataSourceInitializer` that applies `modern.sql.init.schema-locations`
  (`classpath:modern_tables.sql`) since `spring.sql.init` only ever targets the primary datasource.

**Why two were needed.** The migration has to read the legacy tables and write the modern tables in
the same process, and the dual-read path has to serve from modern with a legacy fallback — so
neither side can be retired while the other is in use. As soon as a second `DataSource` bean exists,
Spring Boot's datasource/JPA auto-configuration backs off (it cannot choose which one to
auto-configure), so both `EntityManagerFactory`/`TransactionManager` pairs are declared explicitly.
The legacy beans stay `@Primary` so everything that resolves a datasource or transaction manager by
type keeps resolving to legacy exactly as before: `spring.sql.init` still runs
`schema-legacy.sql`/`data-legacy.sql`, `show-sql`/dialect/open-in-view still apply, and a plain
`@Transactional` still uses the legacy manager. Modern access is opt-in by qualifier
(`@Transactional("modernTransactionManager")`, `@PersistenceContext(unitName = "modern")`).

### Toggle

`loanservice.datasource.mode` selects the read path. It is read **once at startup** in the
`LoanService` constructor via `@Value("${loanservice.datasource.mode:modern}")` and reduced to a
`boolean modernMode` field — there is no per-request or runtime re-read.

- `mode=modern` (default): dual-read. Each `LoanService` method first asks `ModernLoanReader`
  (modern repositories, `@Transactional("modernTransactionManager", readOnly = true)`); if the
  modern schema returns a result it is returned. The legacy path runs **only** when the modern
  schema returns nothing for that request (empty list / no row) — e.g. a modern datasource that was
  never migrated.
- `mode=legacy`: the original legacy path only — legacy repositories plus the `parseLegacy*` /
  `expand*` translation helpers. Unchanged from before the migration work.

---

## 3. Migration process

`DataMigrationRunner` (`com.workshop.loanservice.migration`) is a standalone `main()`. It boots its
own Spring context with `WebApplicationType.NONE`, so the migration is **never** triggered by normal
web startup — there is no production `ApplicationRunner`, `CommandLineRunner` or `@PostConstruct`
that calls `migrate()`.

```
./mvnw compile exec:java
```

`exec-maven-plugin` in `pom.xml` is preconfigured with
`mainClass = com.workshop.loanservice.migration.DataMigrationRunner`, so no `-Dexec.mainClass` is
needed. The runner calls `initializeTracking()`, then `migrate()`, prints the `MigrationReport`, and
exits **0 on overall PASS / 1 on any failed criterion or a rolled-back transaction**, so it can gate
a deployment pipeline.

`MigrationService.migrate()` is annotated `@Transactional("modernTransactionManager")`: all four
tables migrate in a **single** modern-datasource transaction, so a thrown failure rolls back every
migrated row together with its `migration_id_map` entries. Individual records that cannot be
transformed are caught, logged `WARN`, reported as skipped, and never abort the run.

Order is foreign-key driven, and each step only runs once its parents exist:

| # | Source | Target | Requires |
|---|--------|--------|----------|
| 1 | `CDW_BORR_MSTR` | `borrowers` | — |
| 2 | `CDW_LN_PROD` | `loan_products` | — |
| 3 | `CDW_LN_ACCT` | `loan_accounts` | `borrowers` + `loan_products` (FK resolution) |
| 4 | `CDW_PMT_HIST` | `payments` | `loan_accounts` (FK resolution) |

Parents are resolved via `MigrationService.resolveParent(...)`, which prefers
`MigrationIdMap.findModernId(...)` and cross-checks it against the natural-key finder
(`findByExternalId` / `findByCode` / `findByAccountNumber`), failing the record if the two disagree.
The denormalized `BORR_FST_NM` / `BORR_LST_NM` / `BORR_SSN_LST4` columns on `CDW_LN_ACCT` are
dropped — a borrower is never matched by name.

**When to run it.** Manually, **before** starting the web app in `mode=modern`. The web app assumes
the modern datasource is already populated and never migrates anything itself; if it isn't
populated, requests fall back to the legacy path.

---

## 4. Idempotency design

`migration_id_map` maps a preserved legacy id to the modern surrogate id it became. It is
**deliberately not part of `data/modern-schema/modern_tables.sql`**: it is a migration artefact, not
part of the approved application schema. `MigrationIdMap.createTableIfMissing()` issues
`CREATE TABLE IF NOT EXISTS` on its own connection from the modern `DataSource`, outside the
migration transaction (DDL auto-commits, so it cannot interfere with a rollback). Consequences of
that choice, per PR #51: the approved modern DDL stays untouched, no modern table gains a
`legacy_id` column, and `payments.id` remains a plain auto-increment that never reuses legacy
`PMT_SEQ_NBR` values.

Runtime reads/writes go through the modern `EntityManager`
(`@PersistenceContext(unitName = "modern")`, native SQL), so map rows share the connection and
transaction with the migrated entities — a rollback reverts the data *and* its map entries together.

API:

| Method | Purpose |
|---|---|
| `findModernId(entityType, legacyId)` | forward lookup, used for FK resolution |
| `exists(entityType, legacyId)` | idempotency check before insert |
| `record(entityType, legacyId, modernId, migratedAt)` | records a new mapping |
| `count(entityType)` | totals for the migration report |
| `findLegacyId(entityType, modernId)` | reverse lookup, added in PR #52 so `ModernLoanReader.toPaymentDto` can surface the preserved legacy `paymentId` |

Entity types and their legacy keys: `borrower`/`BORR_ID`, `loan_product`/`PROD_CD`,
`loan_account`/`LN_ACCT_NBR`, `payment`/`PMT_SEQ_NBR`.

Idempotency is uniform across all four: before inserting, each record is looked up
(`skipAlreadyMigrated` → `exists`) and skipped (counted as *already migrated*) if it is already
mapped; otherwise it is inserted and recorded. Re-running the migration against an
already-migrated modern datasource therefore inserts nothing and still exits 0.

---

## 5. Known data-mapping gaps

This is a **living list**: each entry is an open item for a future `data/mappings/column_mappings.md`
update. The migration behaviour described here is what the code on this branch actually does; the
mapping document is what still needs to catch up.

### `PROP_TYP_CD` → `loan_accounts.property_type` (incomplete mapping)

`column_mappings.md` line 70 specifies only `Expand: SFR→Single Family, CND→Condominium, etc.` — the
`etc.` is undefined, and the seed data contains `TWN` (and `MFR` is likewise unmapped).
**Resolution:** PR #51 (commit `b85945ed`) changed the migration from throwing/skipping to migrating
such records with the **raw** value preserved — a missing expansion is a gap in the mapping document,
not bad data, and dropping a real loan over it would be data loss. So `LN-2021-00567` migrates with
`property_type = 'TWN'`. `ModernLoanReader.displayPropertyType` then maps `TWN → "Townhouse"` and
`MFR → "Multi-Family Residence"` for display, so the API contract is preserved while the underlying
mapping gap is still visible in the stored data and in the migration report's *Codes migrated
unexpanded* section (which scans the entire legacy dataset, not just the current run's inserts).

### `PROD_STAT_CD` → `loan_products.is_active BOOLEAN` (unmapped code = skip)

Only `ACT → TRUE` and `INA → FALSE` are mapped. Because the target column is a `BOOLEAN`, it
**cannot** hold an arbitrary raw string the way `property_type` can, and guessing true/false would
silently corrupt the record. So `MigrationService.expandFlag` treats an unexpanded code as malformed:
it throws `MalformedRecordException` and the loan product is skipped and reported (PR #51 commit
`d2e96b2c`). This does not occur in the shipped seed data.

### `PMT_SEQ_NBR` → `payments.id` ("legacy ID stored if needed")

The mapping document leaves this open: `Auto-generated; legacy ID stored if needed`.
**Resolution:** the legacy sequence number is stored in `migration_id_map` (entity type `payment`)
rather than by adding a column to `payments`, and the API's `paymentId` is reproduced through the
reverse lookup `MigrationIdMap.findLegacyId(PAYMENT, modernId)`.

### Behaviour verified against the final code (not the intermediate snapshot)

Worth recording because the branch history is misleading: the first migration commit
(`5fdcd9a5`) threw `MalformedRecordException` for **all** unmapped codes. Later PR #51 commits
changed that. The **final** `MigrationService.java` on this branch behaves as follows, which is what
the sections above describe:

- `expand(...)` — used for `BORR_STAT_CD`, `LN_STAT_CD`, `PROP_TYP_CD`, `PMT_TYP_CD`,
  `PMT_STAT_CD`: logs a warning and **returns the raw code** when no expansion exists.
- `expandFlag(...)` — used only for `PROD_STAT_CD` → `is_active`: **throws**
  `MalformedRecordException`, so the record is skipped.
- `scanForMappingGaps(...)` reports unexpanded codes for the five `expand(...)` fields and
  deliberately omits `PROD_STAT_CD`, which surfaces under *Skipped records* instead.

---

## 6. API contract preservation

`ModernLoanReader` keeps four display-expansion methods, pointed at the modern stored values rather
than removed:

| Method | Stored (modern) → returned (API) |
|---|---|
| `displayStatus` | `ACTIVE→Active`, `CLOSED→Closed`, `DEFAULT→Default`, `FORBEARANCE→Forbearance` |
| `displayPropertyType` | `Single Family→Single Family Residence`, `Condominium→Condominium`, raw `TWN→Townhouse`, raw `MFR→Multi-Family Residence` |
| `displayPaymentType` | `REGULAR→Regular`, `EXTRA→Extra`, `PARTIAL→Partial`, `PREPAYMENT→Prepayment` |
| `displayPaymentStatus` | `POSTED→Posted`, `REVERSED→Reversed`, `NSF→Non-Sufficient Funds`, `PENDING→Pending` |

The modern schema intentionally stores **canonical** values — statuses and types in ALL-CAPS
(`ACTIVE`, `POSTED`, `REGULAR`) and `property_type` in its stored form (`Single Family`) — whereas
the historical API contract returns **title-case display strings** (`"Active"`, `"Posted"`,
`"Regular"`, `"Single Family Residence"`). Removing the expansion step would therefore have changed
the JSON payload. Keeping these methods (each with `null → "Unknown"` and `default → value`, matching
the legacy helpers) is what makes `mode=modern` JSON byte-for-byte identical to `mode=legacy`. They
also gracefully render the raw legacy codes that were migrated as-is (`TWN`, `MFR`), so a mapping gap
never leaks a cryptic code into the API.

`ModernLoanReader.wholeDollar()` is the one formatting-only counterpart on the numeric side: legacy
parsed `"285,000"` to scale-0 `285000`, while `original_amount DECIMAL(12,2)` reads back
`285000.00`, so a purely-zero fractional part is dropped. No DTO field or type changed.

---

## 7. Verification

**Test harness.** `src/test/java/com/workshop/loanservice/verification/EndpointParityVerificationTest`
boots the app with `@SpringBootTest(RANDOM_PORT)` under `@ActiveProfiles("verification")` and imports
`VerificationMigrationConfig` — a test-only `@TestConfiguration` (never component-scanned into the
main context) whose `@Profile("verification")` `ApplicationRunner`, ordered
`Ordered.HIGHEST_PRECEDENCE`, calls `initializeTracking()` then `migrate()`. So the migration
genuinely runs into an empty in-memory modern DB before any assertion; the modern data is not
pre-seeded. A guard test, `verificationMigrationPopulatedTheModernDatasource`, asserts the modern
`loan_accounts`, `payments` and `migration_id_map` are non-empty — without it, `LoanService` could
silently fall back to legacy and make the parity check vacuous.

**Golden files.** `src/test/resources/golden/*.json` capture the legacy contract: the app was booted
in `mode=legacy`, all 5 endpoints called, and each raw response written verbatim (no
re-serialization) so legacy number rendering such as `4.750` and `0.00` is preserved.

**Structural comparison.** PR #53 replaced the byte-for-byte assertion with a single recursive
structural JSON comparator (Jackson) reused for all 5 endpoints, checking field presence (same field
set), JSON node type, and value. Arrays must match in length and compare element-wise. Numbers are
compared by `BigDecimal.compareTo`, so a difference that is only decimal scale (`4.75` vs `4.750`) is
reconciled and reported as a formatting note instead of masking or faking a value match; any real
value difference fails. Failures are rendered per field with a JSON path so a reviewer can decide on
each one.

**Excluded field: `paymentId`.** It is excluded from the exact structural match because it is not a
migrated value at all — it is a modern-only auto-generated primary key (`payments.id`), whose value
depends on insertion order and the auto-increment sequence rather than on any legacy column, so
asserting equality against a golden capture would assert an implementation detail of the modern
database. It is validated separately in `excludedPaymentIdIsPresentUniqueAndCorrectlyMapped`:
present, non-null, unique across the response, and FK-correct — resolved through
`MigrationIdMap.findModernId(PAYMENT, id)` to a `Payment` whose `loanAccount.accountNumber` matches
the JSON `loanAccountNumber`. The exclusion is purely test-side; `ModernLoanReader.toPaymentDto` is
untouched.

**Result.** The parity report lives at `docs/PARITY_REPORT.md`; all 5 endpoints PASS
(`GET /api/loans`, `/api/loans/{id}`, `/api/loans/{loanId}/payments`, `/api/borrowers`,
`/api/borrowers/{id}`).

---

## 8. Known limitations / deferred items

- **Enum/CHECK constraints documented but not enforced.** The `modern_tables.sql` header advertises
  "Enum-like status fields with CHECK constraints", but the DDL defines **no** `CHECK` constraints and
  the JPA entities map these columns as plain `String` (no `@Enumerated`, no Bean Validation). So the
  documented value sets for `loan_products.type` / `rate_type`, `loan_accounts.status`,
  `borrowers.status`, `payments.type` / `status` are enforced by neither the database nor the
  entities. This matters precisely because the migration writes expanded codes — and, per §5,
  sometimes a raw unexpanded one. Adding the CHECKs to the DDL and/or modelling the columns as
  `@Enumerated(EnumType.STRING)` enums is deferred: the DDL is the approved source of truth.
- **Payments endpoint path discrepancy.** The actual endpoint is
  `GET /api/loans/{loanId}/payments`; the README was corrected to match in PR #49 (commit
  `d1dfb5e1`). The original scaffold README informally referenced `/api/payments/loan/{loanId}`,
  which never existed. Older notes or scripts using that path will 404.
- **Idempotency is not demonstrable with the default configuration.** Both
  `spring.datasource.url` and `modern.datasource.url` default to in-memory H2
  (`jdbc:h2:mem:...`), so both databases are recreated on every JVM start and a second migration run
  always starts from an empty modern schema. Demonstrating (or relying on) idempotency requires a
  persistent `modern.datasource.url`, e.g. `--modern.datasource.url=jdbc:h2:file:./target/moderndb`.
  The same applies to running the web app in `mode=modern`: with an in-memory modern URL, the data
  migrated by a separate `DataMigrationRunner` JVM does not exist for the web JVM, and the dual-read
  path falls back to legacy.
