# Proposed Legacy → Target Column Mappings

Field-level mapping from the legacy CDW tables (`src/main/resources/schema-legacy.sql`) to the proposed target schema (`docs/proposed-target-schema.sql`). Companion to `docs/MIGRATION_ANALYSIS.md` and `docs/DESIGN_DECISIONS.md`.

## Transformation rules (referenced by ID below)

| Rule | Name | Definition |
|---|---|---|
| **T-DATE** | Date parse | Trim; parse strict `MM/dd/yyyy` (`DateTimeFormatter.ofPattern("MM/dd/yyyy").withResolverStyle(STRICT)`). Empty/NULL → NULL (only where target is nullable). Unparseable → reject row to quarantine. |
| **T-TS** | Audit date → timestamp | T-DATE then widen to `TIMESTAMP` at `00:00:00` (legacy has no time component). |
| **T-AMT** | Amount de-format | Trim; strip `,` and optional leading `$`; parse `BigDecimal`; scale to 2 (`HALF_UP`). Empty/NULL → NULL, **not** zero (differs from current `parseLegacyAmount`). Non-numeric → quarantine. |
| **T-DEC** | Decimal parse | Trim; parse `BigDecimal`; scale as per target column. Non-numeric → quarantine. |
| **T-INT** | Integer parse | Trim; parse integer; strip `,` if present. Non-numeric → quarantine. |
| **T-CODE** | Code lookup | Trim + upper-case; must exist in the target reference table (FK). Unknown code → quarantine (no `default -> code` pass-through). |
| **T-CODE-MAP** | Code lookup with rename | As T-CODE after applying a rename map (e.g. `SELF-EMP` → `SELF_EMPLOYED`). |
| **T-BOOL** | Code → boolean | `ACT` → `TRUE`, anything else → `FALSE`. |
| **T-ID** | ID resolution | Look up the legacy string ID in the target table's `legacy_*` / business-key column and substitute the surrogate `BIGINT id`. Missing parent → quarantine. |
| **T-ADDR** | Address extraction | Group address columns into an `address` row; reuse an existing row if all five fields match exactly (after trim); store the new `address.id`. |
| **T-COPY** | Copy | Trim; copy as-is (length-checked). |
| **T-DROP** | Drop | Not migrated; value is duplicated or derivable elsewhere. Validate against the source of truth first (see notes). |
| **T-SENTINEL** | Sentinel → NULL | `12/31/2099` (or later than `12/31/2098`) → NULL meaning "open-ended". |

Risk rating: **H** = parse can fail or silently change a financial/business value; **M** = parse can fail or lose information but is easy to detect; **L** = straight copy or trivially validated.

---

## `CDW_BORR_MSTR` → `borrower` (+ `address`)

| Legacy column | Target table.column | Type conversion | Rule | Risk | Notes |
|---|---|---|---|---|---|
| `BORR_ID` | `borrower.legacy_borrower_id` | VARCHAR(20) → VARCHAR(20) UNIQUE | T-COPY | L | Surrogate `borrower.id` is generated; this column preserves traceability. |
| `BORR_FST_NM` | `borrower.first_name` | VARCHAR → VARCHAR(50) NOT NULL | T-COPY | L | NULL/blank → quarantine (target is NOT NULL). |
| `BORR_LST_NM` | `borrower.last_name` | VARCHAR → VARCHAR(50) NOT NULL | T-COPY | L | As above. |
| `BORR_MID_INIT` | `borrower.middle_initial` | VARCHAR(1) → CHAR(1) NULL | T-COPY | L | NULL is legitimate (`B-10005`). |
| `BORR_SSN_ENCR` | `borrower.ssn_encrypted` | VARCHAR(100) → VARCHAR(100) | T-COPY | M | Opaque ciphertext; do not decrypt/re-encrypt in this migration. Confirm key custody (open question). |
| — (from `CDW_LN_ACCT.BORR_SSN_LST4`) | `borrower.ssn_last4` | VARCHAR(4) → CHAR(4) | T-COPY | M | Sourced from loan rows; all loans for a borrower must agree, else quarantine. |
| `BORR_DOB_DT` | `borrower.date_of_birth` | VARCHAR `MM/DD/YYYY` → DATE NOT NULL | T-DATE | M | Reject future dates or age > 120. |
| `BORR_ADDR_LN1` | `address.line1` (via `borrower.mailing_address_id`) | VARCHAR → VARCHAR(100) NOT NULL | T-ADDR | L | |
| `BORR_ADDR_LN2` | `address.line2` | VARCHAR → VARCHAR(100) NULL | T-ADDR | L | NULL is legitimate. |
| `BORR_CTY_NM` | `address.city` | VARCHAR → VARCHAR(50) NOT NULL | T-ADDR | L | |
| `BORR_ST_CD` | `address.state_code` | VARCHAR(2) → CHAR(2) NOT NULL | T-ADDR | L | Upper-case; validate against US state list. |
| `BORR_ZIP_CD` | `address.postal_code` | VARCHAR(10) → VARCHAR(10) NOT NULL | T-ADDR | L | |
| `BORR_PH_NBR` | `borrower.phone_number` | VARCHAR(15) → VARCHAR(20) | T-COPY | L | Format normalisation deferred (open question). |
| `BORR_EMAIL_ADDR` | `borrower.email_address` | VARCHAR(100) → VARCHAR(100) | T-COPY | L | Lower-case; light syntax check only. |
| `BORR_CRDT_SCR` | `borrower.credit_score` | VARCHAR(5) → SMALLINT | T-INT | M | CHECK 300–850; out-of-range → quarantine. |
| `BORR_EMP_STAT` | `borrower.employment_status_code` | VARCHAR(20) → FK `employment_status.code` | T-CODE-MAP | M | `SELF-EMP` → `SELF_EMPLOYED`; `EMPLOYED`, `RETIRED` unchanged. |
| `BORR_ANN_INCM` | `borrower.annual_income` | VARCHAR `'92,500'` → DECIMAL(15,2) | T-AMT | H | Financial figure; commas embedded. |
| `BORR_CRET_DT` | `borrower.created_at` | VARCHAR → TIMESTAMP NOT NULL | T-TS | M | |
| `BORR_UPDT_DT` | `borrower.updated_at` | VARCHAR → TIMESTAMP NOT NULL | T-TS | M | Must be ≥ `created_at`. |
| `BORR_STAT_CD` | `borrower.status_code` | VARCHAR(5) → FK `borrower_status.code` | T-CODE | M | Only `ACT` observed; other codes need labels (open question). |
| `BORR_REC_TYP` | `borrower.record_type_code` | VARCHAR(10) → FK `borrower_record_type.code` | T-CODE | M | Only `PRI` observed. |

## `CDW_LN_PROD` → `loan_product`

| Legacy column | Target table.column | Type conversion | Rule | Risk | Notes |
|---|---|---|---|---|---|
| `PROD_CD` | `loan_product.product_code` | VARCHAR(10) → VARCHAR(10) UNIQUE | T-COPY | L | Remains the business key; `loan_product.id` is the FK target. |
| `PROD_DESC_TXT` | `loan_product.description` | VARCHAR(200) → VARCHAR(200) NOT NULL | T-COPY | L | |
| `PROD_TYP_CD` | `loan_product.product_type_code` | VARCHAR(5) → FK `product_type.code` | T-CODE | L | FXD/ARM/FHA/VA all seeded. |
| `PROD_TERM_MOS` | `loan_product.term_months` | VARCHAR `'360'` → SMALLINT | T-INT | M | CHECK > 0. |
| `PROD_RT_TYP` | `loan_product.rate_type_code` | VARCHAR(10) → FK `rate_type.code` | T-CODE | L | |
| `PROD_MIN_AMT` | `loan_product.min_amount` | VARCHAR `'50,000'` → DECIMAL(15,2) | T-AMT | M | `'0'` is valid (VA30). |
| `PROD_MAX_AMT` | `loan_product.max_amount` | VARCHAR `'1,500,000'` → DECIMAL(15,2) | T-AMT | M | CHECK ≥ `min_amount`. |
| `PROD_STAT_CD` | `loan_product.is_active` | VARCHAR(5) → BOOLEAN | T-BOOL | L | `ACT` → TRUE. |
| `PROD_EFF_DT` | `loan_product.effective_date` | VARCHAR → DATE NOT NULL | T-DATE | M | |
| `PROD_EXP_DT` | `loan_product.expiry_date` | VARCHAR → DATE NULL | T-DATE + T-SENTINEL | M | All seed rows are `12/31/2099` → NULL. |

## `CDW_LN_ACCT` → `loan_account` (+ `property`, `address`)

| Legacy column | Target table.column | Type conversion | Rule | Risk | Notes |
|---|---|---|---|---|---|
| `LN_ACCT_NBR` | `loan_account.account_number` | VARCHAR(20) → VARCHAR(20) UNIQUE | T-COPY | L | Business key kept; `loan_account.id` generated. |
| `BORR_ID` | `loan_account.borrower_id` | VARCHAR(20) → BIGINT FK `borrower.id` | T-ID | H | Orphan (no matching borrower) → quarantine. |
| `BORR_FST_NM` | — | dropped | T-DROP | M | Duplicate of `CDW_BORR_MSTR.BORR_FST_NM`. **Pre-check:** report every row where it differs from the master. |
| `BORR_LST_NM` | — | dropped | T-DROP | M | As above. |
| `BORR_SSN_LST4` | `borrower.ssn_last4` | VARCHAR(4) → CHAR(4) | T-COPY (to borrower) | M | Relocated to borrower; conflicting values across a borrower's loans → quarantine. |
| `PROD_CD` | `loan_account.product_id` | VARCHAR(10) → BIGINT FK `loan_product.id` | T-ID | H | Unknown product → quarantine. |
| `LN_ORIG_AMT` | `loan_account.original_amount` | VARCHAR `'285,000'` → DECIMAL(15,2) NOT NULL | T-AMT | H | CHECK > 0. |
| `LN_CURR_BAL` | `loan_account.current_balance` | VARCHAR `'271,432.56'` → DECIMAL(15,2) NOT NULL | T-AMT | H | Blank must **not** become 0 (current service bug). |
| `LN_INT_RT` | `loan_account.interest_rate` | VARCHAR `'4.750'` → DECIMAL(6,3) NOT NULL | T-DEC | H | Percent units; CHECK 0–100. |
| `LN_TERM_MOS` | `loan_account.term_months` | VARCHAR `'360'` → SMALLINT NOT NULL | T-INT | M | Should equal product term; report mismatch. |
| `LN_PMT_AMT` | `loan_account.monthly_payment_amount` | VARCHAR `'1,487.02'` → DECIMAL(15,2) NOT NULL | T-AMT | H | |
| `LN_ORIG_DT` | `loan_account.origination_date` | VARCHAR → DATE NOT NULL | T-DATE | H | Currently passed to clients raw; parsing is new behaviour. |
| `LN_MAT_DT` | `loan_account.maturity_date` | VARCHAR → DATE NOT NULL | T-DATE | M | CHECK > `origination_date`. |
| `LN_1ST_PMT_DT` | `loan_account.first_payment_date` | VARCHAR → DATE NOT NULL | T-DATE | M | CHECK ≥ `origination_date`. |
| `LN_NXT_PMT_DT` | `loan_account.next_payment_date` | VARCHAR → DATE NULL | T-DATE | M | NULL allowed for closed loans. |
| `LN_STAT_CD` | `loan_account.status_code` | VARCHAR(5) → FK `loan_status.code` | T-CODE | M | ACT/CLO/DFT/FRB seeded. |
| `LN_DLQ_DAYS` | `loan_account.delinquency_days` | VARCHAR `'15'` → INTEGER NOT NULL DEFAULT 0 | T-INT | M | Blank → 0 is acceptable here (non-financial). |
| `LN_ESCROW_BAL` | `loan_account.escrow_balance` | VARCHAR `'3,245.80'` → DECIMAL(15,2) | T-AMT | H | |
| `LN_LTV_PCT` | `loan_account.loan_to_value_pct` | VARCHAR `'82.5'` → DECIMAL(6,2) | T-DEC | M | Derivable (`original_amount / appraised_value`); keep and cross-check within ±0.5 pt. |
| `PROP_ADDR_LN1` | `address.line1` (via `property.address_id`) | VARCHAR → VARCHAR(100) NOT NULL | T-ADDR | L | Dedup: seed data shows property = borrower mailing address for all five loans, so the same `address` row is reused. |
| `PROP_CTY_NM` | `address.city` | VARCHAR → VARCHAR(50) | T-ADDR | L | |
| `PROP_ST_CD` | `address.state_code` | VARCHAR(2) → CHAR(2) | T-ADDR | L | |
| `PROP_ZIP_CD` | `address.postal_code` | VARCHAR(10) → VARCHAR(10) | T-ADDR | L | |
| `PROP_TYP_CD` | `property.property_type_code` | VARCHAR(10) → FK `property_type.code` | T-CODE | M | SFR/CND/MFR/TWN seeded. |
| `PROP_APRS_VAL` | `property.appraised_value` | VARCHAR `'345,000'` → DECIMAL(15,2) NOT NULL | T-AMT | H | CHECK > 0. |
| `LN_CRET_DT` | `loan_account.created_at` | VARCHAR → TIMESTAMP NOT NULL | T-TS | M | |
| `LN_UPDT_DT` | `loan_account.updated_at` | VARCHAR → TIMESTAMP NOT NULL | T-TS | M | |

## `CDW_PMT_HIST` → `payment`

| Legacy column | Target table.column | Type conversion | Rule | Risk | Notes |
|---|---|---|---|---|---|
| `PMT_SEQ_NBR` | `payment.legacy_payment_id` | VARCHAR(20) → VARCHAR(20) UNIQUE | T-COPY | L | `payment.id` generated. |
| `LN_ACCT_NBR` | `payment.loan_account_id` | VARCHAR(20) → BIGINT FK `loan_account.id` | T-ID | H | Orphan payment → quarantine. |
| `PMT_DT` | `payment.payment_date` | VARCHAR → DATE NOT NULL | T-DATE | H | Drives the `ORDER BY ... DESC` in the payments endpoint; string sort → date sort may change order for mixed years. |
| `PMT_AMT` | `payment.total_amount` | VARCHAR `'1,487.02'` → DECIMAL(15,2) NOT NULL | T-AMT | H | |
| `PMT_PRIN_AMT` | `payment.principal_amount` | VARCHAR → DECIMAL(15,2) | T-AMT | H | |
| `PMT_INT_AMT` | `payment.interest_amount` | VARCHAR → DECIMAL(15,2) | T-AMT | H | |
| `PMT_ESCROW_AMT` | `payment.escrow_amount` | VARCHAR → DECIMAL(15,2) | T-AMT | H | `ck_payment_split` requires prin + int + escrow = total; all 10 seed rows satisfy this. |
| `PMT_LATE_FEE` | `payment.late_fee_amount` | VARCHAR `'47.50'` → DECIMAL(15,2) | T-AMT | M | Not part of the split check. |
| `PMT_TYP_CD` | `payment.payment_type_code` | VARCHAR(5) → FK `payment_type.code` | T-CODE | M | REG/EXT/PRT/PRE seeded. |
| `PMT_STAT_CD` | `payment.status_code` | VARCHAR(5) → FK `payment_status.code` | T-CODE | M | PST/REV/NSF/PND seeded. |
| `PMT_RECV_DT` | `payment.received_date` | VARCHAR → DATE NULL | T-DATE | M | |
| `PMT_PROC_DT` | `payment.processed_date` | VARCHAR → DATE NULL | T-DATE | M | CHECK ≥ `received_date`. |
| `PMT_CRET_DT` | `payment.created_at` | VARCHAR → TIMESTAMP NOT NULL | T-TS | M | |
| `PMT_UPDT_DT` | `payment.updated_at` | VARCHAR → TIMESTAMP NOT NULL | T-TS | M | |

---

## Summary by risk

| Risk | Count | Typical fields |
|---|---|---|
| **H** | 16 | Every money column except `PMT_LATE_FEE` (`*_AMT`, `*_BAL`, `PROP_APRS_VAL`, `BORR_ANN_INCM`), `LN_INT_RT`, the three FK resolutions (`BORR_ID`, `PROD_CD`, `LN_ACCT_NBR`), `LN_ORIG_DT`, `PMT_DT` |
| **M** | 33 | All other dates, integer parses, code lookups, dropped duplicate names, SSN fields |
| **L** | 22 | Identifiers, names, address text, descriptions |

Load order (respecting FKs): reference tables → `address` → `borrower` → `loan_product` → `property` → `loan_account` → `payment`.
