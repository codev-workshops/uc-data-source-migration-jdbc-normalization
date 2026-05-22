# Data Source Migration Notes

## Overview

Migrated the loan-service application from legacy CDW (Core Data Warehouse) tables to a normalized modern schema. The REST API contract remains byte-for-byte identical.

## Migration Order (FK dependency aware)

1. **Borrowers** (no FK dependencies)
2. **Loan Products** (no FK dependencies)
3. **Loan Accounts** (depends on borrowers and loan products)
4. **Payments** (depends on loan accounts)

## Mapping Decisions

### Status Values: Title Case (not UPPERCASE)

The `column_mappings.md` document specifies uppercase expansions (e.g., `ACT -> ACTIVE`), but the existing application code in `LoanService.expandStatusCode()` uses title case (e.g., `ACT -> Active`). Since the API contract is the source of truth, the migration stores **title-case** status values to match the existing API output:

| Entity       | Legacy Code | Modern Value               |
|--------------|-------------|----------------------------|
| Loan Account | ACT         | Active                     |
| Loan Account | CLO         | Closed                     |
| Loan Account | DFT         | Default                    |
| Loan Account | FRB         | Forbearance                |
| Payment Type | REG         | Regular                    |
| Payment Type | EXT         | Extra                      |
| Payment Type | PRT         | Partial                    |
| Payment Type | PRE         | Prepayment                 |
| Payment Stat | PST         | Posted                     |
| Payment Stat | REV         | Reversed                   |
| Payment Stat | NSF         | Non-Sufficient Funds       |
| Payment Stat | PND         | Pending                    |

**Exception:** Borrower status uses uppercase (`ACT -> ACTIVE`, `INA -> INACTIVE`) because this field is not exposed via the API.

### Property Type Expansion

| Legacy Code | Modern Value              |
|-------------|---------------------------|
| SFR         | Single Family Residence   |
| CND         | Condominium               |
| MFR         | Multi-Family Residence    |
| TWN         | Townhouse                 |

## Null / Blank String Handling

- Null or blank legacy strings for optional fields are stored as `null` in the modern schema.
- Null or blank amounts default to `BigDecimal.ZERO`.
- Null or blank integers remain `null`.

## Date Format

- Legacy dates stored as `MM/DD/YYYY` VARCHAR strings.
- Modern schema uses `DATE` and `TIMESTAMP` types.
- Parsing: `DateTimeFormatter.ofPattern("MM/dd/yyyy")`.
- Timestamps created at midnight (`date.atStartOfDay()`).
- DTO output reformats `LocalDate` back to `MM/dd/yyyy` to preserve API contract.

## BigDecimal Scale Preservation

The legacy code creates `BigDecimal` values by parsing raw strings, which preserves the original string's scale:
- `"285,000"` -> `BigDecimal("285000")` (scale 0 -> serializes as `285000`)
- `"271,432.56"` -> `BigDecimal("271432.56")` (scale 2 -> serializes as `271432.56`)
- `"4.750"` -> `BigDecimal("4.750")` (scale 3 -> serializes as `4.750`)

The modern schema stores values in `DECIMAL(n,m)` columns which force a fixed scale. To preserve the exact JSON output:
- Interest rate uses `DECIMAL(5,3)` which naturally preserves scale 3.
- Monetary amounts use `DECIMAL(12,2)` / `DECIMAL(10,2)` which preserves scale 2.
- For `originalAmount` (often whole numbers), `stripTrailingZeros()` is applied during DTO mapping to match the legacy scale-0 output.

## FK Resolution Strategy

- **Borrower resolution:** `LegacyLoanAccount.BORR_ID` -> lookup `borrowerRepository.findByExternalId()` -> get modern `Borrower.id`.
- **Product resolution:** `LegacyLoanAccount.PROD_CD` -> lookup `loanProductRepository.findByCode()` -> get modern `LoanProduct.id`.
- **Loan Account resolution:** `LegacyPayment.LN_ACCT_NBR` -> lookup `loanAccountRepository.findByAccountNumber()` -> get modern `LoanAccount.id`.

## Payment ID Preservation

Legacy payments use `PMT_SEQ_NBR` (e.g., `PMT-2025120001`) as their identifier. The modern schema uses auto-increment `BIGINT` IDs. A `legacy_sequence_number` column was added to the `payments` table to preserve the original payment ID for API compatibility.

## Row Counts

| Table         | Count |
|---------------|-------|
| Borrowers     | 5     |
| Loan Products | 5     |
| Loan Accounts | 5     |
| Payments      | 10    |

## Dropped Fields

- `BORR_REC_TYP` (record type) - not needed in modern schema.
- `BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4` from `CDW_LN_ACCT` - denormalized fields replaced by borrower FK.
