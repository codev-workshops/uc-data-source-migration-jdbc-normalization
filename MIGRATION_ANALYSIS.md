# Migration Analysis: Legacy to Modern Data Source

> Assessment report for the `loan-service` application. No code changes have been
> made — this document is analysis and planning only.

## 1. Project Overview

`loan-service` is a small Spring Boot 3.2 (Java 17) REST application that manages
mortgage/loan data: borrowers, loan products, loan accounts, and payment history.

It is a workshop project whose explicit goal is to **migrate the application's data
source from a legacy "Corporate Data Warehouse" (CDW) style schema to a clean,
normalized modern schema** — without changing the externally observable REST API.

Both the legacy and modern data stores are simulated with an in-memory **H2**
database; the legacy schema is what the app reads from today, and the modern schema
is the target.

**Tech stack**
- Java 17
- Spring Boot 3.2.3 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`)
- Spring Data JPA / Hibernate
- H2 (in-memory, runtime scope)
- Maven (with Maven Wrapper)

**Key paths**
- `src/main/java/com/workshop/loanservice/` — application code
- `src/main/resources/` — `application.properties`, `schema-legacy.sql`, `data-legacy.sql`
- `data/legacy-schema/` — legacy DDL documentation
- `data/modern-schema/modern_tables.sql` — target DDL
- `data/mappings/column_mappings.md` — full legacy→modern column mapping
- `docs/MIGRATION_TASKS.md` — workshop task definitions

## 2. Current Architecture

```
Client ─► Controllers ─► LoanService ─► Legacy Repositories ─► H2 (legacy CDW schema)
                            │
                            └─ translation logic (string → typed)
```

Layered Spring MVC architecture:

- **Controllers** (`controller/`)
  - `LoanController` → `/api/loans`, `/api/loans/{id}`, `/api/loans/{loanId}/payments`
  - `BorrowerController` → `/api/borrowers`, `/api/borrowers/{id}`
  - Note: the README also advertises `GET /api/payments/loan/{loanId}`, but the
    actual implemented payments endpoint is `GET /api/loans/{loanId}/payments`
    (see `LoanController`). This is a documentation discrepancy to reconcile.
- **Service** (`service/LoanService.java`)
  - The heart of the app. Reads from the four legacy repositories and **translates
    cryptic, all-`VARCHAR` legacy fields into clean DTOs**.
  - Contains all the messy conversion logic: `parseLegacyAmount` (strips commas),
    `parseLegacyDecimal`, `parseLegacyInteger`, and code-expansion `switch`
    statements (`expandStatusCode`, `expandPropertyType`, `expandPaymentType`,
    `expandPaymentStatus`).
  - Builds derived fields in Java (e.g. `borrowerName` from embedded first/last
    name on the loan row; `fullName` with middle initial; concatenated
    `propertyAddress`).
- **Repositories** (`repository/`)
  - `LegacyBorrowerRepository`, `LegacyLoanAccountRepository`,
    `LegacyLoanProductRepository`, `LegacyPaymentRepository` — Spring Data JPA
    interfaces over the legacy entities. IDs are `String`.
- **Entities** (`entity/`)
  - `LegacyBorrower`, `LegacyLoanAccount`, `LegacyLoanProduct`, `LegacyPayment` —
    mapped to the CDW tables. Every column is a `String`.
- **DTOs** (`dto/`)
  - `BorrowerDto`, `LoanSummaryDto`, `PaymentDto` — already use proper types
    (`BigDecimal`, `Integer`, etc.) and are the API response shapes.

**Data initialization**: `application.properties` sets
`spring.jpa.hibernate.ddl-auto=none` and uses Spring SQL init
(`spring.sql.init.mode=always`) to run `schema-legacy.sql` then `data-legacy.sql`
on startup into `jdbc:h2:mem:legacydw`.

## 3. Business Purpose

The application is a **loan/mortgage servicing read API**. It exposes consolidated,
human-readable views of:

- **Borrowers** — name, contact info, credit score, employment status, and the
  loans they hold.
- **Loan products** — the catalog of mortgage products (30-yr fixed, 15-yr fixed,
  5/1 ARM, FHA, VA), with terms, rate types, and amount limits.
- **Loan accounts** — individual mortgages: balances, interest rate, monthly
  payment, status (active/closed/default/forbearance), property details, LTV.
- **Payments** — payment history per loan with principal/interest/escrow breakdown,
  late fees, payment type, and status.

The seed data models a realistic servicing portfolio (5 borrowers, 5 products,
5 loan accounts, 10 payments). The business value of the migration is to move off a
fragile, loosely-typed legacy warehouse representation onto a properly-typed,
normalized, referentially-correct schema — improving data integrity, query
performance, and maintainability — while keeping the API contract stable for
consumers.

## 4. Existing Data Sources (Legacy)

A single H2 datasource (`jdbc:h2:mem:legacydw`) initialized from
`schema-legacy.sql` + `data-legacy.sql`. Four denormalized CDW tables, **all
columns `VARCHAR`**:

| Legacy table | Purpose | Rows | Notable legacy traits |
|---|---|---|---|
| `CDW_BORR_MSTR` | Borrower master | 5 | Cryptic names (`BORR_FST_NM`), dates as `MM/DD/YYYY` strings, income as `"92,500"`, status code `ACT`, extra `BORR_REC_TYP` |
| `CDW_LN_PROD` | Loan products | 5 | Term/amounts as strings, `PROD_STAT_CD` (ACT/INA) instead of boolean |
| `CDW_LN_ACCT` | Loan accounts | 5 | **Denormalized** — duplicates `BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4`; amounts/rates/dates all strings; status codes `ACT/CLO/DFT/FRB`; property type `SFR/CND/MFR/TWN` |
| `CDW_PMT_HIST` | Payment history | 10 | String amounts/dates; type codes `REG/EXT/PRT/PRE`; status codes `PST/REV/NSF/PND` |

**Characteristics of the legacy schema** (documented in `data/legacy-schema/cdw_tables.sql`):
1. Everything is `VARCHAR` (loose typing)
2. Dates stored as `MM/DD/YYYY` strings
3. Amounts stored as strings with commas (e.g. `"285,000"`)
4. Abbreviated/cryptic column names
5. Denormalization (borrower fields embedded in loan accounts)
6. No foreign-key constraints
7. Cryptic status-code abbreviations

The Java entities/repositories under `entity/` and `repository/` (all prefixed
`Legacy*`) map directly to these tables, and `LoanService` performs all
string→type translation at read time.

## 5. Target Data Sources (Modern)

Defined in `data/modern-schema/modern_tables.sql`. Four normalized tables with
proper types, FKs, defaults, CHECK-style enums, and indexes:

| Modern table | Replaces | Key improvements |
|---|---|---|
| `borrowers` | `CDW_BORR_MSTR` | `BIGINT` PK + `external_id` unique key; `DATE`/`TIMESTAMP`/`INTEGER`/`DECIMAL` types; expanded `status` |
| `loan_products` | `CDW_LN_PROD` | `BIGINT` PK + unique `code`; `term_months INTEGER`; `is_active BOOLEAN`; `DATE` effective/expiration |
| `loan_accounts` | `CDW_LN_ACCT` | **Normalized** — `borrower_id`/`product_id` FKs replace embedded borrower data; typed amounts/rates/dates; expanded `status`, `property_type` |
| `payments` | `CDW_PMT_HIST` | `BIGINT` PK; `loan_account_id` FK; typed amounts/dates; expanded `type`/`status` |

Improvements over legacy: proper data types, clear names, normalization (no
duplicated borrower data), foreign-key integrity, indexing, timestamps instead of
string dates, and enum-like status fields.

**Important: the modern schema currently exists only as a `.sql` documentation
file.** There are no modern JPA entities, no modern repositories, no
`schema-modern.sql`/`data-modern.sql` resources wired into the app, and no modern
datasource configured. All of that is still to be built.

## 6. Migration Requirements

Derived from `docs/MIGRATION_TASKS.md` and `data/mappings/column_mappings.md`.

**Functional requirements**
- The REST API contract must remain **identical** — same endpoints, same JSON
  response shapes (`BorrowerDto`, `LoanSummaryDto`, `PaymentDto`).
- Service layer must read from modern tables instead of legacy, and the
  string→type parsing should be eliminated (modern entities are already typed).

**Data transformation rules** (full table in `data/mappings/column_mappings.md`):
1. **Dates**: `MM/DD/YYYY` strings → `DATE`/`TIMESTAMP`.
2. **Amounts**: strip commas from strings → `DECIMAL`.
3. **Status/code expansion**: short codes → readable values
   (e.g. `ACT→ACTIVE`, `CLO→CLOSED`, `DFT→DEFAULT`, `FRB→FORBEARANCE`;
   payment `REG→REGULAR`, status `PST→POSTED`, etc.; `PROD_STAT_CD ACT/INA → is_active true/false`).
4. **Denormalization removal**: drop borrower fields from loan accounts; use
   `borrower_id` FK instead.
5. **ID resolution**: legacy string IDs → modern auto-increment `BIGINT` PKs, with
   FK lookups (`CDW_LN_ACCT.BORR_ID` → `borrowers.id` via `external_id`;
   `PROD_CD` → `loan_products.id` via `code`; `LN_ACCT_NBR` → `loan_accounts.id`
   via `account_number`).
6. **Dropped columns**: `BORR_REC_TYP`, denormalized borrower fields on loans.

**Data volume / validation targets**: 5 borrowers, 5 products, 5 loan accounts,
10 payments must all migrate with correct FKs; row counts and converted amounts
must reconcile against legacy.

**Behavioral nuance to preserve**: note that the legacy code-expansion in
`LoanService` produces **title-case display strings** (e.g. `"Active"`,
`"Single Family Residence"`, `"Posted"`), while the column-mapping doc specifies
**upper-case canonical values** for stored modern columns (e.g. `ACTIVE`,
`POSTED`). Whichever representation is chosen, the API output must either stay
byte-identical to today or the difference must be explicitly documented (Task 4
requires golden-file comparison). This is the single biggest correctness risk.

## 7. Open Tasks That Need Completion

Nothing in the migration has been implemented yet — the repo is at the
"legacy-only" starting state. Outstanding work, per `docs/MIGRATION_TASKS.md`:

- **Task 1 — Modern schema + entities (NOT STARTED)**
  - Create JPA entities `Borrower`, `LoanProduct`, `LoanAccount`, `Payment` with
    proper types (`LocalDate`, `BigDecimal`, `Long`, `Boolean`).
  - Add `@ManyToOne`/`@OneToMany` relationships and Spring Data repositories.
  - Provide a `schema-modern.sql` (and wire a modern datasource / second
    persistence config) so the modern tables actually exist at runtime.
- **Task 2 — Data migration script (NOT STARTED)**
  - Build a migration service/runner that reads legacy rows, transforms types,
    resolves FKs, and inserts into modern tables; handle nulls, malformed data,
    duplicates.
- **Task 3 — Rewire application (NOT STARTED)**
  - Point `LoanService` at modern repositories; remove string-parsing; keep DTOs
    and REST contract identical; optionally retire legacy entities/repos.
- **Task 4 — Validation tests (NOT STARTED)**
  - Capture golden API responses pre-migration, switch sources, diff responses;
    document intentional differences. (Current test suite is only
    `contextLoads()`.)
- **Task 5 — Migration documentation (NOT STARTED)**
  - `DATA_SOURCE_MIGRATION_NOTES.md`, update `application.properties` to point at
    the modern schema, deprecate/remove legacy entities.

**Bonus / optional**
- Dual-read feature flag to switch sources at runtime.
- SQL reconciliation queries (legacy vs. modern).
- Performance comparison (VARCHAR-everything vs. typed schema).

**Other gaps observed**
- README endpoint list (`GET /api/payments/loan/{loanId}`) does not match the
  implemented route (`GET /api/loans/{loanId}/payments`). Reconcile during Task 3/5.
- No `schema-modern.sql` / `data-modern.sql` resources exist yet.
- No CI configuration or pre-commit hooks present in the repo.

## 8. Suggested Implementation Plan

A safe, incremental sequence that keeps the app running at every step:

1. **Establish a baseline (golden files) first.**
   Before touching anything, run the app and capture current JSON for all
   endpoints (`/api/loans`, `/api/loans/{id}`, `/api/borrowers`,
   `/api/borrowers/{id}`, `/api/loans/{loanId}/payments`). These become the
   regression oracle for Task 4. Add an integration test that hits the endpoints
   so behavior is locked in.

2. **Add the modern schema as a second datasource (Task 1).**
   - Create `schema-modern.sql` from `data/modern-schema/modern_tables.sql`
     (H2-compatible) and an empty/typed seed.
   - Configure a second H2 datasource (e.g. `jdbc:h2:mem:moderndw`) with its own
     `@Configuration`, `EntityManagerFactory`, and `@EnableJpaRepositories`
     scoped to a `entity.modern` / `repository.modern` package. Keep the legacy
     datasource as primary for now.
   - Create modern entities (`Borrower`, `LoanProduct`, `LoanAccount`, `Payment`)
     with `LocalDate`/`BigDecimal`/`Long`/`Boolean` and JPA relationships, plus
     their repositories.

3. **Write and run the migration (Task 2).**
   - Implement a `MigrationService` (invokable via `CommandLineRunner` or a
     dedicated endpoint/flag) that reads all legacy rows, applies the
     transformations in `column_mappings.md`, resolves FKs in dependency order
     (borrowers → products → loan_accounts → payments), and persists to modern
     tables.
   - Add reconciliation: assert row counts (5/5/5/10) and summed amounts match.

4. **Rewire the service layer behind a flag (Task 3 + dual-read bonus).**
   - Introduce a feature flag (e.g. `loanservice.datasource=legacy|modern`) so
     `LoanService` can resolve either the legacy or modern repositories. This
     enables side-by-side validation and a safe rollback.
   - Add modern translation methods that build the same DTOs from typed modern
     entities — paying special attention to the **status/type display-string
     formatting** so output matches the golden files (or document the diff).

5. **Validate (Task 4).**
   - Run the golden-file comparison in both modes; resolve or document every diff.
   - Once green, flip the default flag to `modern` and make the modern datasource
     primary in `application.properties`.

6. **Clean up and document (Task 5).**
   - Write `DATA_SOURCE_MIGRATION_NOTES.md` (decisions, mappings, known diffs).
   - Mark legacy entities/repositories `@Deprecated` (or remove once confident).
   - Fix the README endpoint discrepancy.
   - Optionally add reconciliation SQL and a perf comparison (bonus).

**Sequencing rationale**: capturing golden files first de-risks the whole effort;
running legacy and modern side-by-side (dual-read) lets each step be verified
against a known-good baseline before the legacy path is removed.
