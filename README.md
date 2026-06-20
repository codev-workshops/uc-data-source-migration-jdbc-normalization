# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application that has been migrated from a **legacy data warehouse** schema (simulated via H2) to a **modern normalized schema**, while keeping the REST API contract byte-for-byte identical.

The migration is complete: the app now serves reads from the modern schema by default, with a runtime feature flag to fall back to the legacy schema. See [`DATA_SOURCE_MIGRATION_NOTES.md`](DATA_SOURCE_MIGRATION_NOTES.md) for the design, decisions, and reconciliation procedure.

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and payment history. It originally read from legacy tables with denormalized structures, cryptic column names, and string-typed values. It has been rewired to a normalized modern schema with clear naming conventions and proper types, with the legacy schema retained as the migration source and a reversible read-time fallback.

## Architecture

```
┌─────────────────────────────┐
│   Loan Service (Spring Boot)│
│                             │
│  Controllers ─► Services    │
│                  │          │
│              Repositories   │
│                  │          │
│   LoanService (facade)      │
│      │ dual-read flag       │
│      ├► ModernLoanDataProvider ─► Modern DataSource (PRIMARY, H2 moderndb)
│      └► LegacyLoanDataProvider ─► Legacy DataSource (H2 legacydw)
│                             │
│   MigrationRunner ─► DataMigrationService (legacy ─► modern on startup)
└─────────────────────────────┘
```

Both data sources are configured programmatically (`config/` package), each with
its own `EntityManagerFactory` and transaction manager. On startup, an idempotent
transactional migration copies the legacy CDW data into the modern schema.

## Legacy schema (migration source / fallback)

The legacy CDW tables:
- `CDW_BORR_MSTR` — Borrower master (denormalized, cryptic columns)
- `CDW_LN_PROD` — Loan products
- `CDW_LN_ACCT` — Loan accounts (wide table with embedded borrower data)
- `CDW_PMT_HIST` — Payment history

See `data/legacy-schema/` for full DDL and `data/mappings/` for column-level mappings.

## Modern schema (active)

Normalized tables the app now serves from:
- `borrowers` — Clean borrower records
- `loan_products` — Product catalog
- `loan_accounts` — Normalized loan accounts with foreign keys
- `payments` — Payment records

See `data/modern-schema/` for target DDL.

## Quick Start

```bash
# No Maven wrapper is committed; use a local Maven (3.9.x) install:
mvn spring-boot:run
```

The app runs on `http://localhost:8080` with endpoints:
- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Get loan details
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Get borrower with loans
- `GET /api/loans/{loanId}/payments` — Payment history for a loan

Dual-read feature flag (admin):
- `GET /api/admin/datasource-mode` — report the active mode (`MODERN`/`LEGACY`)
- `PUT /api/admin/datasource-mode/{MODERN|LEGACY}` — switch at runtime

The default mode is set by `loanservice.datasource.mode` in
`application.properties` (defaults to `MODERN`).

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- H2 (in-memory, simulating legacy DW)
- Maven

## License

MIT
