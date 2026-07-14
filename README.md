# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application demonstrating migration from a
**legacy data warehouse** to a normalized **modern schema** while preserving
API behavior.

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and
payment history. It reads the modern schema by default and retains the legacy
CDW read path behind `datasource.mode=legacy` for rollback during rollout.

## Architecture

```
┌─────────────────────────────┐
│   Loan Service (Spring Boot)│
│                             │
│  Controllers ─► Services    │
│                  │          │
│              Repositories   │
│                  │          │
│         Legacy DataSource   │  ← YOU ARE HERE
│         (H2 / legacy schema)│
│                             │
│         Modern DataSource   │  ← MIGRATE TO HERE
│         (H2 / modern schema)│
└─────────────────────────────┘
```

## Current State (Legacy)

The app connects to legacy tables:
- `CDW_BORR_MSTR` — Borrower master (denormalized, cryptic columns)
- `CDW_LN_PROD` — Loan products
- `CDW_LN_ACCT` — Loan accounts (wide table with embedded borrower data)
- `CDW_PMT_HIST` — Payment history

See `data/legacy-schema/` for full DDL and `data/mappings/` for column-level mappings.

## Target State (Modern)

Migrate to normalized tables:
- `borrowers` — Clean borrower records
- `loan_products` — Product catalog
- `loan_accounts` — Normalized loan accounts with foreign keys
- `payments` — Payment records

See `data/modern-schema/` for target DDL.

See `DATA_SOURCE_MIGRATION_NOTES.md` for migration behavior, validation, and
the production rollout plan.

## Quick Start

```bash
./mvnw spring-boot:run
```

The app runs on `http://localhost:8080` with endpoints:
- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Get loan details
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Get borrower with loans
- `GET /api/loans/{loanId}/payments` - Payment history for a loan

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- H2 (in-memory, simulating legacy DW)
- Maven

## License

MIT
