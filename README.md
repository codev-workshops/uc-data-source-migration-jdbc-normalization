# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application that currently connects to a **legacy data warehouse** (simulated via H2 with legacy-style schemas). The workshop challenge is to migrate the data source to a **modern schema** while keeping the application functional.

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and payment history. It currently reads from legacy tables with denormalized structures, cryptic column names, and outdated patterns. The goal is to rewire it to use a normalized modern schema with clear naming conventions.

## Architecture

```
┌─────────────────────────────┐
│   Loan Service (Spring Boot)│
│                             │
│  Controllers ─► Services    │
│                  │          │
│              Repositories   │
│                  │          │
│         Modern DataSource   │  ← serves the API (default)
│         (H2 / modern schema)│
│              ▲              │
│   LegacyToModernMigrator    │  (JDBC, runs on startup)
│              │              │
│         Legacy DataSource   │  ← migration source / rollback
│         (H2 / legacy schema)│
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

## Migration Status

The API is served from the modern schema. On startup the legacy CDW tables are
copied into the modern tables by an idempotent JDBC migration, followed by a
reconciliation of row counts, amount totals and foreign keys.

| Property | Default | Meaning |
|----------|---------|---------|
| `loan.datasource.mode` | `modern` | `modern` or `legacy` (deprecated read path, for rollback) |
| `loan.migration.run-on-startup` | `true` | Run the legacy → modern migration on startup |

Migration status is exposed at `/actuator/health/migration` and included in `/actuator/health/readiness`; it is `DOWN` if the migration failed or reconciliation found a mismatch.

See [DATA_SOURCE_MIGRATION_NOTES.md](DATA_SOURCE_MIGRATION_NOTES.md) for decisions and intentional differences.

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
- H2 (in-memory: `legacydw` simulates the legacy DW, `loandb` holds the modern schema)
- Maven

## Tests

```bash
./mvnw test
```

Golden-file tests compare every endpoint against responses captured from the legacy app
(`src/test/resources/golden`), in both `modern` and `legacy` modes.

## License

MIT
