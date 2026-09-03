# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application that has been migrated from a
**legacy data warehouse** (CDW-style, all-VARCHAR, denormalized) to a
**modern normalized schema**, while keeping the REST API byte-for-byte identical.

## Overview

The app manages loan data: borrowers, loan products, loan accounts, and payment
history. At runtime it now reads exclusively from the four normalized tables
`borrowers`, `loan_products`, `loan_accounts`, and `payments` (H2 in-memory).
The legacy `CDW_*` schema is retained only as historical reference under
`data/legacy-schema/` and is never loaded by the application.

## Architecture

```
┌─────────────────────────────────────────┐
│   Loan Service (Spring Boot)            │
│                                         │
│  Controllers ─► LoanService             │
│                    │                    │
│   Borrower / LoanProduct / LoanAccount  │
│   / Payment repositories (Spring Data)  │
│                    │                    │
│      Modern DataSource (H2, loandb)     │
│      schema-modern.sql + data-modern.sql│
└─────────────────────────────────────────┘

data/legacy-schema/   ← historical CDW_* DDL + seed (reference only, not on classpath)
```

## Original source (legacy)

The service originally read four legacy tables (see
`data/legacy-schema/cdw_tables.sql` and `cdw_seed_data.sql`):

| Table | Purpose | Problems |
|---|---|---|
| `CDW_BORR_MSTR` | Borrower master | cryptic names, all VARCHAR |
| `CDW_LN_PROD` | Product catalog | codes, string amounts |
| `CDW_LN_ACCT` | Loan accounts | borrower name **denormalized** onto the loan row; `'285,000'` amounts; `MM/DD/YYYY` strings; `ACT`/`SFR` codes |
| `CDW_PMT_HIST` | Payment history | same typing issues; `REG`/`PST` codes |

`LoanService` compensated in code: stripping commas, parsing strings, expanding
codes, and joining products through an in-memory `Map`.

## Modern schema

`src/main/resources/schema-modern.sql` (derived from
`data/modern-schema/modern_tables.sql`):

- `borrowers` — BIGINT surrogate `id`, `external_id` (legacy `BORR_ID`), typed
  `credit_score INT`, `date_of_birth DATE`, `annual_income DECIMAL`
- `loan_products` — `id`, `code` (legacy `PROD_CD`), `name`, `term_months INT`, …
- `loan_accounts` — `id`, `account_number` (legacy `LN_ACCT_NBR`),
  `borrower_id` FK → `borrowers`, `product_id` FK → `loan_products`,
  `DECIMAL` amounts/rates, `DATE` dates, full-word `status`/`property_type`
- `payments` — `id`, **`external_id`** (legacy `PMT_SEQ_NBR`, added to the
  reference DDL), `loan_account_id` FK → `loan_accounts`, `DATE payment_date`,
  `DECIMAL` amounts, full-word `type`/`status`

`src/main/resources/data-modern.sql` seeds the same 5 borrowers, 5 products,
5 loan accounts and 10 payments as the legacy seed, transformed per
`data/mappings/column_mappings.md`. FKs are resolved by sub-select on the
retained external identifiers.

## Key mapping decisions

| Decision | Why |
|---|---|
| **Denormalization removed via FKs** — `LoanAccount.borrower` / `LoanAccount.product` are `@ManyToOne`; `Borrower.loanAccounts` is `@OneToMany`. `borrowerName` and `productDescription` are built from the related entities, not copied loan columns. | Single source of truth; no in-memory product map. |
| **Typed columns** — `BigDecimal`, `LocalDate`, `Integer`, `Boolean` in entities; `spring.jpa.hibernate.ddl-auto=validate` checks entities against the DDL. | Removes all `parseLegacy*` string-parsing helpers. |
| **Dates formatted back to `MM/dd/yyyy`** in `LoanService` | DTOs expose the date as a string; the JSON must not change. |
| **Full-word DB values mapped to legacy labels** (`ACTIVE`→`Active`, `SINGLE_FAMILY`→`Single Family Residence`, `REGULAR`→`Regular`, `POSTED`→`Posted`, …) | Preserves the exact labels the old `expand*` methods produced. |
| **Legacy external IDs retained** — `borrowers.external_id`, `loan_accounts.account_number`, `payments.external_id` | `BorrowerDto.id` stays `B-10001`, `PaymentDto.paymentId` stays `PMT-2025120001`; surrogate BIGINT ids never leak into the API. |
| Whole-dollar amounts serialize without `.00` (e.g. `285000`) | Matches legacy `BigDecimal` scale in the golden master. |

Full details: `docs/MIGRATION.md`. Legacy discovery: `docs/MIGRATION_ANALYSIS.md`.

## API (unchanged)

- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Loan details (`RuntimeException` → 500 if unknown)
- `GET /api/loans/{loanId}/payments` — Payment history, newest first (`[]` if unknown)
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Borrower with loans (`RuntimeException` → 500 if unknown)

The exact pre-migration JSON for every endpoint is captured in
`src/test/resources/golden/` and enforced by `GoldenMasterApiTest`.

## Running

```bash
./mvnw spring-boot:run      # http://localhost:8080, H2 console at /h2-console (jdbc:h2:mem:loandb)
./mvnw test                 # golden-master + modern H2 integration tests
./mvnw verify
```

Tests:
- `GoldenMasterApiTest` — MockMvc, byte-for-byte JSON contract for all endpoints
- `ModernSchemaIntegrationTest` — real H2 modern schema: listing/lookup, FK-based
  borrower/product population, borrower→loan association, payment ordering,
  absence of `CDW_*` tables

## Tech Stack

Java 17 · Spring Boot 3.2 · Spring Data JPA · H2 · Maven

## License

MIT
