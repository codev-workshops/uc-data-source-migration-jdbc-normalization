# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application that manages loan data: borrowers, loan
products, loan accounts, and payment history. It originally read from a **legacy data
warehouse** schema (denormalized, cryptic column names, everything a `VARCHAR`) and now reads
from the **modern normalized schema**, with the REST API unchanged.

See [DATA_SOURCE_MIGRATION_NOTES.md](DATA_SOURCE_MIGRATION_NOTES.md) for the mapping decisions.

## Architecture

```
┌─────────────────────────────┐
│   Loan Service (Spring Boot)│
│                             │
│  Controllers ─► Services    │
│                  │          │
│              Repositories   │
│                  │          │
│         Modern DataSource   │
│         (H2 / modern schema)│
└─────────────────────────────┘
```

## Data source

The application reads from the normalized tables, created and seeded at startup from
`src/main/resources/schema-modern.sql` and `data-modern.sql` into `jdbc:h2:mem:loandb`:

- `borrowers` — borrower records, keyed publicly by `external_id` (`B-10001`)
- `loan_products` — product catalog, keyed publicly by `code`
- `loan_accounts` — loan accounts with `borrower_id` / `product_id` foreign keys
- `payments` — payment records with a `loan_account_id` foreign key

The JPA entities (`Borrower`, `LoanProduct`, `LoanAccount`, `Payment`) map these tables with
typed fields and `@ManyToOne`/`@OneToMany` relationships; `LoanService` reads borrower and
product data through those relationships instead of the denormalized columns the legacy loan
account used to carry.

The legacy schema is retained for reference only, in `data/legacy-schema/` (DDL plus the
original seed data) and `data/mappings/` (column-level mappings). No `CDW_*` table is created
or queried at runtime.

## Quick Start

```bash
./mvnw spring-boot:run
```

The app runs on `http://localhost:8080` with endpoints:
- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Get loan details
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Get borrower with loans
- `GET /api/payments/loan/{loanId}` — Payment history for a loan
- `GET /api/loans/{loanId}/payments` — Payment history for a loan (equivalent, nested form)

The H2 console is available at `http://localhost:8080/h2-console` (JDBC URL
`jdbc:h2:mem:loandb`, user `sa`, no password).

## Tests

```bash
./mvnw test      # unit + integration tests
./mvnw verify    # full build
```

The suite runs against the real modern H2 schema, not mocks:

- `ApiCompatibilityTest` — replays every endpoint and diffs it against responses captured from
  the legacy implementation (`src/test/resources/golden/`)
- `ModernSchemaPersistenceTest` — asserts the modern tables and their data, and that no
  `CDW_*` table exists at runtime
- `LoanServiceTest` — service behaviour over the normalized relationships

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- H2 (in-memory)
- Maven

## License

MIT
