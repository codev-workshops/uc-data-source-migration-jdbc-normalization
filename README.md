# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application migrated from a **legacy data warehouse** (simulated via H2 with legacy-style schemas) onto a **modern normalized schema**, with the API contract preserved.

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and payment history. The legacy tables have denormalized structures, cryptic column names, and everything stored as `VARCHAR`; the modern schema is normalized and properly typed. Both data sources run side by side: the migration copies legacy rows into the modern schema, and reconciliation verifies the two agree.

## Architecture

```
┌─────────────────────────────┐
│   Loan Service (Spring Boot)│
│                             │
│  Controllers ─► LoanService │
│                  │          │
│           LoanDataProvider  │
│            ├── legacy ──────┼─► Legacy DataSource (H2, CDW_* tables)
│            └── modern ──────┼─► Modern DataSource (H2, normalized)
│                             │
│  MigrationService: legacy ──┼─► modern
│  ReconciliationService: legacy vs modern
└─────────────────────────────┘
```

Reads are served from the modern data source by default. Set
`loanservice.datasource.mode=legacy` to fall back to the CDW tables; the API
responses are identical either way. See
[DATA_SOURCE_MIGRATION_NOTES.md](DATA_SOURCE_MIGRATION_NOTES.md).

## Source of Truth (Legacy)

The legacy data source holds the CDW tables the data is migrated from:
- `CDW_BORR_MSTR` — Borrower master (denormalized, cryptic columns)
- `CDW_LN_PROD` — Loan products
- `CDW_LN_ACCT` — Loan accounts (wide table with embedded borrower data)
- `CDW_PMT_HIST` — Payment history

See `data/legacy-schema/` for full DDL and `data/mappings/` for column-level mappings.

## Target State (Modern)

The modern data source holds the normalized tables the application reads:
- `borrowers` — Clean borrower records
- `loan_products` — Product catalog
- `loan_accounts` — Normalized loan accounts with foreign keys
- `payments` — Payment records

See `data/modern-schema/` for target DDL.

## Quick Start

```bash
./mvnw spring-boot:run
```

The app runs on `http://localhost:8080` with endpoints:
- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Get loan details
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Get borrower with loans
- `GET /api/loans/{loanId}/payments` — Payment history for a loan
- `GET /api/admin/reconciliation` — Legacy vs modern comparison (operational)

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- H2 (in-memory, simulating legacy DW)
- Maven

## License

MIT
