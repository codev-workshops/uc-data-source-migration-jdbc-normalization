# Data Source Migration Notes

The loan service now reads exclusively from the modern normalized schema. This document
records what moved, the mapping decisions taken, and the guarantees that were kept.

## Before / after

| | Legacy | Modern |
|---|---|---|
| Database | `jdbc:h2:mem:legacydw` | `jdbc:h2:mem:loandb` |
| Init scripts | `schema-legacy.sql`, `data-legacy.sql` (on the classpath) | `schema-modern.sql`, `data-modern.sql` |
| Tables | `CDW_BORR_MSTR`, `CDW_LN_PROD`, `CDW_LN_ACCT`, `CDW_PMT_HIST` | `borrowers`, `loan_products`, `loan_accounts`, `payments` |
| Entities | `LegacyBorrower`, `LegacyLoanProduct`, `LegacyLoanAccount`, `LegacyPayment` (all-`String`) | `Borrower`, `LoanProduct`, `LoanAccount`, `Payment` (typed + FK relationships) |
| Joins | none — borrower/product data was denormalized into `CDW_LN_ACCT` and looked up by code in Java | `@ManyToOne` FKs, fetched with explicit `join fetch` queries |

The legacy DDL and seed data now live in `data/legacy-schema/` as historical reference only;
nothing on the runtime classpath creates or reads a `CDW_*` table.

## Table mapping

- `CDW_BORR_MSTR` → `borrowers`: `BORR_ID` becomes `external_id` (the surrogate `id` is new),
  dates parsed from `MM/DD/YYYY`, `BORR_CRDT_SCR`/`BORR_ANN_INCM` parsed to `INTEGER`/`DECIMAL`,
  `BORR_STAT_CD` expanded (`ACT` → `ACTIVE`), `BORR_REC_TYP` dropped.
- `CDW_LN_PROD` → `loan_products`: `PROD_CD` → `code`, `PROD_DESC_TXT` → `name`,
  `PROD_STAT_CD` → `is_active` boolean.
- `CDW_LN_ACCT` → `loan_accounts`: `LN_ACCT_NBR` → `account_number`; the denormalized
  `BORR_FST_NM`/`BORR_LST_NM`/`BORR_SSN_LST4` columns are gone and resolved through
  `borrower_id`; `PROD_CD` resolved through `product_id`; amounts/rates/dates properly typed;
  `LN_STAT_CD` expanded (`ACT` → `ACTIVE`).
- `CDW_PMT_HIST` → `payments`: `LN_ACCT_NBR` resolved through `loan_account_id`;
  `PMT_TYP_CD`/`PMT_STAT_CD` expanded (`REG` → `REGULAR`, `PST` → `POSTED`).

Full column-level detail lives in `data/mappings/column_mappings.md`.

## Decisions worth knowing

- **`payments.external_id` was added** to the modern DDL. The mapping notes allow keeping the
  legacy `PMT_SEQ_NBR` "if needed", and it is needed: the API's `paymentId` field would
  otherwise change from `PMT-2025120001` to an auto-increment number.
- **Identifiers stay business keys.** `/api/loans/{id}` takes an account number and
  `/api/borrowers/{id}` takes `B-100xx`; the new surrogate `BIGINT` PKs are internal only.
- **`property_type` is stored expanded** (`Single Family Residence`, `Condominium`,
  `Townhouse`) rather than as the abbreviated form suggested in the mapping table, because
  that is the value the API has always returned.
- **Enum-like columns keep the modern uppercase form** (`ACTIVE`, `REGULAR`, `POSTED`) as the
  DDL prescribes; the service still renders them as `Active`, `Regular`, `Posted` for the API.
- **Dates are stored as `DATE`/`TIMESTAMP`** and formatted back to `MM/DD/YYYY` when they hit
  the DTOs, so response strings are unchanged.
- **Numeric scale**: fixed-scale `DECIMAL` columns serialize with their scale, so
  `"originalAmount": 285000` is now `"originalAmount": 285000.00`. Same JSON number, different
  text; this is the only observable difference from the legacy responses.
- **Ordering** is explicit (`order by id`, payments by `payment_date desc`) and reproduces the
  legacy ordering rather than relying on implicit scan order.

## Verification

`src/test/resources/golden/` holds responses captured from the legacy implementation before
the migration; `ApiCompatibilityTest` replays every endpoint against the running application
and compares them. `ModernSchemaPersistenceTest` asserts that the modern tables exist with the
expected data and that no `CDW_*` table is present in the database at runtime.
