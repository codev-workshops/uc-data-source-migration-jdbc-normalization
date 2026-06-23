# Data Source Migration Notes

Migration of `loan-service` from the legacy CDW (Corporate Data Warehouse)
tables to the modern, normalized schema — **with byte-for-byte identical REST
API responses**.

## Goal & invariant

The single hard requirement: every endpoint must return exactly the same JSON
after the migration as before. The five endpoints are the contract:

- `GET /api/loans`
- `GET /api/loans/{accountNumber}`
- `GET /api/borrowers`
- `GET /api/borrowers/{borrowerId}`
- `GET /api/loans/{accountNumber}/payments`

Captured "golden" responses (`src/test/resources/golden/`) act as the
regression oracle and are asserted by an automated test.

## Architecture decision: single H2 + startup migration

Task 1 mentions a "second data source". A true second `DataSource` (separate
`EntityManagerFactory` / `TransactionManager` / persistence units and split
entity packages) is the highest boot-time risk part of this work and is overkill
for an in-memory demo. Instead:

- **One in-memory H2 instance holds both schemas.** `schema-legacy.sql` and
  `schema-modern.sql` are both created at startup; only the legacy tables are
  seeded (`data-legacy.sql`).
- **A startup migration bridge** (`DataMigrationRunner`, a `CommandLineRunner`)
  reads the legacy rows, transforms them, resolves foreign keys, and writes the
  modern tables.
- After boot, the **API serves exclusively from the modern schema.**

This keeps every change incremental and non-breaking while still demonstrating
the full legacy → modern data migration (Task 2).

### Why legacy code is retained (deprecate, not delete)

Because the only seed data in the app lives in `data-legacy.sql`, the legacy
tables/entities/repositories are the **source** the startup migration reads
from. Deleting them would leave the modern tables empty. They are therefore
retained but clearly documented as *migration-source-only* (see the javadoc on
each `Legacy*` class) and are **not on the API read path**. To remove legacy
entirely, drop the migration bridge and seed the modern tables directly with a
`data-modern.sql` — at the cost of no longer demonstrating the migration.

## What changed

| Layer | Legacy | Modern |
|-------|--------|--------|
| Tables | `CDW_BORR_MSTR`, `CDW_LN_PROD`, `CDW_LN_ACCT`, `CDW_PMT_HIST` (all VARCHAR, denormalized) | `borrowers`, `loan_products`, `loan_accounts`, `payments` (typed, normalized, FKs) |
| Entities | `LegacyBorrower`, `LegacyLoanProduct`, `LegacyLoanAccount`, `LegacyPayment` (all `String`) | `Borrower`, `LoanProduct`, `LoanAccount`, `Payment` (`LocalDate`/`BigDecimal`/`Long`/`Boolean`, `@ManyToOne`/`@OneToMany`) |
| Repositories | string-key finders | business-key + FK-navigation finders (`findByExternalId`, `findByBorrower_ExternalId`, `findByLoanAccount_AccountNumberOrderByPaymentDateDesc`) |
| Service | parsed strings on every read | reads typed modern entities; only presentation formatting remains |

## Type transformations (DataMigrationRunner)

- `MM/DD/YYYY` string → `LocalDate` (formatter `M/d/yyyy`); timestamps → `LocalDateTime` at start of day.
- `"285,000"` / `"271,432.56"` → `BigDecimal` (strip commas).
- numeric strings (credit score, term, delinquency days) → `Integer`.
- Status / type codes → canonical values (e.g. `ACT`→`ACTIVE`, `REG`→`REGULAR`, `PST`→`POSTED`).
- Foreign keys resolved from business keys: borrower `external_id`, product `code`, loan `account_number` → modern `BIGINT` PKs.
- Nulls/blanks preserved as `null` (e.g. borrower middle initial).
- Legacy payment id (`PMT-...`) carried into `payments.external_id`.

Validation: after loading, row counts are asserted against the legacy source
(5 borrowers / 5 products / 5 loan accounts / 10 payments); a mismatch fails boot.

## Contract preservation (the subtle traps)

The modern schema stores clean canonical data; the **service layer formats for
the API contract**. This is presentation, not type parsing. Each preserved
behavior:

1. **Decimal scale of `originalAmount`.** Source amounts had no cents
   (`285000`); a `DECIMAL(12,2)` column reads back `285000.00`. The service
   strips trailing zeros for `originalAmount` only (`285000`), while
   `currentBalance` / `monthlyPayment` keep scale 2 (including a real trailing
   zero like `142567.90`).
2. **Loan `borrowerName`.** Built from the borrower FK as `first last` **without**
   middle initial (`James Mitchell`) — distinct from `BorrowerDto.fullName`
   which includes it (`James R. Mitchell`).
3. **Loan `status` display.** Stored `ACTIVE` → emitted `Active` (and
   `CLOSED`→`Closed`, etc.).
4. **`propertyType`.** Stored canonical `Single Family` → emitted
   `Single Family Residence` (`Multi-Family`→`Multi-Family Residence`, etc.).
5. **Payment `type` / `status` display.** `REGULAR`→`Regular`, `POSTED`→`Posted`,
   `NSF`→`Non-Sufficient Funds`, etc.
6. **Date formatting.** `LocalDate` → `MM/dd/yyyy` on output.
7. **`paymentId`.** Returns the carried legacy id (`payments.external_id`), not
   the modern auto-increment PK.
8. **Payment ordering.** `findBy...OrderByPaymentDateDesc` over a real `DATE`.
9. **Not-found behavior.** `getLoanById` / `getBorrowerById` still throw on a
   missing id, preserving the error path.

## Validation

`GoldenFileRegressionTest` boots the app on a random port and asserts each
endpoint's raw response body equals the corresponding file in
`src/test/resources/golden/`. **No intentional differences** — all five
endpoints match exactly.

## Files

- `src/main/resources/schema-modern.sql` — modern DDL (mirrors
  `data/modern-schema/modern_tables.sql`, plus `payments.external_id` to carry
  the legacy payment id).
- `entity/{Borrower,LoanProduct,LoanAccount,Payment}.java` — modern entities.
- `repository/{Borrower,LoanProduct,LoanAccount,Payment}Repository.java` — modern repos.
- `migration/DataMigrationRunner.java` — legacy → modern startup migration.
- `service/LoanService.java` — rewired to modern repos; presentation formatting only.
- `src/test/resources/golden/` + `GoldenFileRegressionTest` — regression oracle.

## Possible follow-ups (bonus)

- Dual-read feature flag to switch legacy/modern at runtime.
- SQL reconciliation queries comparing legacy vs modern.
- Performance comparison between the VARCHAR-everything and typed schemas.
