# Loan Service

A Spring Boot loan management application with a normalized relational schema, proper data types, and JPA relationships.

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and payment history. It uses a clean, normalized schema with proper types (DATE, DECIMAL, INTEGER, BOOLEAN), foreign key constraints, and indexed columns.

## Architecture

```
┌─────────────────────────────┐
│   Loan Service (Spring Boot)│
│                             │
│  Controllers ─► LoanService │
│                    │        │
│              Repositories   │
│                    │        │
│           H2 (modern schema)│
│           4 normalized tables│
└─────────────────────────────┘
```

## Database Schema

The application uses 4 normalized tables with proper foreign key relationships:

```
borrowers (1) ──── (N) loan_accounts (1) ──── (N) payments
                            │
                       (N)  │  (1)
                       loan_products
```

- **borrowers** — Borrower records with proper DATE, INTEGER, DECIMAL types
- **loan_products** — Product catalog with BOOLEAN active flag, INTEGER term
- **loan_accounts** — Loan accounts with FK to borrowers and loan_products
- **payments** — Payment records with FK to loan_accounts

See `data/modern-schema/modern_tables.sql` for full DDL.

## API Endpoints

The app runs on `http://localhost:8080` with endpoints:
- `GET /api/loans` — List all loans
- `GET /api/loans/{accountNumber}` — Get loan details
- `GET /api/loans/{accountNumber}/payments` — Payment history for a loan
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{externalId}` — Get borrower with loans

## Quick Start

```bash
./mvnw spring-boot:run
```

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- H2 (in-memory)
- Maven

## Migration History

This application was migrated from a legacy CDW (Corporate Data Warehouse) schema. See `docs/MIGRATION_NOTES.md` for details on the migration process and `data/mappings/column_mappings.md` for the column-level mapping reference.

## License

MIT
