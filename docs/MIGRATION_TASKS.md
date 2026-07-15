# Data Source Migration Tasks

This document records the workshop tasks used to migrate the loan-service
application from its legacy data source to the modern schema.

> Status: completed. The modern schema is now the default runtime, all required
> migration and parity tests pass, and implementation details are documented in
> [`DATA_SOURCE_MIGRATION_NOTES.md`](../DATA_SOURCE_MIGRATION_NOTES.md).

## Overview

The original loan-service app read from legacy CDW (Corporate Data Warehouse)
tables with cryptic column names, all-VARCHAR typing, denormalized structures,
and string-encoded dates/amounts. The completed migration uses a clean,
normalized modern schema while keeping the API endpoints functioning
identically.

## Task 1: Create Modern Schema and Entities

**Objective:** Create JPA entities for the modern schema and configure a second data source.

**Steps:**
1. Review the modern schema in `data/modern-schema/modern_tables.sql`
2. Create new JPA entity classes for `borrowers`, `loan_products`, `loan_accounts`, `payments`
3. Use proper Java types: `LocalDate`, `BigDecimal`, `Long`, `Boolean` — not strings
4. Add proper JPA relationships (`@ManyToOne`, `@OneToMany`)
5. Create Spring Data repositories for the modern entities

**Success Criteria:**
- Modern entities use proper types (no string-for-everything)
- Foreign key relationships are modeled with JPA annotations
- Repositories compile and are wired correctly

## Task 2: Write Data Migration Script

**Objective:** Create a migration utility that reads from legacy tables and writes to modern tables.

**Steps:**
1. Review the column mappings in `data/mappings/column_mappings.md`
2. Write a migration service/script that:
   - Reads all legacy records
   - Transforms data types (parse dates, amounts, expand codes)
   - Resolves foreign keys (borrower IDs → modern borrower PKs)
   - Inserts into modern tables
3. Handle edge cases: null values, malformed data, duplicate detection

**Success Criteria:**
- All 5 borrowers migrated with proper types
- All 5 loan products migrated
- All 5 loan accounts migrated with correct FK references
- All 10 payments migrated with correct FK references
- Validation: row counts match, amounts match after type conversion

## Task 3: Rewire Application to Modern Schema

**Objective:** Update the service layer to read from modern tables instead of legacy tables.

**Steps:**
1. Update `LoanService.java` to use modern repositories instead of legacy repositories
2. Simplify the translation methods — modern entities already have proper types
3. Update DTOs if needed (most should work as-is since DTOs are already clean)
4. Keep the REST API contract identical (same endpoints, same response shapes)

**Success Criteria:**
- All API endpoints return identical JSON responses
- No more string-to-type parsing in the service layer
- Legacy entities and repositories can be removed (or kept for reference)

## Task 4: Add Validation Tests

**Objective:** Prove that the modern data source produces identical API results.

**Steps:**
1. Capture the current API responses as golden files (before migration)
2. Switch to the modern data source
3. Run the same API calls and compare responses
4. Document any intentional differences (e.g., date format changes)

**Success Criteria:**
- Golden file comparison passes for all endpoints
- Any differences are documented and justified
- Both data sources produce the same business-meaningful results

## Task 5: Document the Migration

**Objective:** Create migration documentation.

**Deliverables:**
- `DATA_SOURCE_MIGRATION_NOTES.md` — decisions made, patterns used
- Updated `application.properties` pointing to modern schema
- Cleanup: remove or flag legacy entities as deprecated

## Bonus Tasks

- **Dual-read mode:** Implement a feature flag that can switch between legacy and modern data sources at runtime
- **Data validation queries:** Write SQL queries that compare legacy vs. modern data for reconciliation
- **Performance comparison:** Benchmark query performance between legacy VARCHAR-everything schema and properly-typed modern schema
