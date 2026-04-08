# Data Source Migration Notes

## Summary

The loan-service application was migrated from a legacy Corporate Data Warehouse (CDW) schema to a modern normalized schema. The migration was completed in 6 phases with zero downtime and identical API output.

## Legacy Problems Solved

1. **All-VARCHAR typing** — Every column (dates, amounts, integers, booleans) was stored as VARCHAR. Now uses DATE, DECIMAL(12,2), INTEGER, BOOLEAN, TIMESTAMP.
2. **No foreign keys** — Cross-table references were string-based with no referential integrity. Now uses BIGINT FKs with constraints.
3. **Denormalized data** — Borrower name and SSN were duplicated in the loan_accounts table. Now normalized with FK to borrowers table.
4. **Cryptic column names** — `BORR_FST_NM`, `LN_ORIG_AMT`, `PMT_ESCROW_AMT`. Now uses readable names: `first_name`, `original_amount`, `escrow_amount`.
5. **String-encoded dates** — All dates stored as `MM/DD/YYYY` strings. Now uses DATE and TIMESTAMP types.
6. **Comma-formatted amounts** — Values like `"285,000"` and `"1,487.02"` stored as strings. Now uses DECIMAL.
7. **Abbreviated status codes** — `ACT`, `CLO`, `REG`, `PST`. Now stores expanded values: `ACTIVE`, `CLOSED`, `REGULAR`, `POSTED`.
8. **No indexes** — No indexes on any table. Now has 6 indexes on common query columns.
9. **No constraints** — No NOT NULL, CHECK, or UNIQUE constraints. Now has proper constraints.

## Transformation Patterns Applied

1. **Date conversion:** `MM/DD/YYYY` string → `DATE` or `TIMESTAMP`
2. **Amount conversion:** Strip commas, parse to `DECIMAL`
3. **Status expansion:** `ACT`→`ACTIVE`, `CLO`→`CLOSED`, `DFT`→`DEFAULT`, `FRB`→`FORBEARANCE`
4. **Property type expansion:** `SFR`→`Single Family Residence`, `CND`→`Condominium`, `MFR`→`Multi-Family Residence`, `TWN`→`Townhouse`
5. **Payment type expansion:** `REG`→`REGULAR`, `EXT`→`EXTRA`, `PRT`→`PARTIAL`, `PRE`→`PREPAYMENT`
6. **Payment status expansion:** `PST`→`POSTED`, `REV`→`REVERSED`, `NSF`→`NSF`, `PND`→`PENDING`
7. **Denormalization removal:** Dropped `BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4` from loan_accounts; use FK to borrowers instead
8. **ID resolution:** Legacy string IDs (`B-10001`, `FXD30`) → auto-increment BIGINT PKs with FK lookups
9. **Column dropped:** `BORR_REC_TYP` (record type) — not needed in modern schema

## Migration Phases

1. **Phase 1:** Created modern schema SQL, JPA entities with proper types and relationships, and Spring Data repositories
2. **Phase 2:** Built DataMigrationService to read legacy data, transform types, and write to modern tables
3. **Phase 3:** Created ModernLoanService reading from modern repos with JPA relationships — no string parsing
4. **Phase 4:** Added feature flag to toggle between legacy and modern services at the controller level
5. **Phase 5:** Made modern the default, created pre-transformed seed data, deprecated legacy code
6. **Phase 6:** Deleted all legacy code, renamed modern classes to canonical names, updated documentation

## Known API Differences

- **`paymentId`:** Legacy used the CDW sequence number (e.g., `PMT-2025120001`). Modern uses auto-generated BIGINT ID. The field type in the API response (`String`) is unchanged.

## Data Counts

| Table | Records |
|-------|---------|
| borrowers | 5 |
| loan_products | 5 |
| loan_accounts | 5 |
| payments | 10 |
