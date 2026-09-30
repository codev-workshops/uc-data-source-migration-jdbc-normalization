# Legacy Data Quality Report

Validation of the legacy CDW tables (`src/main/resources/schema-legacy.sql`, seeded by
`src/main/resources/data-legacy.sql`) ahead of migration to the modern schema.

- **Engine:** `com.workshop.loanservice.validation.LegacyDataValidator` (rule sets built with `RuleSet`)
- **Scoring:** `com.workshop.loanservice.service.DataQualityService` — every record starts at 100;
  each ERROR deducts 25, each WARNING deducts 10 (floored at 0)
- **Runtime endpoint:** `GET /api/data-quality/report`
- **Mapping reference:** `data/mappings/column_mappings.md` (rows cited as *table → column*)

Severity semantics: **ERROR** = the value cannot be loaded by the documented transformation
(or breaks an integrity rule the modern schema enforces); **WARNING** = the value loads but is
suspicious, lossy, or semantically inconsistent.

Format checks (date, amount, integer, range) skip nulls; nullness is reported only by the
explicit required/warn-if-null rules so a missing value yields exactly one finding.

---

## 1. Validation rules

### CDW_BORR_MSTR → borrowers

| Column(s) | Rule | Severity | Rationale / mapping row |
|---|---|---|---|
| `BORR_ID`, `BORR_FST_NM`, `BORR_LST_NM` | not null/blank | ERROR | `BORR_ID → external_id` is the lookup key for `loan_accounts.borrower_id`; names are direct copies used in `fullName` |
| `BORR_DOB_DT`, `BORR_CRET_DT`, `BORR_UPDT_DT` | strict `MM/DD/YYYY`, real calendar date | ERROR | "Parse MM/DD/YYYY → DATE/TIMESTAMP" (`date_of_birth`, `created_at`, `updated_at`) |
| `BORR_CRDT_SCR` | integer 300–850 | ERROR | "Parse string → integer" (`credit_score`); FICO range sanity |
| `BORR_ANN_INCM` | decimal after comma-stripping, >= 0 | ERROR | "Remove commas, parse → decimal" (`annual_income DECIMAL(12,2)`) |
| `BORR_STAT_CD` | in {ACT, INA} | ERROR | "Expand: ACT→ACTIVE, INA→INACTIVE" — any other code has no target value |
| `BORR_ST_CD` | exactly 2 characters | ERROR | `state VARCHAR(2)`; API contract expects 2-char state |
| `BORR_EMAIL_ADDR` | contains `@` | ERROR | Direct copy to `email`; minimal deliverability sanity |
| `BORR_MID_INIT`, `BORR_ADDR_LN2` | null | WARNING | Nullable direct copies; flagged so the null path is exercised and visible (e.g. `fullName` omits the initial) |

### CDW_LN_PROD → loan_products

| Column(s) | Rule | Severity | Rationale / mapping row |
|---|---|---|---|
| `PROD_CD` | not null/blank | ERROR | `code`; lookup key for `loan_accounts.product_id` |
| `PROD_STAT_CD` | in {ACT, INA} | ERROR | "ACT→true, INA→false" (`is_active BOOLEAN`) — no third value possible |
| `PROD_EFF_DT`, `PROD_EXP_DT` | `MM/DD/YYYY`; effective < expiration | ERROR | "Parse MM/DD/YYYY → DATE"; inverted windows make the product never valid |
| `PROD_MIN_AMT`, `PROD_MAX_AMT` | parseable amounts; min <= max | ERROR | "Remove commas, parse → decimal" |
| `PROD_TERM_MOS` | positive integer | ERROR | "Parse string → integer" (`term_months`) |

### CDW_LN_ACCT → loan_accounts

| Column(s) | Rule | Severity | Rationale / mapping row |
|---|---|---|---|
| `LN_ACCT_NBR`, `BORR_ID`, `PROD_CD` | not null/blank | ERROR | `account_number`; FK lookups |
| `BORR_ID` | exists in CDW_BORR_MSTR | ERROR | "Lookup borrowers.id by external_id" — legacy schema has **no FK**, so orphans would fail the modern FK |
| `PROD_CD` | exists in CDW_LN_PROD | ERROR | "Lookup loan_products.id by code" — same reason |
| `LN_ORIG_DT`, `LN_MAT_DT`, `LN_1ST_PMT_DT`, `LN_NXT_PMT_DT` | `MM/DD/YYYY` | ERROR | "Parse MM/DD/YYYY → DATE" |
| `LN_ORIG_AMT`, `LN_CURR_BAL`, `LN_PMT_AMT`, `LN_ESCROW_BAL`, `PROP_APRS_VAL` | parseable amounts | ERROR | "Remove commas, parse → decimal" |
| `LN_INT_RT` | plain decimal 0–30 that fits `DECIMAL(5,3)` | ERROR | "Parse string → decimal" (`interest_rate DECIMAL(5,3)`); `LoanService.parseLegacyDecimal` does not strip commas |
| `LN_TERM_MOS` / `LN_DLQ_DAYS` | integer >= 1 / integer >= 0 | ERROR | "Parse string → integer" |
| `LN_STAT_CD` | in {ACT, CLO, DFT, FRB} | ERROR | Status expansion row; unknown codes leak raw into the API |
| `PROP_TYP_CD` | in {SFR, CND, MFR, TWN} | ERROR | Property-type expansion row |
| `LN_LTV_PCT` | decimal 0–150 | ERROR | "Parse string → decimal" (`ltv_percent DECIMAL(5,2)`) |
| `LN_STAT_CD` + `LN_DLQ_DAYS` | ACT with delinquency > 0 | WARNING | Semantic conflict: an active, current loan should not carry delinquent days |
| `BORR_FST_NM`, `BORR_LST_NM` | differ from referenced CDW_BORR_MSTR row | WARNING | Mapping drops these ("Denormalized; use borrower FK"); drift means API `borrowerName` changes post-migration |

### CDW_PMT_HIST → payments

| Column(s) | Rule | Severity | Rationale / mapping row |
|---|---|---|---|
| `PMT_SEQ_NBR`, `LN_ACCT_NBR` | not null/blank | ERROR | Identity and FK lookup |
| `LN_ACCT_NBR` | exists in CDW_LN_ACCT | ERROR | "Lookup loan_accounts.id by account_number" — no legacy FK |
| `PMT_DT`, `PMT_RECV_DT`, `PMT_PROC_DT`, `PMT_CRET_DT`, `PMT_UPDT_DT` | `MM/DD/YYYY` | ERROR | "Parse MM/DD/YYYY → DATE/TIMESTAMP" |
| `PMT_AMT`, `PMT_PRIN_AMT`, `PMT_INT_AMT`, `PMT_ESCROW_AMT`, `PMT_LATE_FEE` | parseable amounts | ERROR | "Remove commas, parse → decimal" |
| `PMT_TYP_CD` / `PMT_STAT_CD` | in {REG, EXT, PRT, PRE} / {PST, REV, NSF, PND} | ERROR | Type/status expansion rows |
| `PMT_AMT` | principal + interest + escrow + late fee = total | WARNING | Components are migrated independently; unreconciled rows break ledger checks |
| `PMT_PROC_DT` | processed before received | WARNING | Chronologically impossible |
| `PMT_LATE_FEE` | received after `PMT_DT` but late fee = 0 | WARNING | Late receipt without a fee indicates a missed or waived fee that is not recorded |

---

## 2. Findings against the seed data

Result of `GET /api/data-quality/report` on the seeded H2 database
(also asserted in `LegacyDataValidatorTest` and `DataQualityServiceTest`):

| Metric | Value |
|---|---|
| Records validated | 25 (5 borrowers, 5 products, 5 loans, 10 payments) |
| ERROR findings | 0 |
| WARNING findings | 9 |
| Average score | 96.4 |
| Minimum score | 80 (B-10005) |

| Table | Records | Warnings | Avg score | Min score |
|---|---|---|---|---|
| CDW_BORR_MSTR | 5 | 4 | 92.0 | 80 |
| CDW_LN_PROD | 5 | 0 | 100.0 | 100 |
| CDW_LN_ACCT | 5 | 1 | 98.0 | 90 |
| CDW_PMT_HIST | 10 | 4 | 96.0 | 90 |

### Findings

| Severity | Table | Record | Column | Message |
|---|---|---|---|---|
| WARNING | CDW_BORR_MSTR | B-10002 | BORR_ADDR_LN2 | value is null |
| WARNING | CDW_BORR_MSTR | B-10003 | BORR_ADDR_LN2 | value is null |
| WARNING | CDW_BORR_MSTR | **B-10005** | **BORR_MID_INIT** | value is null |
| WARNING | CDW_BORR_MSTR | B-10005 | BORR_ADDR_LN2 | value is null |
| WARNING | CDW_LN_ACCT | **LN-2018-00089** | LN_STAT_CD | status ACT conflicts with LN_DLQ_DAYS=15 |
| WARNING | CDW_PMT_HIST | **PMT-2025120001** | PMT_AMT | principal+interest+escrow+late fee = 1887.02 does not equal PMT_AMT 1,487.02 |
| WARNING | CDW_PMT_HIST | PMT-2025110001 | PMT_AMT | principal+interest+escrow+late fee = 1887.02 does not equal PMT_AMT 1,487.02 |
| WARNING | CDW_PMT_HIST | PMT-2025110003 | PMT_AMT | principal+interest+escrow+late fee = 1124.55 does not equal PMT_AMT 1,077.05 |
| WARNING | CDW_PMT_HIST | **PMT-2025120003** | PMT_LATE_FEE | received 12/05/2025 after payment date 12/01/2025 but late fee is 0.00 |

### Known anomalies explained

- **Null middle initial (B-10005).** Loads fine (nullable column); `LoanService.toBorrowerDto`
  omits the initial, so `fullName` is `Robert Williams`. Three borrowers also have no address line 2.
- **ACT + delinquency (LN-2018-00089).** Status `ACT` with `LN_DLQ_DAYS = 15`. The API reports
  `Active`; the modern `status` column will too unless the business decides whether 15 days
  delinquent should be a distinct state.
- **Unreconciled payment components.** Both LN-2019-00142 payments (PMT-2025120001 and
  PMT-2025110001) exceed the recorded total by exactly **400.00** (components sum to 1,887.02 vs
  `PMT_AMT` 1,487.02); the source of the difference needs business confirmation. The check
  also surfaced **PMT-2025110003**, where the 47.50 late fee is itemised but not included in
  `PMT_AMT` (P+I = 1,077.05 = `PMT_AMT`). Both patterns need a business rule before migration.
- **Late receipt, zero late fee (PMT-2025120003).** Due 12/01/2025, received 12/05, processed
  12/06, `PMT_LATE_FEE = 0.00`. The previous month on the same loan (PMT-2025110003, received 17
  days late) was charged 47.50, so the waiver is unrecorded or the fee was missed.
- **Comma-formatted amounts.** 52 seeded values are stored with thousands separators
  (e.g. `'285,000'`, `'1,487.02'`); all parse after comma-stripping, so no ERROR is raised.
  `LN_INT_RT`/`LN_LTV_PCT` are parsed without stripping, matching `LoanService.parseLegacyDecimal`.
  `ApiContractTest.responsesDoNotLeakLegacyFormatting` guards that none reach the API.
- **String dates.** 105 seeded date values are `VARCHAR(10)` `MM/DD/YYYY`; all are real calendar
  dates. The API still returns them as `MM/DD/YYYY` strings (e.g. `originationDate`), so switching
  to `DATE` columns will change the wire format unless the DTO formats them explicitly.

---

## 3. Data-loss risk notes

| Risk | Detail | Recommendation |
|---|---|---|
| `BORR_REC_TYP` dropped | Mapping: "*(dropped)* — Not needed in modern schema". All seed rows are `PRI`, so nothing is lost today, but a non-`PRI` value (e.g. co-borrower) would be silently discarded. | Add a pre-migration assertion that every row is `PRI`, or keep the column. |
| Denormalized borrower columns dropped | `BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4` on CDW_LN_ACCT are dropped in favour of the FK. `LoanService.toLoanSummary` currently builds `borrowerName` from the **denormalized** copy. | The name-drift WARNING rule must be clean before cut-over (it is for the seed); otherwise `borrowerName` changes after migration. `BORR_SSN_LST4` is not cross-checked because the master holds only an encrypted SSN. |
| `PMT_SEQ_NBR` traceability | Mapping: "Auto-generated; legacy ID stored if needed". The API exposes it as `paymentId` (`PMT-2025120001`). Replacing it with a BIGINT breaks the API contract and any external reconciliation. | **Retain** the legacy value in a unique `legacy_payment_id` column and keep serving it as `paymentId`. |
| `LoanService.parseLegacyAmount` converts null/blank to `ZERO` | A missing balance or payment amount is reported to API consumers as `0`, indistinguishable from a real zero; the same applies to `parseLegacyDecimal` for `interestRate`. Format rules here skip nulls, so this is not scored. | Migrate nulls as SQL `NULL` (not `0`) and decide per column whether null is legal; consider adding warn-if-null rules for amount columns before cut-over. |

---

## Re-running

```bash
mvn -B test                          # validator, scoring, and API contract tests
mvn -B spring-boot:run
curl -s localhost:8080/api/data-quality/report | jq '.findingsBySeverity, .findings'
```
