# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application migrated from a legacy data
warehouse schema to a normalized, typed H2 schema while preserving the
original REST API.

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and
payment history. The default runtime now uses clear modern table and column
names, proper Java and SQL types, and normalized relationships.

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
│         (H2 / typed schema) │
└─────────────────────────────┘
```

## Legacy Migration Source

The retained migration path reads:

- `CDW_BORR_MSTR` — borrower master
- `CDW_LN_PROD` — loan products
- `CDW_LN_ACCT` — denormalized loan accounts
- `CDW_PMT_HIST` — payment history

See `data/legacy-schema/` for the source DDL and `data/mappings/` for
column-level transformations. Legacy components are enabled only by migration
profiles and tests.

## Current State (Modern)

The default application reads:

- `borrowers` — typed borrower records
- `loan_products` — product catalog
- `loan_accounts` — normalized accounts with borrower and product foreign keys
- `payments` — typed payments with preserved external payment IDs

See `data/modern-schema/` for the target DDL.

## Quick Start

Requirements:

- Java 17
- No system Maven installation is required

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The default application initializes `schema-modern.sql` and `data-modern.sql`
in an in-memory H2 database and runs on `http://localhost:8080`.

## API

- `GET /api/loans` — list all loans
- `GET /api/loans/{id}` — get a loan by account number
- `GET /api/loans/{loanId}/payments` — get payment history for a loan
- `GET /api/borrowers` — list borrowers
- `GET /api/borrowers/{id}` — get a borrower and their loans

External borrower IDs (`B-*`), loan account numbers (`LN-*`), and payment IDs
(`PMT-*`) are preserved in API responses.

## Run the Legacy Migration

The `legacy-migration-run` profile initializes the migration runner. Supply
both schemas and the legacy seed data so the runner can read the source tables
and write the modern target tables:

```bash
./mvnw package
java -jar target/loan-service-1.0.0.jar \
  --spring.profiles.active=legacy-migration-run \
  --spring.sql.init.schema-locations=classpath:schema-legacy.sql,classpath:schema-modern.sql \
  --spring.sql.init.data-locations=classpath:data-legacy.sql
```

The migration:

- writes borrowers, products, loans, then payments in one transaction;
- preserves source business identifiers;
- treats a fully reconciled rerun as a no-op;
- rejects partial or conflicting target data before writing;
- rolls back all writes when a transformation or foreign-key validation fails.

See [DATA_SOURCE_MIGRATION_NOTES.md](DATA_SOURCE_MIGRATION_NOTES.md) for
transformation rules, retained legacy artifacts, and verification evidence.

## Tests

macOS/Linux:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

The suite covers typed persistence, migration reconciliation and rollback,
the runner profile, modern-default startup, and API parity against the captured
legacy responses.

## Tech Stack

- Java 17
- Spring Boot 3.2.3
- Spring Data JPA
- H2 2.2.224
- Maven Wrapper 3.9.6

## License

MIT
