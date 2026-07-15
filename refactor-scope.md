# Refactor Scope: Legacy-to-Modern Data Source Migration

## Purpose

This document defines the required scope for migrating the loan service from the legacy CDW-style H2 schema to the normalized modern schema. It is intended to be the primary planning input for a Devin agent implementing the migration.

The migration is complete when the application reads from the modern tables, the legacy seed data has been transformed correctly, and the existing REST API continues to return the same business results.

## Sources of Truth

Use these files when planning and implementing the migration:

1. `README.md` — application purpose, current/target architecture, and public endpoints.
2. `data/modern-schema/modern_tables.sql` — target table definitions, constraints, and indexes.
3. `data/mappings/column_mappings.md` — field mappings and transformation rules.
4. `docs/MIGRATION_TASKS.md` — migration tasks and expected record counts.
5. `src/main/resources/schema-legacy.sql` and `data-legacy.sql` — actual legacy runtime schema and seed data.
6. Existing controllers, DTOs, and current API responses — compatibility baseline.

If documentation and runtime behavior disagree, record the discrepancy before implementation and preserve the current runtime API unless an explicit decision says otherwise.

## Current State

The Spring Boot application uses one in-memory H2 data source initialized with:

- `CDW_BORR_MSTR`
- `CDW_LN_PROD`
- `CDW_LN_ACCT`
- `CDW_PMT_HIST`

The legacy model has cryptic names, string-encoded dates and numbers, denormalized borrower data, abbreviated codes, and no foreign-key constraints. `LoanService` currently performs data parsing, code expansion, joins, and DTO translation in application code.

## Target State

The application must use these normalized tables:

- `borrowers`
- `loan_products`
- `loan_accounts`
- `payments`

The target model must use proper SQL and Java types, explicit relationships, foreign-key constraints, readable names, and the indexes defined by the modern schema. The service layer must query modern repositories and must no longer parse legacy string values.

## Required Migration Scope

### 1. Establish an API Compatibility Baseline

Before changing the data source:

- Run the current application with the legacy schema and seed data.
- Capture deterministic responses, preferably as golden files, for:
  - listing all loans;
  - retrieving each loan by account number;
  - listing all borrowers;
  - retrieving each borrower with associated loans;
  - retrieving payment history for each loan.
- Record response status codes, JSON field names, value formats, ordering where observable, and not-found behavior.
- Resolve the endpoint documentation discrepancy:
  - `README.md` lists `GET /api/payments/loan/{loanId}`;
  - the current controller exposes `GET /api/loans/{loanId}/payments`.
  Do not silently add, remove, or rename a route as part of the data migration.

### 2. Add the Modern Runtime Schema

- Add executable application resources for the modern schema and migrated/seed data rather than relying only on the reference DDL under `data/`.
- Implement the four target tables, foreign keys, unique constraints, defaults, and indexes described in `data/modern-schema/modern_tables.sql`.
- Update Spring/H2 initialization so the final application starts against the modern schema.
- Use `spring.jpa.hibernate.ddl-auto=none`; schema creation must remain explicit and reproducible.
- If both schemas are needed during migration, isolate their configuration clearly. The final default runtime must read from the modern schema.

### 3. Add Modern JPA Entities and Repositories

Create entities and Spring Data repositories for all four modern tables.

Required Java types include:

- `Long` for generated primary keys and foreign-key identities;
- `LocalDate` for date columns;
- an appropriate date-time type for timestamp columns;
- `BigDecimal` for monetary values, balances, rates, percentages, and income;
- `Integer` for terms, scores, and delinquency days;
- `Boolean` for active flags;
- `String` for external identifiers, account numbers, codes, names, and addresses.

Required relationships include:

- `LoanAccount` → `Borrower`;
- `LoanAccount` → `LoanProduct`;
- `Payment` → `LoanAccount`;
- inverse collections only where they improve the existing query paths without creating serialization or loading problems.

Repositories must support lookups required by the existing API, including:

- borrower by `external_id`;
- loans by borrower;
- loan by `account_number`;
- product by `code`;
- payments by loan, ordered consistently with the legacy response.

### 4. Implement Legacy-to-Modern Data Migration

Migrate data in dependency order:

1. borrowers;
2. loan products;
3. loan accounts;
4. payments.

Apply every transformation in `data/mappings/column_mappings.md`, including:

- parsing `MM/DD/YYYY` strings into typed dates/timestamps;
- removing thousands separators and parsing decimal values;
- parsing integer values;
- expanding status, payment type, payment status, and property type codes;
- converting product active/inactive codes to booleans;
- dropping denormalized borrower fields from loan accounts;
- resolving borrower and product foreign keys;
- resolving payment-to-loan foreign keys.

The migration must:

- handle nullable optional fields;
- reject or report malformed required values with enough context to identify the source row;
- avoid duplicate records when rerun or fail clearly before creating inconsistent data;
- validate referenced borrowers, products, and loans before inserting dependent rows;
- run within a transaction or otherwise prevent a partially migrated final state.

Expected seed-data totals are:

| Entity | Expected rows |
|---|---:|
| Borrowers | 5 |
| Loan products | 5 |
| Loan accounts | 5 |
| Payments | 10 |

### 5. Preserve External Identifiers

Modern generated IDs are internal implementation details. Existing API lookups and response identifiers must continue to use stable business identifiers:

- borrower API IDs map to `borrowers.external_id`;
- loan API IDs map to `loan_accounts.account_number`;
- loan product references map through `loan_products.code`.

The current payment response exposes the legacy payment sequence number, while the target `payments` table defines only an auto-generated ID. The implementation must explicitly preserve the legacy payment identifier, for example through a dedicated external/source ID column, if golden responses confirm that it is part of the API contract. Do not silently replace it with a new generated value.

### 6. Rewire the Application to the Modern Model

- Replace legacy repository dependencies in `LoanService` with modern repositories.
- Use entity relationships instead of manually joining maps of legacy codes.
- Remove legacy parsing helpers from the normal read path.
- Keep DTO field names and endpoint signatures stable.
- Keep business-facing values compatible with the baseline, including:
  - borrower full-name formatting;
  - loan product description;
  - status labels and capitalization;
  - property address and property type formatting;
  - payment type and status labels;
  - date and decimal JSON formatting.
- Preserve current not-found behavior unless separately refactored and tested.

DTO internals may be adjusted only when needed to consume typed entities. A Java type improvement must not accidentally change the public JSON contract.

### 7. Add Migration and Regression Validation

Automated tests must cover:

- Spring context startup using the modern schema;
- modern repository lookups and relationships;
- each transformation category;
- null and malformed-value handling;
- duplicate handling or idempotency behavior;
- row-count reconciliation;
- foreign-key integrity;
- representative amount, rate, date, status, and identifier values;
- all documented API endpoints;
- comparison of modern responses against the legacy golden responses.

Any intentional API difference must be documented with its reason and asserted in tests. Otherwise, differences are migration defects.

### 8. Complete Cutover and Cleanup

- Make the modern data source the default application configuration.
- Remove legacy entities, repositories, initialization resources, and translation code when they are no longer required, or clearly mark and isolate them if retained for migration/reference purposes.
- Ensure no production read path queries a `CDW_*` table after cutover.
- Add `DATA_SOURCE_MIGRATION_NOTES.md` describing:
  - migration approach and execution order;
  - transformation and identifier decisions;
  - error and duplicate handling;
  - reconciliation results;
  - intentional API differences, if any;
  - retained legacy components and why.
- Update `README.md` if startup instructions, configuration, or endpoint documentation changes.

## Out of Scope

The following are optional follow-up work and must not be included unless explicitly requested:

- runtime dual-read or feature-flag switching between data sources;
- performance benchmarking;
- a general-purpose ETL platform;
- deployment to an external database or cloud service;
- unrelated API redesign, route renaming, pagination, or error-model changes;
- authentication or authorization changes;
- borrower SSN re-encryption;
- new loan-management business features;
- broad framework or dependency upgrades.

## Recommended Implementation Sequence

1. Capture legacy API golden files and document discrepancies.
2. Add executable modern schema initialization.
3. Add modern entities and repositories.
4. Implement and test transformation/migration logic.
5. Reconcile migrated rows and foreign keys.
6. Rewire the service layer to modern repositories.
7. Run API parity tests and resolve regressions.
8. Switch the default configuration to modern.
9. Remove or isolate obsolete legacy code.
10. Update migration and project documentation.

## Definition of Done

The migration is complete only when all of the following are true:

- The application builds and starts on Java 17 using the Maven wrapper.
- The default runtime initializes and reads the modern schema.
- All four modern entities and repositories use appropriate typed fields and relationships.
- Exactly 5 borrowers, 5 products, 5 loans, and 10 payments are migrated from the supplied seed data.
- Every loan references an existing borrower and product.
- Every payment references an existing loan.
- Parsed dates, decimals, integers, statuses, and identifiers match the mapping specification.
- Existing REST endpoints and business-meaningful JSON responses match the captured legacy baseline.
- Modern API reads contain no dependency on legacy repositories or `CDW_*` tables.
- Automated migration, integrity, and API regression tests pass.
- Migration decisions and any intentional differences are documented.
- Optional bonus work has not expanded the refactor without explicit approval.
