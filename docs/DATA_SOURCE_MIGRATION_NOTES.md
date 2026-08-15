# Data Source Migration Notes — legacy CDW → modern normalized schema

## 1. Two data sources, and why

| | Legacy | Modern |
|---|---|---|
| JDBC URL | `jdbc:h2:mem:legacydw` | `jdbc:h2:mem:moderndb` |
| Property prefix | `spring.datasource.legacy.*` | `spring.datasource.modern.*` |
| Config class | `LegacyDataSourceConfig` (`@Primary`) | `ModernDataSourceConfig` |
| Entities | `com.workshop.loanservice.entity` (all-`String` CDW mirrors) | `com.workshop.loanservice.modern.entity` (typed) |
| Repositories | `...loanservice.repository` | `...loanservice.modern.repository` |
| Transaction manager | `legacyTransactionManager` | `modernTransactionManager` |
| Schema/data | `schema-legacy.sql` + `data-legacy.sql` via `spring.sql.init.*` (primary datasource only) | `schema-modern.sql` (+ intentionally empty `data-modern.sql`), executed in the `modernDataSource` bean |
| Role | source of record for migration, reconciliation and rollback | **active** source behind `LoanService` and every public endpoint |

`DataSourceAutoConfiguration` is excluded (`spring.autoconfigure.exclude`) because two
data sources cannot be auto-configured from a single `spring.datasource.*` block. Both
data sources are therefore built explicitly from bound `DataSourceProperties`. The legacy
beans stay `@Primary` so Boot's SQL initializer keeps seeding the CDW tables and so that
any unqualified injection point still resolves.

## 2. Entity / type mapping decisions

The legacy CDW tables store everything as `VARCHAR`. The modern entities use real types:

| Legacy (String) | Modern |
|---|---|
| `BORR_DOB_DT`, `LN_ORIG_DT`, `PMT_DT`, … (`MM/DD/YYYY`) | `LocalDate` |
| `*_CRET_DT` / `*_UPDT_DT` | `LocalDateTime` (midnight of the parsed date) |
| `BORR_ANN_INCM`, `LN_ORIG_AMT`, `PMT_TOT_AMT`, … (`"285,000.00"`) | `BigDecimal` |
| `BORR_CRDT_SCR`, `LN_TERM_MOS`, `LN_DELQ_DAYS` | `Integer` |
| product `PROD_STAT_CD` (`ACT`/`INA`) | `Boolean isActive` |

Business keys are preserved so the two schemas stay reconcilable and the public API keys
are unchanged: `BORR_ID` → `borrowers.external_id`, `PROD_CD` → `loan_products.code`,
`LN_ACCT_NBR` → `loan_accounts.account_number`, `PMT_SEQ_NBR` → `payments.external_id`.

> `payments.external_id` is the one addition to the supplied modern DDL. The public
> `PaymentDto.paymentId` is the legacy sequence number, so it has to survive the
> migration; it also gives payments a business key for idempotency.

## 3. ETL rules (`DataMigrationService`)

Order: `borrowers → loan_products → loan_accounts → payments`.

Transformations follow `data/mappings/column_mappings.md`:

* dates: `MM/DD/YYYY` → `LocalDate` / `LocalDateTime`
* amounts: strip `,` → `BigDecimal`
* integers: parsed, blank → `null`
* borrower status `ACT`→`ACTIVE`, `INA`→`INACTIVE`
* product status `ACT`→`is_active = true`, `INA`→`false`
* loan status `ACT`/`CLO`/`DFT`/`FRB` → `ACTIVE`/`CLOSED`/`DEFAULT`/`FORBEARANCE`
* property type `SFR`/`CND`/`MFR`/`TWN` → `SINGLE_FAMILY_RESIDENCE`/`CONDOMINIUM`/`MULTI_FAMILY_RESIDENCE`/`TOWNHOUSE`
* payment type `REG`/`EXT`/`PRT`/`PRE` → `REGULAR`/`EXTRA`/`PARTIAL`/`PREPAYMENT`
* payment status `PST`/`REV`/`NSF`/`PND` → `POSTED`/`REVERSED`/`NSF`/`PENDING`

Null/blank handling: optional dates become `null`; amount and decimal columns become
`BigDecimal.ZERO` (matching what the legacy service returned for blanks); blank integers
become `null`.

FK resolution: loan accounts resolve their borrower via `findByExternalId(BORR_ID)` and
their product via `findByCode(PROD_CD)`; payments resolve their account via
`findByAccountNumber(LN_ACCT_NBR)`. A row whose parent cannot be resolved fails the
migration rather than being silently dropped.

**Idempotency** — every row is looked up by its business key first and skipped if already
present, so re-running `migrateAll()` (startup + `POST /api/admin/migrate`) is safe.

**Transactional safety** — `migrateAll()` is `@Transactional("modernTransactionManager")`,
so any failure rolls back the entire migration and leaves the modern schema empty rather
than half-populated.

**Startup ordering** — `MigrationRunner` invokes `migrateAll()` from `@PostConstruct`,
i.e. during context refresh and therefore before Tomcat starts accepting requests. The
modern schema itself is created inside the `modernDataSource` bean method, so the tables
exist before JPA or the runner touch the database.

## 4. API parity

`LoanService` now reads typed modern entities; no `parseLegacyAmount` /
`parseLegacyDecimal` / `parseLegacyInteger` remains in DTO mapping. What is left is purely
presentational, and exists so the JSON does not change:

* `LocalDate` is formatted back to `MM/DD/YYYY` (`LoanSummaryDto.originationDate`,
  `PaymentDto.paymentDate`) — the DTOs keep `String` date fields.
* Modern uppercase codes are mapped back to the legacy title-case display strings
  (`ACTIVE`→`Active`, `REGULAR`→`Regular`, `POSTED`→`Posted`, `NSF`→`Non-Sufficient Funds`,
  `SINGLE_FAMILY_RESIDENCE`→`Single Family Residence`, …). The API never returns raw codes.
* `borrowerName`, `fullName`, `propertyAddress` concatenation and `productDescription`
  (product name) are assembled exactly as before.

Verified two ways: `ApiParityTest` compares all five endpoints against goldens captured
from the legacy implementation (`src/test/resources/golden/`), and `GET /api/validation`
reports row counts plus a per-record, per-field diff of legacy-derived vs modern-derived
DTOs (`inParity: true`). There are **no intentional differences**.

## 5. Assumptions

* Seed volumes: 5 borrowers, 5 products, 5 loan accounts, 10 payments.
* All legacy date strings use `MM/DD/YYYY`; legacy amounts use `,` thousands separators.
* Legacy timestamps carry a date only, so `createdAt`/`updatedAt` land at midnight.
* Both databases are in-memory and recreated per JVM, so the migration runs on every boot.

## 6. Rollback

The legacy datasource, entities and repositories are untouched and still seeded on every
start; they are marked `@Deprecated` only to signal that production request paths no
longer use them. To roll back, re-point `LoanService`'s constructor at the legacy
repositories (the previous mapping logic is preserved verbatim in
`validation/LegacyDtoAssembler`) — no data restore is needed, because the migration only
ever writes to the modern database and never mutates the legacy one.
