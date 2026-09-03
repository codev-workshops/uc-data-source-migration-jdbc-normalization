# Legacy Data Source Migration Analysis

> Read-only discovery of the current legacy "CDW" (Corporate Data Warehouse) data source backing the loan service. This documents the current state and its problems only — not the target solution.

## 1. Legacy Schema Inventory

The legacy schema is defined in `src/main/resources/schema-legacy.sql` (the file `data/legacy-schema/cdw_tables.sql` is a descriptive stub that points to it). It contains four tables, all VARCHAR-typed with cryptic abbreviated column names.

### `CDW_BORR_MSTR` — Borrower Master (a person holding loans)
Primary key `BORR_ID` (e.g. `B-10001`). Columns: `BORR_FST_NM`/`BORR_LST_NM`/`BORR_MID_INIT` = first/last/middle initial; `BORR_SSN_ENCR` = encrypted SSN (`ENC_XXX_001`); `BORR_DOB_DT` = date of birth; `BORR_ADDR_LN1`/`LN2`, `BORR_CTY_NM`, `BORR_ST_CD`, `BORR_ZIP_CD` = address; `BORR_PH_NBR`, `BORR_EMAIL_ADDR` = contact; `BORR_CRDT_SCR` = credit score; `BORR_EMP_STAT` = employment status; `BORR_ANN_INCM` = annual income; `BORR_CRET_DT`/`BORR_UPDT_DT` = audit dates; `BORR_STAT_CD`, `BORR_REC_TYP` = status/record-type codes.

### `CDW_LN_PROD` — Loan Product catalog (mortgage product definitions)
Primary key `PROD_CD` (`FXD30`, `ARM51`, `FHA30`...). Columns: `PROD_DESC_TXT` = description; `PROD_TYP_CD` = FXD/ARM/FHA/VA; `PROD_TERM_MOS` = term in months; `PROD_RT_TYP` = FIXED/VARIABLE; `PROD_MIN_AMT`/`PROD_MAX_AMT` = amount bounds; `PROD_STAT_CD`; `PROD_EFF_DT`/`PROD_EXP_DT` = effective/expiry dates.

### `CDW_LN_ACCT` — Loan Account (a specific mortgage held by a borrower)
Primary key `LN_ACCT_NBR` (`LN-2019-00142`). Key columns: `BORR_ID` (link to borrower) plus **denormalized** `BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4`; `PROD_CD` (link to product); `LN_ORIG_AMT`, `LN_CURR_BAL`, `LN_INT_RT` (`4.750`), `LN_TERM_MOS`, `LN_PMT_AMT`; dates `LN_ORIG_DT`/`LN_MAT_DT`/`LN_1ST_PMT_DT`/`LN_NXT_PMT_DT`; `LN_STAT_CD` (ACT/CLO/DFT/FRB); `LN_DLQ_DAYS`, `LN_ESCROW_BAL`, `LN_LTV_PCT`; property fields `PROP_ADDR_LN1`, `PROP_CTY_NM`, `PROP_ST_CD`, `PROP_ZIP_CD`, `PROP_TYP_CD` (SFR/CND/MFR/TWN), `PROP_APRS_VAL`; audit `LN_CRET_DT`/`LN_UPDT_DT`.

### `CDW_PMT_HIST` — Payment History (individual loan payments)
Primary key `PMT_SEQ_NBR` (`PMT-2025120001`). Columns: `LN_ACCT_NBR` (link to loan); `PMT_DT`; `PMT_AMT`, `PMT_PRIN_AMT`, `PMT_INT_AMT`, `PMT_ESCROW_AMT`, `PMT_LATE_FEE` (money split); `PMT_TYP_CD` (REG/EXT/PRT/PRE); `PMT_STAT_CD` (PST/REV/NSF/PND); dates `PMT_RECV_DT`/`PMT_PROC_DT`/`PMT_CRET_DT`/`PMT_UPDT_DT`.

## 2. Data Quality Problems (from seed data in `src/main/resources/data-legacy.sql`)

| Antipattern | Evidence |
|---|---|
| All-VARCHAR typing | Every column is `VARCHAR`, including numbers, dates, and codes |
| String-encoded dates | `MM/DD/YYYY` strings like `'03/15/1978'`, `'02/15/2049'` — no date type, no validation |
| String amounts with commas | `'92,500'`, `'285,000'`, `'1,487.02'` — thousands separators embedded in text |
| Numbers as strings | Credit score `'745'`, interest rate `'4.750'`, LTV `'82.5'`, term `'360'`, delinquency `'15'` |
| Cryptic status codes | `ACT`, `PRI`, `SFR`, `CND`, `TWN`, `REG`, `PST`, `EMPLOYED`, `SELF-EMP`, `RETIRED` |
| Denormalization / duplicated fields | `CDW_LN_ACCT` re-stores `BORR_FST_NM`/`BORR_LST_NM` already in `CDW_BORR_MSTR`; property address duplicates borrower address for owner-occupied loans |
| Relationships without foreign keys | `BORR_ID`, `PROD_CD`, `LN_ACCT_NBR` are plain VARCHARs with no FK constraints — nothing prevents orphaned loans/payments |
| Null / sparse values | `BORR_ADDR_LN2` is `NULL` for several rows; `BORR_MID_INIT` is `NULL` for `B-10005` |

Note: the seed data as written is internally consistent (all `BORR_ID`/`PROD_CD` references resolve, all amounts/dates are well-formed), so the malformed-value risk is latent (the schema permits it) rather than present in the current five borrower / five loan / ten payment rows.

## 3. Application Coupling

All endpoints funnel through a single `LoanService`, so both `LoanController` and `BorrowerController` are tightly coupled to the legacy translation layer.

Endpoint → dependency trace:
- `GET /api/loans` → `getAllLoans()` loads all products into a map, streams all accounts, joins in memory
- `GET /api/loans/{id}` → `getLoanById()` uses `findById` on account + product
- `GET /api/loans/{loanId}/payments` → `getPaymentsByLoan()` via `findByLoanAccountNumberOrderByPaymentDateDesc`
- `GET /api/borrowers` → `getAllBorrowers()`
- `GET /api/borrowers/{id}` → `getBorrowerById()` loads borrower, then products map, then `findByBorrowerId` and joins loans manually

Where the service compensates for the legacy schema (in `LoanService.java`):
- **Manual joins (no FKs):** products loaded into a `Map` and joined to accounts in application code rather than via SQL joins.
- **String→number parsing:** `parseLegacyAmount` strips commas and builds `BigDecimal`; `parseLegacyDecimal`/`parseLegacyInteger` trim and parse.
- **Code translation:** `expandStatusCode`, `expandPropertyType`, `expandPaymentType`, `expandPaymentStatus` map cryptic codes to human labels.
- **String concatenation for denormalized fields:** borrower name and full property address are assembled from separate columns.
- **Dates passed through untouched:** `originationDate` and `paymentDate` are copied as raw `MM/DD/YYYY` strings into the DTOs (no parsing/validation).

The entities (e.g. `LegacyLoanAccount`) are string-for-string mirrors of the legacy tables (every field is `String`), so the service is the only place types and semantics are recovered.

## 4. Business Risk

| Problem | Concrete risk | Rating |
|---|---|---|
| String amounts + `parseLegacyAmount` | Stray/unexpected format throws `NumberFormatException` → 500 error on the whole listing. Blank/null silently becomes `BigDecimal.ZERO`, so a missing balance is reported as $0.00 — silent financial corruption | High |
| String dates (`MM/DD/YYYY`, no validation) | Dates are never parsed; malformed values pass straight to clients. No sorting/comparison; downstream consumers can't reliably compute maturity/delinquency | High |
| No foreign keys | Orphaned loans (bad `BORR_ID`) or payments (bad `LN_ACCT_NBR`) possible; `getLoanById` throws `RuntimeException` → 500 when product/borrower missing | High |
| Denormalized borrower name in `CDW_LN_ACCT` | Update anomaly: a name/address change in `CDW_BORR_MSTR` is not reflected in loan rows → inconsistent data depending on endpoint | Medium |
| Cryptic codes with `default -> code` fallback | An unmapped code leaks the raw abbreviation to end users instead of a label — confusing but not corrupting | Low/Medium |
| Numbers as strings (credit score, rate, LTV) | `parseLegacyInteger`/`parseLegacyDecimal` throw on non-numeric input; no range validation | Medium |
| Null values (`ADDR_LN2`, `MID_INIT`) | Handled for middle initial, but null names/addresses would produce `"null null"` in concatenated fields | Low |

## 5. Testing Gap

The only test is `LoanServiceApplicationTests.contextLoads()`, an empty `@SpringBootTest` that just verifies the Spring context starts. There is zero coverage of translation logic, repositories, controllers, or edge cases.

Safety net needed before any migration:
- Unit tests for every translation method (`parseLegacyAmount`, `parseLegacyDecimal`, `parseLegacyInteger`, and all four `expand*` mappers including the `default` fallback).
- Service-level tests for `getAllLoans`, `getLoanById`, `getAllBorrowers`, `getBorrowerById`, `getPaymentsByLoan`, including not-found `RuntimeException` paths and manual joins.
- Repository tests for custom finders (`findByBorrowerId`, `findByLoanAccountNumberOrderByPaymentDateDesc`).
- Controller / API contract tests (e.g. `MockMvc`) capturing the exact JSON shape of `LoanSummaryDto`, `BorrowerDto`, `PaymentDto` as golden output.
- Characterization / golden-master tests snapshotting current responses against the legacy seed data to detect behavioral drift.

## Why this data source should be migrated

*(Written for a non-technical stakeholder.)*

Today, all of our loan and borrower information is stored in a decades-old data warehouse that treats every piece of information — dollar amounts, dates, credit scores, account statuses — as plain text, with no rules to keep it correct. Think of it like a spreadsheet where every cell is free-form text: nothing stops someone from typing a date as "13/40/2025" or leaving a loan balance blank.

This creates real business risks:

- **Money can be silently wrong.** Loan balances and payment amounts are stored as text. If a value is missing or slightly malformed, the system either crashes the whole page or quietly reports the balance as $0.00 — the kind of error that erodes trust and can lead to bad decisions or compliance problems.
- **Dates can't be trusted or compared.** Because dates are stored as text with no validation, the system can't reliably tell which loans are maturing, overdue, or delinquent.
- **Records can become disconnected.** There are no built-in links between borrowers, their loans, and their payments. A loan can end up pointing to a borrower who doesn't exist, and nothing in the database prevents it.
- **The same information is stored in multiple places.** A borrower's name lives in two tables. If it's updated in one place and not the other, different screens show different answers for the same customer.
- **All the intelligence lives in fragile application code.** The meaning of cryptic codes and the cleanup of messy text happens inside one service. That makes the system brittle, hard to change, and risky to hand off to new team members.
- **There is virtually no safety net.** The only automated test simply checks that the application starts. Nothing verifies that the numbers, dates, and customer details are correct.

Migrating to a modern, properly structured database lets the database itself enforce correctness — real number and date types, guaranteed relationships between records, and a single source of truth for each fact. The result is fewer outages, more trustworthy financial figures, easier regulatory reporting, and a system that is safer and cheaper to maintain and evolve.
