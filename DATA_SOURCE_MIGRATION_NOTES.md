# Data Source Migration Notes

## Overview

This document describes the migration from the legacy Core Data Warehouse (CDW) schema to a normalized modern relational schema for the Loan Service application.

## Schema Changes

### Legacy Tables → Modern Tables

| Legacy Table | Modern Table | Key Changes |
|---|---|---|
| `CDW_BORR_MSTR` | `borrowers` | Proper types (DATE, INTEGER, DECIMAL), clear column names |
| `CDW_LN_PROD` | `loan_products` | Boolean `is_active`, typed amounts |
| `CDW_LN_ACCT` | `loan_accounts` | Normalized — removed denormalized borrower fields, added FK references |
| `CDW_PMT_HIST` | `payments` | Typed amounts/dates, FK to loan_accounts |

### Key Normalization Decisions

1. **Denormalized borrower data removed from loan accounts**: Legacy `CDW_LN_ACCT` contained `BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4` — these are now accessed via the `borrower_id` FK relationship.

2. **Foreign key references**: `loan_accounts.borrower_id` and `loan_accounts.product_id` use auto-generated `BIGINT` IDs instead of string codes.

3. **Legacy string IDs preserved as `external_id`**: The `borrowers.external_id` field retains the legacy `BORR_ID` values (e.g., `B-10001`) for API backward compatibility.

## Data Type Transformations

| Field Type | Legacy Format | Modern Format |
|---|---|---|
| Dates | `"03/15/1978"` (VARCHAR) | `DATE '1978-03-15'` (DATE) |
| Amounts | `"92,500"` (VARCHAR) | `92500.00` (DECIMAL) |
| Status codes | `ACT`, `INA` | `ACTIVE`, `INACTIVE` |
| Property types | `SFR`, `CND`, `TWN`, `MFR` | `Single Family`, `Condominium`, `Townhouse`, `Multi-Family` |
| Payment types | `REG`, `EXT`, `PRT`, `PRE` | `REGULAR`, `EXTRA`, `PARTIAL`, `PREPAYMENT` |
| Payment statuses | `PST`, `REV`, `NSF`, `PND` | `POSTED`, `REVERSED`, `NSF`, `PENDING` |
| Credit score | `"745"` (VARCHAR) | `745` (INTEGER) |
| Boolean flags | `"ACT"` (VARCHAR) | `TRUE` (BOOLEAN) |

## API Impact

### Breaking Changes

- `originationDate` format changed from `MM/DD/YYYY` to `YYYY-MM-DD` (ISO 8601)
- `paymentDate` format changed from `MM/DD/YYYY` to `YYYY-MM-DD` (ISO 8601)
- `paymentId` changed from legacy sequence string (e.g., `PMT-2025120001`) to auto-generated numeric ID
- Status field values changed from title case (`Active`) to uppercase (`ACTIVE`)
- Property type values changed (e.g., `Single Family Residence` to `Single Family`)
- Payment type/status values changed from title case (`Regular`, `Posted`) to uppercase (`REGULAR`, `POSTED`)

### Preserved Behavior

- All REST endpoints remain unchanged (`/api/loans`, `/api/borrowers`, etc.)
- Borrower lookup by legacy ID (`B-10001`) still works via `external_id` field
- Loan lookup by account number (`LN-2019-00142`) still works
- Borrower `fullName` format preserved (first + middle initial + last)
- Property address concatenation format preserved

## Service Layer Simplifications

The following legacy translation helpers were removed as they are no longer needed:

- `parseLegacyAmount()` — amounts are now `BigDecimal` natively
- `parseLegacyDecimal()` — decimals are now typed
- `parseLegacyInteger()` — integers are now typed
- `expandStatusCode()` — statuses stored expanded
- `expandPropertyType()` — property types stored expanded
- `expandPaymentType()` — payment types stored expanded
- `expandPaymentStatus()` — payment statuses stored expanded

## Migration Sequence

Data must be inserted in this order due to foreign key constraints:
1. `borrowers` (no dependencies)
2. `loan_products` (no dependencies)
3. `loan_accounts` (depends on borrowers and loan_products)
4. `payments` (depends on loan_accounts)
