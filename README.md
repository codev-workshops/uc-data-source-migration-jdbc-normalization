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
| `loan.migration.fail-on-error` | `false` | Abort startup if the migration fails or reconciliation finds a mismatch |

Migration status is exposed at `/actuator/health/migration` and included in `/actuator/health/readiness`; it is `DOWN` if the migration failed or reconciliation found a mismatch.

See [DATA_SOURCE_MIGRATION_NOTES.md](DATA_SOURCE_MIGRATION_NOTES.md) for decisions and intentional differences.

## Quick Start

```bash
./mvnw spring-boot:run
```

The default configuration is production-safe: SQL logging and the H2 console are off, and health details are hidden.
For local demos and debugging, run with the `local` profile, which turns on SQL logging, the H2 console at `/h2-console`
and full `/actuator/health` details (migration report, reconciliation mismatches):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The app runs on `http://localhost:8080` with endpoints:
- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Get loan details
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Get borrower with loans
- `GET /api/loans/{loanId}/payments` — Payment history for a loan

An unknown loan account number or borrower ID returns `404 Not Found` with a JSON body (`timestamp`, `status`, `error`, `message`, `path`, `requestId`).
Every error uses that shape: `400` for bad parameters, `404` for unknown IDs or routes, `405` for unsupported methods, and `500`
with a generic message for unexpected failures (stack traces are only logged).

## Observability

- **Request IDs:** every response has an `X-Request-Id` header (the caller's value if it is a safe token of up to 64 characters, otherwise a new UUID). The same id is in error bodies and in every log line of that request (`[requestId=...]`).
- **Logs:** startup logs the datasource mode, the migration time and row counts per table, and every reconciliation check. Each API request is logged with its status and duration.
- **Actuator:** `/actuator/health` (with `migration` and `readiness`), `/actuator/info` (build version, Java, datasource mode) and `/actuator/metrics`.
- **Migration metrics:** `loan.migration.rows` (tags `table`, `outcome` = `read`/`inserted`/`skipped_existing`/`rejected`/`duplicate`), `loan.migration.duration` (tag `outcome` = `success`/`failure`), and the gauges `loan.migration.reconciliation.checks` and `loan.migration.reconciliation.mismatches`.

## Tech Stack

- Java 17
- Spring Boot 3.5
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
