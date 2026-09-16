# Data Source Migration Notes

## Overview

The service previously read the legacy CDW-style warehouse tables (`CDW_BORR_MSTR`,
`CDW_LN_PROD`, `CDW_LN_ACCT`, `CDW_PMT_HIST`) directly. Every column there is a
`VARCHAR`, so the service parsed dates, amounts and codes from strings on every
request.

The runtime data source is now the modern normalized schema (`borrowers`,
`loan_products`, `loan_accounts`, `payments`) with real `DATE`, `TIMESTAMP`,
`DECIMAL`, `INTEGER` and `BOOLEAN` columns and enforced foreign keys.

At startup the application:

1. creates the legacy schema and loads the legacy seed data
   (`schema-legacy.sql`, `data-legacy.sql`),
2. creates the modern schema (`schema-modern.sql`),
3. runs `DataMigrationService` (via `DataMigrationRunner`, an `ApplicationRunner`)
   to transform legacy rows into modern rows and reconcile the counts.

The legacy tables therefore still exist, but only as the migration source. No
request path reads them; `LoanService` depends exclusively on the modern
repositories.

The public API is unchanged: same paths, same DTO field names, same formatting.

## Table and column mapping

### `CDW_BORR_MSTR` → `borrowers`

| Legacy | Modern | Conversion |
| --- | --- | --- |
| `BORR_ID` | `external_id` | kept verbatim; surrogate `id` added |
| `BORR_FST_NM` / `BORR_LST_NM` / `BORR_MID_INIT` | `first_name` / `last_name` / `middle_initial` | verbatim |
| `BORR_SSN_ENCR` | `ssn_hash` | verbatim |
| `BORR_DOB_DT` | `date_of_birth` | `VARCHAR MM/DD/YYYY` → `DATE` |
| `BORR_ADDR_LN1` / `BORR_ADDR_LN2` / `BORR_CTY_NM` / `BORR_ST_CD` / `BORR_ZIP_CD` | `address_line1` / `address_line2` / `city` / `state` / `zip_code` | verbatim |
| `BORR_PH_NBR` / `BORR_EMAIL_ADDR` | `phone` / `email` | verbatim |
| `BORR_CRDT_SCR` | `credit_score` | `VARCHAR` → `INTEGER` |
| `BORR_EMP_STAT` | `employment_status` | verbatim |
| `BORR_ANN_INCM` | `annual_income` | `VARCHAR` with commas → `DECIMAL(12,2)` |
| `BORR_CRET_DT` / `BORR_UPDT_DT` | `created_at` / `updated_at` | `VARCHAR MM/DD/YYYY` → `TIMESTAMP` at start of day |
| `BORR_STAT_CD` | `status` | `ACT`→`ACTIVE`, `INA`→`INACTIVE` |
| `BORR_REC_TYP` | — | dropped (warehouse record-type marker, unused) |

### `CDW_LN_PROD` → `loan_products`

| Legacy | Modern | Conversion |
| --- | --- | --- |
| `PROD_CD` | `code` | natural key kept |
| `PROD_DESC_TXT` | `name` | verbatim (API `productDescription`) |
| `PROD_TYP_CD` / `PROD_RT_TYP` | `type` / `rate_type` | verbatim |
| `PROD_TERM_MOS` | `term_months` | `VARCHAR` → `INTEGER` |
| `PROD_MIN_AMT` / `PROD_MAX_AMT` | `min_amount` / `max_amount` | `VARCHAR` → `DECIMAL(12,2)` |
| `PROD_STAT_CD` | `is_active` | `ACT`→`true`, `INA`→`false` |
| `PROD_EFF_DT` / `PROD_EXP_DT` | `effective_date` / `expiration_date` | `VARCHAR` → `DATE` (null when blank) |

### `CDW_LN_ACCT` → `loan_accounts`

| Legacy | Modern | Conversion |
| --- | --- | --- |
| `LN_ACCT_NBR` | `account_number` | natural key kept |
| `BORR_ID` | `borrower_id` | FK resolved via `borrowers.external_id` |
| `PROD_CD` | `product_id` | FK resolved via `loan_products.code` |
| `BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4` | — | dropped: denormalized copies, now read through the `borrower` relationship |
| `LN_ORIG_AMT` / `LN_CURR_BAL` / `LN_PMT_AMT` / `LN_ESCROW_BAL` / `PROP_APRS_VAL` | `original_amount` / `current_balance` / `monthly_payment` / `escrow_balance` / `appraised_value` | `VARCHAR` → `DECIMAL` |
| `LN_INT_RT` | `interest_rate` | `VARCHAR` → `DECIMAL(5,3)` |
| `LN_LTV_PCT` | `ltv_percent` | `VARCHAR` → `DECIMAL(5,2)` |
| `LN_TERM_MOS` / `LN_DLQ_DAYS` | `term_months` / `delinquency_days` | `VARCHAR` → `INTEGER` |
| `LN_ORIG_DT` / `LN_MAT_DT` / `LN_1ST_PMT_DT` / `LN_NXT_PMT_DT` | `origination_date` / `maturity_date` / `first_payment_date` / `next_payment_date` | `VARCHAR MM/DD/YYYY` → `DATE` |
| `LN_STAT_CD` | `status` | `ACT`→`ACTIVE`, `CLO`→`CLOSED`, `DFT`→`DEFAULT`, `FRB`→`FORBEARANCE` |
| `PROP_ADDR_LN1` / `PROP_CTY_NM` / `PROP_ST_CD` / `PROP_ZIP_CD` | `property_address` / `property_city` / `property_state` / `property_zip` | verbatim |
| `PROP_TYP_CD` | `property_type` | `SFR`→`SINGLE_FAMILY`, `CND`→`CONDOMINIUM`, `MFR`→`MULTI_FAMILY`, `TWN`→`TOWNHOUSE` |
| `LN_CRET_DT` / `LN_UPDT_DT` | `created_at` / `updated_at` | `VARCHAR` → `TIMESTAMP` |

### `CDW_PMT_HIST` → `payments`

| Legacy | Modern | Conversion |
| --- | --- | --- |
| `PMT_SEQ_NBR` | `legacy_id` | kept verbatim; exposed as `PaymentDto.paymentId` |
| `LN_ACCT_NBR` | `loan_account_id` | FK resolved via `loan_accounts.account_number` |
| `PMT_DT` / `PMT_RECV_DT` / `PMT_PROC_DT` | `payment_date` / `received_date` / `processed_date` | `VARCHAR MM/DD/YYYY` → `DATE` |
| `PMT_AMT` / `PMT_PRIN_AMT` / `PMT_INT_AMT` / `PMT_ESCROW_AMT` / `PMT_LATE_FEE` | `total_amount` / `principal_amount` / `interest_amount` / `escrow_amount` / `late_fee` | `VARCHAR` → `DECIMAL(10,2)` |
| `PMT_TYP_CD` | `type` | `REG`→`REGULAR`, `EXT`→`EXTRA`, `PRT`→`PARTIAL`, `PRE`→`PREPAYMENT` |
| `PMT_STAT_CD` | `status` | `PST`→`POSTED`, `REV`→`REVERSED`, `NSF`→`NSF`, `PND`→`PENDING` |
| `PMT_CRET_DT` / `PMT_UPDT_DT` | `created_at` / `updated_at` | `VARCHAR` → `TIMESTAMP` |

## Type conversions

All conversions live in `LegacyValueParser` and are unit tested:

- `parseDate` — `MM/dd/yyyy` → `LocalDate`; null/blank → `null`; malformed non-null
  input throws `IllegalArgumentException` (the migration then fails startup).
- `parseTimestamp` — same parsing, `LocalDateTime` at start of day.
- `parseAmount` — strips thousands separators, → `BigDecimal`; null/blank → `null`.
- `parseInteger` — → `Integer`; null/blank → `null`.
- Code expansions — unknown codes throw rather than silently passing through.

Nullable modern columns keep `null` for blank legacy values; the DTO layer
zero-fills them, matching the legacy `parseLegacyAmount` behavior.

## Relationship mapping

- `LoanAccount` → `Borrower`: `@ManyToOne(LAZY)` on `borrower_id` (NOT NULL).
- `LoanAccount` → `LoanProduct`: `@ManyToOne(LAZY)` on `product_id` (NOT NULL).
- `Payment` → `LoanAccount`: `@ManyToOne(LAZY)` on `loan_account_id` (NOT NULL).

Foreign keys are resolved by natural key during migration. An unresolvable
reference throws `IllegalStateException`; no partial or guessed relationship is
ever written.

## Reconciliation results

Logged at startup and asserted by `DataMigrationIntegrationTest`:

| Entity | Legacy rows | Modern rows |
| --- | --- | --- |
| Borrowers | 5 | 5 |
| Loan products | 5 | 5 |
| Loan accounts | 5 | 5 |
| Payments | 10 | 10 |

The reconciliation also verifies that every loan account has a borrower and a
product, and every payment has a loan account. A mismatch fails startup.

## Endpoint-by-endpoint validation

Baseline responses were captured from the pre-migration application and stored in
`src/test/resources/golden/`. `ApiGoldenFileTest` replays all five endpoints and
compares strictly (structure, ordering and values).

| Endpoint | Golden file | Result |
| --- | --- | --- |
| `GET /api/loans` | `loans.json` | matches |
| `GET /api/loans/LN-2019-00142` | `loan-LN-2019-00142.json` | matches |
| `GET /api/borrowers` | `borrowers.json` | matches (byte-identical) |
| `GET /api/borrowers/B-10001` | `borrower-B-10001.json` | matches |
| `GET /api/loans/LN-2019-00142/payments` | `payments-LN-2019-00142.json` | matches (byte-identical) |

## Intentional differences

- **Dates stay `MM/DD/YYYY` strings in DTOs.** The entities store `LocalDate`;
  `LoanService` formats with `MM/dd/yyyy` when building DTOs. No ISO dates are
  emitted.
- **DTO ids stay legacy string keys.** `BorrowerDto.id` is `borrowers.external_id`
  and `PaymentDto.paymentId` is `payments.legacy_id`; the numeric surrogate keys
  are never exposed.
- **Display strings stay in the service.** The modern columns store canonical
  values (`ACTIVE`, `SINGLE_FAMILY`, `REGULAR`, `POSTED`); `LoanService` expands
  them to the existing display strings (`Active`, `Single Family Residence`,
  `Regular`, `Posted`).
- **Whole-dollar amounts now serialize with a decimal fraction.** A legacy
  `"285000"` string was emitted as `285000`; the same value read from
  `DECIMAL(12,2)` serializes as `285000.00`. The values are numerically equal and
  the golden comparison treats them as equal; no rounding or precision is lost.
  This is the only textual difference in any of the five responses.

## Legacy cleanup decisions

- `schema-legacy.sql` and `data-legacy.sql` are retained: they are the migration
  source and are still loaded at startup.
- `Legacy*` entities and repositories are retained and marked `@Deprecated` with
  Javadoc stating they exist only for the migration. Only `DataMigrationService`
  references them.
- Both schema scripts now begin with `DROP TABLE IF EXISTS` so initialization is
  repeatable when several application contexts share the in-memory database
  (as happens across test classes).

## Known limitations

- The migration is a full reload: it clears and repopulates the modern tables on
  every startup. That is correct for an in-memory demo database but would need to
  become an upsert (or a one-off job) against a persistent database.
- Legacy timestamps carry only a date, so `created_at`/`updated_at` are stored at
  midnight.
- Denormalized borrower fields on `CDW_LN_ACCT` are dropped in favor of the
  relationship; if legacy copies ever disagreed with `CDW_BORR_MSTR`, the master
  record wins.
- The seed data contains no malformed values, so the parser's failure paths are
  exercised by unit tests rather than by real data.
