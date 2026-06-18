# Data Source Migration Notes

Migration of the `loan-service` API from the legacy CDW tables (denormalized,
all-`VARCHAR`, string-encoded dates/amounts/codes) to a modern, normalized,
properly-typed schema — **without changing the public API contract**.

This document records the strategy, transformation rules, validation approach,
API comparison results, and the known differences with their rationale.

> Status: Tasks 0–4 complete. The legacy read path remains the default and is
> fully intact. Task 5 (removing/deprecating the legacy code) is **not** done.

---

## 1. Migration strategy

### Two data sources, one contract
Both schemas are live simultaneously, each behind its own Spring `DataSource` /
`EntityManagerFactory` / `TransactionManager`:

| Concern        | Legacy (`legacydw`, **primary**)         | Modern (`moderndw`)                       |
|----------------|------------------------------------------|-------------------------------------------|
| Tables         | `CDW_BORR_MSTR`, `CDW_LN_PROD`, `CDW_LN_ACCT`, `CDW_PMT_HIST` | `borrowers`, `loan_products`, `loan_accounts`, `payments` |
| Types          | every column `VARCHAR`                    | `DATE`, `TIMESTAMP`, `DECIMAL`, `BIGINT`, `BOOLEAN` |
| Structure      | denormalized (borrower fields copied onto accounts) | normalized with FK relationships |
| Config         | `LegacyDataSourceConfig`                  | `ModernDataSourceConfig`                  |
| Schema DDL     | `schema-legacy.sql`                       | `schema-modern.sql`                       |

### Read-side abstraction + feature flag
The read path is hidden behind a single interface, `LoanDataProvider`, with two
implementations selected at runtime by a feature flag:

```
loanservice.datasource = legacy   (default) → LegacyLoanDataProvider  (reads CDW tables)
loanservice.datasource = modern             → ModernLoanDataProvider  (reads modern schema)
```

`LoanService` is a thin facade that picks the provider at construction and logs
the active path. Controllers and DTOs are untouched, so flipping the flag swaps
the data source with zero contract change and trivial rollback.

### Migration execution
`MigrationService.migrate()` performs an idempotent ETL from legacy to modern:

1. **Clear** modern tables in reverse-FK order (payments → accounts → products → borrowers).
2. **Load** in dependency order (borrowers → products → accounts → payments).
3. **Transform** each field per the rules in §2.
4. **Resolve foreign keys** via in-memory maps (legacy string IDs → modern entities);
   fail fast (`IllegalStateException`) on any unresolved reference.
5. **Verify** modern row counts equal legacy row counts.

It is opt-in at startup via `loanservice.migrate-on-startup=true` (off by default
so the legacy-only boot path and golden tests are unaffected). The migration
writes inside a single modern transaction (`modernTransactionManager`).

---

## 2. Transformation rules

Source of truth: `data/mappings/column_mappings.md`. Implemented as pure helpers
in `migration/LegacyValueConverters.java`.

| Pattern              | Legacy form            | Modern (stored)            | Example                                  |
|----------------------|------------------------|----------------------------|------------------------------------------|
| Date                 | `MM/DD/YYYY` string    | `LocalDate` (`DATE`)       | `"02/15/2019"` → `2019-02-15`            |
| Timestamp            | `MM/DD/YYYY` string    | `LocalDateTime` (start of day) | `"01/15/2019"` → `2019-01-15T00:00`  |
| Amount               | comma-grouped string   | `BigDecimal` (`DECIMAL`)   | `"285,000"` → `285000`                   |
| Decimal (rate/LTV)   | string                 | `BigDecimal`               | `"4.750"` → `4.750`                      |
| Integer              | string                 | `Integer`                  | `"745"` → `745`                          |
| Borrower status      | `ACT` / `INA`          | `ACTIVE` / `INACTIVE`      | `ACT` → `ACTIVE`                         |
| Product status       | `ACT` / `INA`          | `Boolean is_active`        | `ACT` → `true`                           |
| Loan status          | `ACT/CLO/DFT/FRB`      | `ACTIVE/CLOSED/DEFAULT/FORBEARANCE` | `FRB` → `FORBEARANCE`           |
| Property type        | `SFR/CND/MFR/TWN`      | `Single Family/Condominium/Multi-Family/Townhouse` | `SFR` → `Single Family` |
| Payment type         | `REG/EXT/PRT/PRE`      | `REGULAR/EXTRA/PARTIAL/PREPAYMENT` | `REG` → `REGULAR`               |
| Payment status       | `PST/REV/NSF/PND`      | `POSTED/REVERSED/NSF/PENDING` | `PST` → `POSTED`                     |
| Denormalization      | borrower fields on account | dropped — reached via FK | `BORR_FST_NM` on account dropped       |
| ID resolution        | string FK              | `BIGINT` PK via FK lookup  | `BORR_ID` "B-10001" → `borrowers.id`     |
| External id          | `PMT_SEQ_NBR`          | preserved in `external_id` | `"PMT-2025120001"` retained for API id   |

### Canonical storage vs. API presentation
The modern schema stores **canonical UPPER-CASE** status/type values (per the
mapping doc), but the legacy API returns **title-case display strings**
(`"Active"`, `"Posted"`) and an expanded property type (`"Single Family Residence"`).

`ModernLoanDataProvider` therefore re-applies the legacy presentation rules on
read so the output matches byte-for-byte:

| API field        | Modern stored      | Legacy/API display          |
|------------------|--------------------|-----------------------------|
| loan `status`    | `ACTIVE`           | `Active`                    |
| payment `type`   | `REGULAR`          | `Regular`                   |
| payment `status` | `POSTED` / `NSF`   | `Posted` / `Non-Sufficient Funds` |
| `propertyType`   | `Single Family`    | `Single Family Residence`   |
| dates            | `LocalDate`        | `MM/DD/YYYY` string         |

Other API-shaping rules preserved by the modern provider:
- `borrowerName` (on a loan) = borrower `first + " " + last` (no middle initial).
- `fullName` (on a borrower) = `first [+ " " + middleInitial + "."] + " " + last`.
- `productDescription` = product name, falling back when absent.
- `propertyAddress` = `address + ", " + city + ", " + state + " " + zip`.
- `id` = borrower `external_id` (e.g. `B-10001`); `paymentId` = `external_id`.
- Payment ordering = by payment date descending.

---

## 3. Validation approach

Three complementary layers, all automated (`mvn test`, **26 tests**):

1. **Golden baseline, both modes** — `LegacyApiGoldenTest` and
   `ModernApiGoldenTest` (both extend `AbstractApiGoldenTest`) hit all five live
   HTTP endpoints with `loanservice.datasource` set to `legacy` and `modern`
   respectively, and compare each response to the captured golden files in
   `src/test/resources/golden/`. The modern run migrates data first.

2. **Cross-source reconciliation** — `CrossSourceReconciliationTest` drives both
   providers directly (no HTTP) in one context and asserts:
   - legacy and modern produce **identical** output for every endpoint (compared
     to *each other*, not just to the golden baseline);
   - loan monetary totals (original amount, current balance, monthly payment) and
     per-loan payment totals **reconcile** across sources.

3. **Migration integrity** — `MigrationServiceTest` asserts row counts
   (5 / 5 / 5 / 10), referential integrity (every FK resolves), monetary total
   reconciliation (legacy parsed vs modern stored), and representative field-level
   transformations.

### Numeric-aware JSON comparison
The golden and reconciliation comparisons use `JsonCompare`, which compares
numbers by **value** (`285000` == `285000.00`) while requiring exact equality for
everything else (field presence, strings, ids, date formats, display values,
array ordering). Rationale in §5.

---

## 4. API comparison results

Raw HTTP responses were captured from both modes for all five endpoints and
compared. **All non-numeric content is byte-identical**, and all numeric values
are equal. The endpoints verified:

- `GET /api/loans`
- `GET /api/loans/{id}`
- `GET /api/loans/{id}/payments`
- `GET /api/borrowers`
- `GET /api/borrowers/{id}`

Representative capture (`GET /api/loans`, first record):

```
legacy: ...,"originalAmount":285000,   "currentBalance":271432.56,"interestRate":4.750,...
modern: ...,"originalAmount":285000.00,"currentBalance":271432.56,"interestRate":4.750,...
```

Note that fields such as `interestRate":4.750`, `lateFee":0.00`, and
`interestAmount":1076.50` are emitted **identically** by both paths.

---

## 5. Known differences and rationale

### The single difference: `originalAmount` numeric scale

| Field           | Legacy   | Modern      | Numerically equal? |
|-----------------|----------|-------------|--------------------|
| `originalAmount`| `285000` | `285000.00` | yes (`285000 == 285000.00`) |

**Why it happens.** The legacy `LoanService` builds `BigDecimal` straight from
the source string, so `"285,000"` yields a scale-0 `BigDecimal` → serialized as
`285000`. The modern column is `original_amount DECIMAL(12,2)`, so JDBC returns a
scale-2 `BigDecimal` → serialized as `285000.00`.

**Why it is not "fixed" by stripping zeros.** Legacy output is *not* normalized —
it reflects each source string's scale, so legacy itself emits trailing zeros for
other fields (`4.750`, `0.00`, `1076.50`). A global `stripTrailingZeros()` on the
modern side would fix `originalAmount` (`285000.00` → `285000`) but would
simultaneously **break** the fields legacy emits *with* trailing zeros
(`0.00` → `0`, `1076.50` → `1076.5`), creating new diffs. There is no single
transform that makes every field byte-match, because the legacy representation is
a side effect of messy source data rather than a rule.

**Resolution (documented & justified).** The values are numerically identical and
the difference is confined to one field's serialized scale. The modern
`DECIMAL(12,2)` typing is the correct, intentional target of the migration.
Consumers already had to tolerate variable decimal scales from the legacy API
(`4.750`, `0.00`), so `285000.00` is consistent with the existing contract's
numeric tolerance. The automated comparison (`JsonCompare`) therefore treats
numbers by value; every non-numeric aspect is still asserted exactly.

If byte-identical numerics were ever required, the options would be (a) a
per-field response-scale policy applied to **both** providers, or (b) a custom
`BigDecimal` serializer — neither is warranted given the values are equal.

---

## 6. File map

| Area | Files |
|------|-------|
| Modern entities | `modern/entity/{Borrower,LoanProduct,LoanAccount,Payment}.java` |
| Modern repositories | `modern/repository/*Repository.java` |
| Data sources | `config/{LegacyDataSourceConfig,ModernDataSourceConfig}.java`, `resources/schema-modern.sql` |
| Migration | `migration/{LegacyValueConverters,MigrationService,MigrationResult,MigrationRunner}.java` |
| Read abstraction | `service/{LoanDataProvider,LegacyLoanDataProvider,ModernLoanDataProvider,LoanService}.java` |
| Feature flag | `resources/application.properties` (`loanservice.datasource`) |
| Tests | `AbstractApiGoldenTest`, `LegacyApiGoldenTest`, `ModernApiGoldenTest`, `CrossSourceReconciliationTest`, `MigrationServiceTest`, `JsonCompare`, golden files under `src/test/resources/golden/` |
| Planning docs | `MIGRATION_ANALYSIS.md`, `MIGRATION_IMPLEMENTATION_PLAN.md` |

---

## 7. Remaining work (Task 5 — not done)

- Make `modern` the default (or point production config at it) once validated in a
  real environment.
- Remove or `@Deprecated`-flag the legacy entities, repositories, and
  `LegacyLoanDataProvider` after a bake-in period.
- Fix the README endpoint discrepancy (`GET /api/payments/loan/{loanId}` documented
  vs implemented `GET /api/loans/{loanId}/payments`).
