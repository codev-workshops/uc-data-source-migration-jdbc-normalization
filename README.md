# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application. It originally read from a **legacy data warehouse** (simulated via H2 with legacy-style schemas) and now serves the **modern normalized schema**; the legacy tables remain only as the migration source. See [DATA_SOURCE_MIGRATION_NOTES.md](DATA_SOURCE_MIGRATION_NOTES.md).

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and payment history. The legacy tables use denormalized structures, cryptic column names and all-VARCHAR typing; the modern tables are normalized with proper types and foreign keys, and the REST contract is identical across both.

## Architecture

```
┌─────────────────────────────┐
│   Loan Service (Spring Boot)│
│                             │
│  Controllers ─► Services    │
│                  │          │
│              Repositories   │
│                  │          │
│         Legacy tables       │  → migration source only
│         (H2 / legacy schema)│
│                             │
│         Modern tables       │  ← SERVED HERE
│         (H2 / modern schema)│
└─────────────────────────────┘
```

## Migration Source (Legacy)

`DataMigrationService` reads these legacy tables at startup and writes the modern tables:
- `CDW_BORR_MSTR` — Borrower master (denormalized, cryptic columns)
- `CDW_LN_PROD` — Loan products
- `CDW_LN_ACCT` — Loan accounts (wide table with embedded borrower data)
- `CDW_PMT_HIST` — Payment history

See `data/legacy-schema/` for full DDL and `data/mappings/` for column-level mappings.

## Current State (Modern)

The service layer reads these normalized tables:
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

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- H2 (in-memory, simulating legacy DW)
- Maven

## License

MIT
