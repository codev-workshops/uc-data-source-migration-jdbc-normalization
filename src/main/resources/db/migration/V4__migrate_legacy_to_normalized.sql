-- =============================================================================
-- DATA MIGRATION: legacy CDW_* tables -> normalized tables
-- =============================================================================
-- Transformations per data/mappings/column_mappings.md:
--   - amounts:    strip thousands separators, cast to DECIMAL
--   - dates:      MM/DD/YYYY strings parsed to DATE / TIMESTAMP (NULL-safe)
--   - numbers:    credit score / terms / delinquency days cast to INTEGER
--   - codes:      short legacy codes expanded to the modern values
--   - identities: legacy string keys become UNIQUE natural keys; the BIGINT
--                 surrogate ids auto-generate and FKs are resolved by subquery
-- Parents (borrower, loan_product) load first so the FK lookups resolve.
-- =============================================================================

INSERT INTO borrower (
    external_id, first_name, last_name, middle_initial, ssn_hash, date_of_birth,
    address_line1, address_line2, city, state, zip_code, phone, email,
    credit_score, employment_status, annual_income, status, created_at, updated_at
)
SELECT
    BORR_ID,
    BORR_FST_NM,
    BORR_LST_NM,
    BORR_MID_INIT,
    BORR_SSN_ENCR,
    CASE WHEN BORR_DOB_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(BORR_DOB_DT, 'MM/dd/yyyy') AS DATE) END,
    BORR_ADDR_LN1,
    BORR_ADDR_LN2,
    BORR_CTY_NM,
    BORR_ST_CD,
    BORR_ZIP_CD,
    BORR_PH_NBR,
    BORR_EMAIL_ADDR,
    CASE WHEN BORR_CRDT_SCR IS NULL THEN NULL ELSE CAST(BORR_CRDT_SCR AS INTEGER) END,
    BORR_EMP_STAT,
    CASE WHEN BORR_ANN_INCM IS NULL THEN NULL ELSE CAST(REPLACE(BORR_ANN_INCM, ',', '') AS DECIMAL(12,2)) END,
    CASE BORR_STAT_CD
        WHEN 'ACT' THEN 'ACTIVE'
        WHEN 'INA' THEN 'INACTIVE'
        ELSE BORR_STAT_CD
    END,
    CASE WHEN BORR_CRET_DT IS NULL THEN CURRENT_TIMESTAMP ELSE CAST(PARSEDATETIME(BORR_CRET_DT, 'MM/dd/yyyy') AS TIMESTAMP) END,
    CASE WHEN BORR_UPDT_DT IS NULL THEN CURRENT_TIMESTAMP ELSE CAST(PARSEDATETIME(BORR_UPDT_DT, 'MM/dd/yyyy') AS TIMESTAMP) END
FROM CDW_BORR_MSTR;

INSERT INTO loan_product (
    code, name, type, term_months, rate_type, min_amount, max_amount,
    is_active, effective_date, expiration_date
)
SELECT
    PROD_CD,
    PROD_DESC_TXT,
    PROD_TYP_CD,
    CASE WHEN PROD_TERM_MOS IS NULL THEN NULL ELSE CAST(PROD_TERM_MOS AS INTEGER) END,
    PROD_RT_TYP,
    CASE WHEN PROD_MIN_AMT IS NULL THEN NULL ELSE CAST(REPLACE(PROD_MIN_AMT, ',', '') AS DECIMAL(12,2)) END,
    CASE WHEN PROD_MAX_AMT IS NULL THEN NULL ELSE CAST(REPLACE(PROD_MAX_AMT, ',', '') AS DECIMAL(12,2)) END,
    CASE WHEN PROD_STAT_CD = 'ACT' THEN TRUE ELSE FALSE END,
    CASE WHEN PROD_EFF_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(PROD_EFF_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN PROD_EXP_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(PROD_EXP_DT, 'MM/dd/yyyy') AS DATE) END
FROM CDW_LN_PROD;

-- Embedded borrower columns (BORR_FST_NM, BORR_LST_NM, BORR_SSN_LST4) are intentionally dropped;
-- borrower_id is the single source of truth.
INSERT INTO loan_account (
    account_number, borrower_id, product_id, original_amount, current_balance,
    interest_rate, term_months, monthly_payment, origination_date, maturity_date,
    first_payment_date, next_payment_date, status, delinquency_days, escrow_balance,
    ltv_percent, property_address, property_city, property_state, property_zip,
    property_type, appraised_value, created_at, updated_at
)
SELECT
    L.LN_ACCT_NBR,
    (SELECT B.id FROM borrower B WHERE B.external_id = L.BORR_ID),
    (SELECT P.id FROM loan_product P WHERE P.code = L.PROD_CD),
    CASE WHEN L.LN_ORIG_AMT IS NULL THEN NULL ELSE CAST(REPLACE(L.LN_ORIG_AMT, ',', '') AS DECIMAL(12,2)) END,
    CASE WHEN L.LN_CURR_BAL IS NULL THEN NULL ELSE CAST(REPLACE(L.LN_CURR_BAL, ',', '') AS DECIMAL(12,2)) END,
    CASE WHEN L.LN_INT_RT IS NULL THEN NULL ELSE CAST(L.LN_INT_RT AS DECIMAL(5,3)) END,
    CASE WHEN L.LN_TERM_MOS IS NULL THEN NULL ELSE CAST(L.LN_TERM_MOS AS INTEGER) END,
    CASE WHEN L.LN_PMT_AMT IS NULL THEN NULL ELSE CAST(REPLACE(L.LN_PMT_AMT, ',', '') AS DECIMAL(10,2)) END,
    CASE WHEN L.LN_ORIG_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(L.LN_ORIG_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN L.LN_MAT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(L.LN_MAT_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN L.LN_1ST_PMT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(L.LN_1ST_PMT_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN L.LN_NXT_PMT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(L.LN_NXT_PMT_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE L.LN_STAT_CD
        WHEN 'ACT' THEN 'ACTIVE'
        WHEN 'CLO' THEN 'CLOSED'
        WHEN 'DFT' THEN 'DEFAULT'
        WHEN 'FRB' THEN 'FORBEARANCE'
        ELSE L.LN_STAT_CD
    END,
    CASE WHEN L.LN_DLQ_DAYS IS NULL THEN 0 ELSE CAST(L.LN_DLQ_DAYS AS INTEGER) END,
    CASE WHEN L.LN_ESCROW_BAL IS NULL THEN 0 ELSE CAST(REPLACE(L.LN_ESCROW_BAL, ',', '') AS DECIMAL(10,2)) END,
    CASE WHEN L.LN_LTV_PCT IS NULL THEN NULL ELSE CAST(L.LN_LTV_PCT AS DECIMAL(5,2)) END,
    L.PROP_ADDR_LN1,
    L.PROP_CTY_NM,
    L.PROP_ST_CD,
    L.PROP_ZIP_CD,
    CASE L.PROP_TYP_CD
        WHEN 'SFR' THEN 'Single Family'
        WHEN 'CND' THEN 'Condominium'
        WHEN 'MFR' THEN 'Multi-Family'
        WHEN 'TWN' THEN 'Townhouse'
        ELSE L.PROP_TYP_CD
    END,
    CASE WHEN L.PROP_APRS_VAL IS NULL THEN NULL ELSE CAST(REPLACE(L.PROP_APRS_VAL, ',', '') AS DECIMAL(12,2)) END,
    CASE WHEN L.LN_CRET_DT IS NULL THEN CURRENT_TIMESTAMP ELSE CAST(PARSEDATETIME(L.LN_CRET_DT, 'MM/dd/yyyy') AS TIMESTAMP) END,
    CASE WHEN L.LN_UPDT_DT IS NULL THEN CURRENT_TIMESTAMP ELSE CAST(PARSEDATETIME(L.LN_UPDT_DT, 'MM/dd/yyyy') AS TIMESTAMP) END
FROM CDW_LN_ACCT L;

INSERT INTO payment (
    external_id, loan_account_id, payment_date, total_amount, principal_amount,
    interest_amount, escrow_amount, late_fee, type, status,
    received_date, processed_date, created_at, updated_at
)
SELECT
    H.PMT_SEQ_NBR,
    (SELECT A.id FROM loan_account A WHERE A.account_number = H.LN_ACCT_NBR),
    CAST(PARSEDATETIME(H.PMT_DT, 'MM/dd/yyyy') AS DATE),
    CAST(REPLACE(H.PMT_AMT, ',', '') AS DECIMAL(10,2)),
    CASE WHEN H.PMT_PRIN_AMT IS NULL THEN NULL ELSE CAST(REPLACE(H.PMT_PRIN_AMT, ',', '') AS DECIMAL(10,2)) END,
    CASE WHEN H.PMT_INT_AMT IS NULL THEN NULL ELSE CAST(REPLACE(H.PMT_INT_AMT, ',', '') AS DECIMAL(10,2)) END,
    CASE WHEN H.PMT_ESCROW_AMT IS NULL THEN NULL ELSE CAST(REPLACE(H.PMT_ESCROW_AMT, ',', '') AS DECIMAL(10,2)) END,
    CASE WHEN H.PMT_LATE_FEE IS NULL THEN 0 ELSE CAST(REPLACE(H.PMT_LATE_FEE, ',', '') AS DECIMAL(10,2)) END,
    CASE H.PMT_TYP_CD
        WHEN 'REG' THEN 'REGULAR'
        WHEN 'EXT' THEN 'EXTRA'
        WHEN 'PRT' THEN 'PARTIAL'
        WHEN 'PRE' THEN 'PREPAYMENT'
        ELSE H.PMT_TYP_CD
    END,
    CASE H.PMT_STAT_CD
        WHEN 'PST' THEN 'POSTED'
        WHEN 'REV' THEN 'REVERSED'
        WHEN 'NSF' THEN 'NSF'
        WHEN 'PND' THEN 'PENDING'
        ELSE H.PMT_STAT_CD
    END,
    CASE WHEN H.PMT_RECV_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(H.PMT_RECV_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN H.PMT_PROC_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(H.PMT_PROC_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN H.PMT_CRET_DT IS NULL THEN CURRENT_TIMESTAMP ELSE CAST(PARSEDATETIME(H.PMT_CRET_DT, 'MM/dd/yyyy') AS TIMESTAMP) END,
    CASE WHEN H.PMT_UPDT_DT IS NULL THEN CURRENT_TIMESTAMP ELSE CAST(PARSEDATETIME(H.PMT_UPDT_DT, 'MM/dd/yyyy') AS TIMESTAMP) END
FROM CDW_PMT_HIST H;
