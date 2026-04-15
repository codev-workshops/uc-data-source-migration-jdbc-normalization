# Data Source Migration Notes

## Overview

This document records the decisions, patterns, and implementation details from migrating the `loan-service` application from its legacy Corporate Data Warehouse (CDW) schema to a modern, normalized relational schema. The migration was completed while maintaining a stable REST API contract — all existing endpoints continue to return identical JSON responses.

## Migration Sequence

| Task | Description | Key Deliverables |
|------|-------------|-----------------|
| **Task 1** | Create modern JPA entities and repositories | `Borrower`, `LoanProduct`, `LoanAccount`, `Payment` entities with proper Java types; Spring Data repositories with custom query methods |
| **Task 2** | Build ETL migration service | `DataMigrationService` + `DataMigrationRunner` — reads legacy tables, transforms data, writes to modern tables at startup |
| **Task 3** | Rewire application service layer | `LoanService` rewritten to use modern repositories; all legacy parsing helpers removed; legacy classes marked `@Deprecated` |
| **Task 4** | Golden file validation tests | `GoldenFileValidationTest` — integration tests comparing modern API output against legacy baseline JSON snapshots |
| **Task 5** | Documentation and cleanup | This document; final verification of configuration and code hygiene |

## Architecture Decisions

### Single Database, Dual Schema

Both legacy and modern tables coexist in the same H2 in-memory database (`jdbc:h2:mem:legacydw`). This simplifies the migration by avoiding multi-datasource configuration while allowing the ETL service to read from legacy tables and write to modern tables in a single transaction context.

- `schema-legacy.sql` — creates CDW tables (`CDW_BORR_MSTR`, `CDW_LN_ACCT`, etc.)
- `schema-modern.sql` — creates normalized tables (`borrowers`, `loan_accounts`, etc.) using `CREATE TABLE IF NOT EXISTS`
- `data-legacy.sql` — seeds the legacy tables with sample data
- Modern tables are populated by the ETL migration at startup, not by a SQL data file

### ETL at Startup

The `DataMigrationRunner` (a Spring `CommandLineRunner`) triggers `DataMigrationService.migrateAll()` after the application context loads. This ensures:

1. Legacy schema and data are initialized first (via `spring.sql.init`)
2. Modern schema tables exist (created by `schema-modern.sql`)
3. ETL reads from legacy repositories, transforms data, and inserts into modern tables
4. The migration is gated by `migration.enabled=true` in `application.properties`

### Legacy Code Retention

Legacy entity and repository classes are retained with `@Deprecated` annotations because the ETL migration service (`DataMigrationService`) still depends on them to read from CDW tables. They can be removed in a future phase once the migration is fully validated and the legacy schema is no longer needed.

Deprecated classes:
- `LegacyBorrower`, `LegacyLoanAccount`, `LegacyLoanProduct`, `LegacyPayment`
- `LegacyBorrowerRepository`, `LegacyLoanAccountRepository`, `LegacyLoanProductRepository`, `LegacyPaymentRepository`

## Schema Design Patterns

### Normalization

The legacy schema embeds borrower data directly in loan account records (denormalized). The modern schema normalizes this into separate tables with foreign key relationships:

```
Legacy (CDW_LN_ACCT):
  BORR_FST_NM, BORR_LST_NM, BORR_SSN_LST4  ← embedded in every loan

Modern (loan_accounts):
  borrower_id BIGINT REFERENCES borrowers(id)  ← normalized FK
```

JPA `@ManyToOne` relationships on `LoanAccount` provide access to `Borrower` and `LoanProduct` without manual joins.

### Type Upgrades

| Legacy Type | Modern Type | Examples |
|------------|-------------|---------|
| `VARCHAR` (all fields) | `BigDecimal` | `LN_ORIG_AMT "285,000"` → `original_amount DECIMAL(12,2)` |
| `VARCHAR` | `LocalDate` | `LN_ORIG_DT "02/15/2019"` → `origination_date DATE` |
| `VARCHAR` | `Integer` | `BORR_CRDT_SCR "745"` → `credit_score INTEGER` |
| `VARCHAR` | `Boolean` | `PROD_STAT_CD "ACT"` → `is_active BOOLEAN` |

### Foreign Key Resolution

Legacy tables use string-based IDs (`BORR_ID "B-10001"`, `PROD_CD "30FXD"`). Modern tables use auto-increment `BIGINT` primary keys with string identifiers stored as indexed unique columns (`external_id`, `code`). During ETL, the migration service looks up the modern PK by the legacy string ID to set foreign keys.

## Data Transformation Rules

### Date Parsing
- Legacy format: `MM/DD/YYYY` strings (e.g., `"02/15/2019"`)
- Modern type: `LocalDate`
- Parser: `DateTimeFormatter.ofPattern("MM/dd/yyyy")`
- API output: Converted back to `MM/dd/yyyy` string via `LoanService.formatDate()` to maintain JSON parity

### Amount Parsing
- Legacy format: Comma-separated strings (e.g., `"285,000"`, `"1,487.02"`)
- Modern type: `BigDecimal`
- Transformation: Strip commas, parse with `new BigDecimal()`

### Status/Type Code Expansion

The ETL expands 3-letter codes into human-readable title-case strings matching the legacy API output:

| Domain | Code | Expanded Value |
|--------|------|---------------|
| Loan Status | `ACT` | `Active` |
| Loan Status | `CLO` | `Closed` |
| Loan Status | `DFT` | `Default` |
| Loan Status | `FRB` | `Forbearance` |
| Property Type | `SFR` | `Single Family Residence` |
| Property Type | `CND` | `Condominium` |
| Property Type | `MFR` | `Multi-Family Residence` |
| Property Type | `TWN` | `Townhouse` |
| Payment Type | `REG` | `Regular` |
| Payment Type | `EXT` | `Extra` |
| Payment Type | `PRT` | `Partial` |
| Payment Type | `PRE` | `Prepayment` |
| Payment Status | `PST` | `Posted` |
| Payment Status | `REV` | `Reversed` |
| Payment Status | `NSF` | `Non-Sufficient Funds` |
| Payment Status | `PND` | `Pending` |

These values are stored pre-expanded in the modern tables, so `LoanService` can assign them directly without any transformation.

## API Parity Strategy

### Unchanged Layers
- **DTOs** (`LoanSummaryDto`, `BorrowerDto`, `PaymentDto`) — no changes; same JSON contract
- **Controllers** (`LoanController`, `BorrowerController`) — no changes; same endpoints and path variables

### Simplified Service Layer
The rewritten `LoanService` eliminates all legacy parsing methods:

| Removed Method | Reason |
|---------------|--------|
| `parseLegacyAmount(String)` | Modern entities use `BigDecimal` natively |
| `parseLegacyDecimal(String)` | Modern entities use `BigDecimal` natively |
| `parseLegacyInteger(String)` | Modern entities use `Integer` natively |
| `expandStatusCode(String)` | ETL stores pre-expanded values |
| `expandPropertyType(String)` | ETL stores pre-expanded values |
| `expandPaymentType(String)` | ETL stores pre-expanded values |
| `expandPaymentStatus(String)` | ETL stores pre-expanded values |

One new method was added: `formatDate(LocalDate)` — converts `LocalDate` back to `MM/dd/yyyy` strings for DTO compatibility.

### JPA Relationship Simplification
Legacy `LoanService` manually built a `Map<String, LegacyLoanProduct>` for product lookups and joined borrower data from denormalized fields. Modern `LoanService` uses JPA `@ManyToOne` navigation:

```java
// Legacy: manual map lookup
Map<String, LegacyLoanProduct> products = ...;
dto.setProductDescription(products.get(acct.getProductCode()).getDescription());

// Modern: JPA relationship
dto.setProductDescription(acct.getProduct().getName());
```

## Known Differences

### Payment ID Format Change

| Field | Legacy Value | Modern Value |
|-------|-------------|-------------|
| `paymentId` | `"PMT-2025120001"` (legacy sequence string) | `"1"` (auto-increment Long) |

This is the only intentional difference between legacy and modern API output. The legacy `PMT_SEQ_NBR` is a formatted string; the modern `payments.id` is an auto-increment `BIGINT` converted to string via `String.valueOf()`. The golden file validation tests exclude this field from strict comparison.

If downstream consumers depend on the legacy payment ID format, consider adding a `legacy_payment_id` column to the modern `payments` table and populating it during ETL.

## Configuration

Final `application.properties` settings:

```properties
# Schema initialization
spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:schema-legacy.sql,classpath:schema-modern.sql
spring.sql.init.data-locations=classpath:data-legacy.sql

# Hibernate (no auto DDL — schemas managed by SQL scripts)
spring.jpa.hibernate.ddl-auto=none

# ETL migration
migration.enabled=true
```

## Validation Results

Golden file validation tests confirm API parity across all 5 endpoints:

| Endpoint | Comparison Mode | Result |
|----------|----------------|--------|
| `GET /api/loans` | STRICT | Pass |
| `GET /api/loans/{accountNumber}` | STRICT | Pass |
| `GET /api/loans/{accountNumber}/payments` | STRICT (excluding `paymentId`) | Pass |
| `GET /api/borrowers` | STRICT | Pass |
| `GET /api/borrowers/{id}` | STRICT | Pass |

## Migration Statistics

| Entity | Records Migrated |
|--------|-----------------|
| Borrowers | 5 |
| Loan Products | 5 |
| Loan Accounts | 5 |
| Payments | 10 |
