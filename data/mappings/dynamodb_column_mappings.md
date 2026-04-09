# Legacy MySQL to Modern DynamoDB Column Mappings

## CDW_BORR_MSTR (MySQL) -> Borrowers (DynamoDB)

| Legacy Column | Legacy Type | DynamoDB Attribute | DynamoDB Type | Transformation |
|---------------|-------------|-------------------|---------------|----------------|
| `BORR_ID` | VARCHAR(20) | `borrower_id` | S (String) | Direct copy; becomes Partition Key |
| `BORR_FST_NM` | VARCHAR(50) | `first_name` | S (String) | Direct copy |
| `BORR_LST_NM` | VARCHAR(50) | `last_name` | S (String) | Direct copy |
| `BORR_MID_INIT` | VARCHAR(1) | `middle_initial` | S (String) | Direct copy; NULL if absent |
| `BORR_SSN_ENCR` | VARCHAR(100) | `ssn_hash` | S (String) | Direct copy (re-encrypt recommended) |
| `BORR_DOB_DT` | VARCHAR(10) | `date_of_birth` | S (String) | Parse `MM/DD/YYYY` -> ISO 8601 `YYYY-MM-DD` |
| `BORR_ADDR_LN1` | VARCHAR(100) | `address_line1` | S (String) | Direct copy |
| `BORR_ADDR_LN2` | VARCHAR(100) | `address_line2` | S (String) | Direct copy; NULL if absent |
| `BORR_CTY_NM` | VARCHAR(50) | `city` | S (String) | Direct copy |
| `BORR_ST_CD` | VARCHAR(2) | `state` | S (String) | Direct copy |
| `BORR_ZIP_CD` | VARCHAR(10) | `zip_code` | S (String) | Direct copy |
| `BORR_PH_NBR` | VARCHAR(15) | `phone` | S (String) | Direct copy |
| `BORR_EMAIL_ADDR` | VARCHAR(100) | `email` | S (String) | Direct copy |
| `BORR_CRDT_SCR` | VARCHAR(5) | `credit_score` | N (Number) | Parse string -> integer |
| `BORR_EMP_STAT` | VARCHAR(20) | `employment_status` | S (String) | Direct copy |
| `BORR_ANN_INCM` | VARCHAR(15) | `annual_income` | N (Number) | Remove commas, parse -> decimal |
| `BORR_CRET_DT` | VARCHAR(10) | `created_at` | S (String) | Parse `MM/DD/YYYY` -> ISO 8601 `YYYY-MM-DDTHH:mm:ssZ` |
| `BORR_UPDT_DT` | VARCHAR(10) | `updated_at` | S (String) | Parse `MM/DD/YYYY` -> ISO 8601 `YYYY-MM-DDTHH:mm:ssZ` |
| `BORR_STAT_CD` | VARCHAR(5) | `status` | S (String) | Expand: `ACT`->`ACTIVE`, `INA`->`INACTIVE` |
| `BORR_REC_TYP` | VARCHAR(10) | *(dropped)* | -- | Not needed in modern schema |

## CDW_LN_PROD (MySQL) -> LoanProducts (DynamoDB)

| Legacy Column | Legacy Type | DynamoDB Attribute | DynamoDB Type | Transformation |
|---------------|-------------|-------------------|---------------|----------------|
| `PROD_CD` | VARCHAR(10) | `product_code` | S (String) | Direct copy; becomes Partition Key |
| `PROD_DESC_TXT` | VARCHAR(200) | `name` | S (String) | Direct copy |
| `PROD_TYP_CD` | VARCHAR(5) | `type` | S (String) | Direct copy (`FXD`, `ARM`, `FHA`, `VA`) |
| `PROD_TERM_MOS` | VARCHAR(5) | `term_months` | N (Number) | Parse string -> integer |
| `PROD_RT_TYP` | VARCHAR(10) | `rate_type` | S (String) | Direct copy (`FIXED`, `VARIABLE`) |
| `PROD_MIN_AMT` | VARCHAR(15) | `min_amount` | N (Number) | Remove commas, parse -> decimal |
| `PROD_MAX_AMT` | VARCHAR(15) | `max_amount` | N (Number) | Remove commas, parse -> decimal |
| `PROD_STAT_CD` | VARCHAR(5) | `is_active` | BOOL (Boolean) | `ACT`->true, `INA`->false |
| `PROD_EFF_DT` | VARCHAR(10) | `effective_date` | S (String) | Parse `MM/DD/YYYY` -> `YYYY-MM-DD` |
| `PROD_EXP_DT` | VARCHAR(10) | `expiration_date` | S (String) | Parse `MM/DD/YYYY` -> `YYYY-MM-DD` |

## CDW_LN_ACCT (MySQL) -> LoanAccounts (DynamoDB)

| Legacy Column | Legacy Type | DynamoDB Attribute | DynamoDB Type | Transformation |
|---------------|-------------|-------------------|---------------|----------------|
| `LN_ACCT_NBR` | VARCHAR(20) | `account_number` | S (String) | Direct copy; becomes Partition Key |
| `BORR_ID` | VARCHAR(20) | `borrower_id` | S (String) | Direct copy; references Borrowers table |
| `BORR_FST_NM` | VARCHAR(50) | *(dropped)* | -- | Denormalized; retrieve via borrower_id |
| `BORR_LST_NM` | VARCHAR(50) | *(dropped)* | -- | Denormalized; retrieve via borrower_id |
| `BORR_SSN_LST4` | VARCHAR(4) | *(dropped)* | -- | Denormalized; retrieve via borrower_id |
| `PROD_CD` | VARCHAR(10) | `product_code` | S (String) | Direct copy; references LoanProducts table |
| `LN_ORIG_AMT` | VARCHAR(15) | `original_amount` | N (Number) | Remove commas, parse -> decimal |
| `LN_CURR_BAL` | VARCHAR(15) | `current_balance` | N (Number) | Remove commas, parse -> decimal |
| `LN_INT_RT` | VARCHAR(8) | `interest_rate` | N (Number) | Parse string -> decimal |
| `LN_TERM_MOS` | VARCHAR(5) | `term_months` | N (Number) | Parse string -> integer |
| `LN_PMT_AMT` | VARCHAR(15) | `monthly_payment` | N (Number) | Remove commas, parse -> decimal |
| `LN_ORIG_DT` | VARCHAR(10) | `origination_date` | S (String) | Parse `MM/DD/YYYY` -> `YYYY-MM-DD` |
| `LN_MAT_DT` | VARCHAR(10) | `maturity_date` | S (String) | Parse `MM/DD/YYYY` -> `YYYY-MM-DD` |
| `LN_1ST_PMT_DT` | VARCHAR(10) | `first_payment_date` | S (String) | Parse `MM/DD/YYYY` -> `YYYY-MM-DD` |
| `LN_NXT_PMT_DT` | VARCHAR(10) | `next_payment_date` | S (String) | Parse `MM/DD/YYYY` -> `YYYY-MM-DD` |
| `LN_STAT_CD` | VARCHAR(5) | `status` | S (String) | Expand: `ACT`->`ACTIVE`, `CLO`->`CLOSED`, `DFT`->`DEFAULT`, `FRB`->`FORBEARANCE` |
| `LN_DLQ_DAYS` | VARCHAR(5) | `delinquency_days` | N (Number) | Parse string -> integer |
| `LN_ESCROW_BAL` | VARCHAR(15) | `escrow_balance` | N (Number) | Remove commas, parse -> decimal |
| `LN_LTV_PCT` | VARCHAR(8) | `ltv_percent` | N (Number) | Parse string -> decimal |
| `PROP_ADDR_LN1` | VARCHAR(100) | `property_address` | S (String) | Direct copy |
| `PROP_CTY_NM` | VARCHAR(50) | `property_city` | S (String) | Direct copy |
| `PROP_ST_CD` | VARCHAR(2) | `property_state` | S (String) | Direct copy |
| `PROP_ZIP_CD` | VARCHAR(10) | `property_zip` | S (String) | Direct copy |
| `PROP_TYP_CD` | VARCHAR(10) | `property_type` | S (String) | Expand: `SFR`->`Single Family Residence`, `CND`->`Condominium`, `MFR`->`Multi-Family Residence`, `TWN`->`Townhouse` |
| `PROP_APRS_VAL` | VARCHAR(15) | `appraised_value` | N (Number) | Remove commas, parse -> decimal |
| `LN_CRET_DT` | VARCHAR(10) | `created_at` | S (String) | Parse `MM/DD/YYYY` -> ISO 8601 `YYYY-MM-DDTHH:mm:ssZ` |
| `LN_UPDT_DT` | VARCHAR(10) | `updated_at` | S (String) | Parse `MM/DD/YYYY` -> ISO 8601 `YYYY-MM-DDTHH:mm:ssZ` |

## CDW_PMT_HIST (MySQL) -> Payments (DynamoDB)

| Legacy Column | Legacy Type | DynamoDB Attribute | DynamoDB Type | Transformation |
|---------------|-------------|-------------------|---------------|----------------|
| `PMT_SEQ_NBR` | VARCHAR(20) | `payment_id` | S (String) | Direct copy; also used in composite sort key |
| `LN_ACCT_NBR` | VARCHAR(20) | `loan_account_id` | S (String) | Direct copy; becomes Partition Key |
| -- | -- | `payment_sort_key` | S (String) | **Computed:** `{payment_date}#{payment_id}` (e.g., `2025-12-15#PMT-2025120001`). Sort Key for chronological ordering. |
| `PMT_DT` | VARCHAR(10) | `payment_date` | S (String) | Parse `MM/DD/YYYY` -> `YYYY-MM-DD` |
| `PMT_AMT` | VARCHAR(15) | `total_amount` | N (Number) | Remove commas, parse -> decimal |
| `PMT_PRIN_AMT` | VARCHAR(15) | `principal_amount` | N (Number) | Remove commas, parse -> decimal |
| `PMT_INT_AMT` | VARCHAR(15) | `interest_amount` | N (Number) | Remove commas, parse -> decimal |
| `PMT_ESCROW_AMT` | VARCHAR(15) | `escrow_amount` | N (Number) | Remove commas, parse -> decimal |
| `PMT_LATE_FEE` | VARCHAR(15) | `late_fee` | N (Number) | Remove commas, parse -> decimal |
| `PMT_TYP_CD` | VARCHAR(5) | `type` | S (String) | Expand: `REG`->`REGULAR`, `EXT`->`EXTRA`, `PRT`->`PARTIAL`, `PRE`->`PREPAYMENT` |
| `PMT_STAT_CD` | VARCHAR(5) | `status` | S (String) | Expand: `PST`->`POSTED`, `REV`->`REVERSED`, `NSF`->`NSF`, `PND`->`PENDING` |
| `PMT_RECV_DT` | VARCHAR(10) | `received_date` | S (String) | Parse `MM/DD/YYYY` -> `YYYY-MM-DD` |
| `PMT_PROC_DT` | VARCHAR(10) | `processed_date` | S (String) | Parse `MM/DD/YYYY` -> `YYYY-MM-DD` |
| `PMT_CRET_DT` | VARCHAR(10) | `created_at` | S (String) | Parse `MM/DD/YYYY` -> ISO 8601 `YYYY-MM-DDTHH:mm:ssZ` |
| `PMT_UPDT_DT` | VARCHAR(10) | `updated_at` | S (String) | Parse `MM/DD/YYYY` -> ISO 8601 `YYYY-MM-DDTHH:mm:ssZ` |

## Common Transformation Patterns

1. **Date conversion:** `MM/DD/YYYY` string -> ISO 8601 `YYYY-MM-DD` (dates) or `YYYY-MM-DDTHH:mm:ssZ` (timestamps)
2. **Amount conversion:** Remove commas from string, store as DynamoDB Number (N) type
3. **Status expansion:** Short codes -> readable values (`ACT`->`ACTIVE`, etc.)
4. **Denormalization removal:** Drop borrower fields from loan accounts; look up via `borrower_id` at application level
5. **Composite sort key:** Payments use `{date}#{id}` pattern for natural chronological ordering with uniqueness
6. **DynamoDB-native types:** VARCHAR -> S, numeric strings -> N, boolean codes -> BOOL
