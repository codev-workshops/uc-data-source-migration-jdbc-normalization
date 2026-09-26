-- =============================================================================
-- DATA MIGRATION: legacy CDW_* tables -> normalized tables
-- =============================================================================
-- Applies the same conversions the service performs at runtime:
--   - amounts: strip thousands separators, cast to DECIMAL
--   - dates:   MM/DD/YYYY strings parsed to DATE (NULL-safe)
--   - numbers: credit score / terms / delinquent days cast to INTEGER
-- Status and type codes are carried over verbatim.
-- =============================================================================

INSERT INTO borrower (
    borrower_id, first_name, last_name, middle_initial, ssn_encrypted, date_of_birth,
    address_line1, address_line2, city, state_code, zip_code, phone_number, email,
    credit_score, employment_status, annual_income, created_date, updated_date,
    status_code, record_type
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
    CASE WHEN BORR_ANN_INCM IS NULL THEN NULL ELSE CAST(REPLACE(BORR_ANN_INCM, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN BORR_CRET_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(BORR_CRET_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN BORR_UPDT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(BORR_UPDT_DT, 'MM/dd/yyyy') AS DATE) END,
    BORR_STAT_CD,
    BORR_REC_TYP
FROM CDW_BORR_MSTR;

INSERT INTO loan_product (
    product_code, description, product_type, term_months, rate_type,
    min_amount, max_amount, status_code, effective_date, expiration_date
)
SELECT
    PROD_CD,
    PROD_DESC_TXT,
    PROD_TYP_CD,
    CASE WHEN PROD_TERM_MOS IS NULL THEN NULL ELSE CAST(PROD_TERM_MOS AS INTEGER) END,
    PROD_RT_TYP,
    CASE WHEN PROD_MIN_AMT IS NULL THEN NULL ELSE CAST(REPLACE(PROD_MIN_AMT, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN PROD_MAX_AMT IS NULL THEN NULL ELSE CAST(REPLACE(PROD_MAX_AMT, ',', '') AS DECIMAL(15,2)) END,
    PROD_STAT_CD,
    CASE WHEN PROD_EFF_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(PROD_EFF_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN PROD_EXP_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(PROD_EXP_DT, 'MM/dd/yyyy') AS DATE) END
FROM CDW_LN_PROD;

-- Embedded borrower columns (BORR_FST_NM, BORR_LST_NM, BORR_SSN_LST4) are intentionally dropped;
-- borrower_id is the single source of truth.
INSERT INTO loan_account (
    loan_account_number, borrower_id, product_code, original_amount, current_balance,
    interest_rate, term_months, monthly_payment, origination_date, maturity_date,
    first_payment_date, next_payment_date, status_code, delinquent_days, escrow_balance,
    ltv_percent, property_address_line1, property_city, property_state, property_zip,
    property_type, property_appraised_value, created_date, updated_date
)
SELECT
    LN_ACCT_NBR,
    BORR_ID,
    PROD_CD,
    CASE WHEN LN_ORIG_AMT IS NULL THEN NULL ELSE CAST(REPLACE(LN_ORIG_AMT, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN LN_CURR_BAL IS NULL THEN NULL ELSE CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN LN_INT_RT IS NULL THEN NULL ELSE CAST(LN_INT_RT AS DECIMAL(6,3)) END,
    CASE WHEN LN_TERM_MOS IS NULL THEN NULL ELSE CAST(LN_TERM_MOS AS INTEGER) END,
    CASE WHEN LN_PMT_AMT IS NULL THEN NULL ELSE CAST(REPLACE(LN_PMT_AMT, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN LN_ORIG_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(LN_ORIG_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN LN_MAT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(LN_MAT_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN LN_1ST_PMT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(LN_1ST_PMT_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN LN_NXT_PMT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(LN_NXT_PMT_DT, 'MM/dd/yyyy') AS DATE) END,
    LN_STAT_CD,
    CASE WHEN LN_DLQ_DAYS IS NULL THEN NULL ELSE CAST(LN_DLQ_DAYS AS INTEGER) END,
    CASE WHEN LN_ESCROW_BAL IS NULL THEN NULL ELSE CAST(REPLACE(LN_ESCROW_BAL, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN LN_LTV_PCT IS NULL THEN NULL ELSE CAST(LN_LTV_PCT AS DECIMAL(6,2)) END,
    PROP_ADDR_LN1,
    PROP_CTY_NM,
    PROP_ST_CD,
    PROP_ZIP_CD,
    PROP_TYP_CD,
    CASE WHEN PROP_APRS_VAL IS NULL THEN NULL ELSE CAST(REPLACE(PROP_APRS_VAL, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN LN_CRET_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(LN_CRET_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN LN_UPDT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(LN_UPDT_DT, 'MM/dd/yyyy') AS DATE) END
FROM CDW_LN_ACCT;

INSERT INTO payment (
    payment_id, loan_account_number, payment_date, total_amount, principal_amount,
    interest_amount, escrow_amount, late_fee, type_code, status_code,
    received_date, processed_date, created_date, updated_date
)
SELECT
    PMT_SEQ_NBR,
    LN_ACCT_NBR,
    CASE WHEN PMT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(PMT_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN PMT_AMT IS NULL THEN NULL ELSE CAST(REPLACE(PMT_AMT, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN PMT_PRIN_AMT IS NULL THEN NULL ELSE CAST(REPLACE(PMT_PRIN_AMT, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN PMT_INT_AMT IS NULL THEN NULL ELSE CAST(REPLACE(PMT_INT_AMT, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN PMT_ESCROW_AMT IS NULL THEN NULL ELSE CAST(REPLACE(PMT_ESCROW_AMT, ',', '') AS DECIMAL(15,2)) END,
    CASE WHEN PMT_LATE_FEE IS NULL THEN NULL ELSE CAST(REPLACE(PMT_LATE_FEE, ',', '') AS DECIMAL(15,2)) END,
    PMT_TYP_CD,
    PMT_STAT_CD,
    CASE WHEN PMT_RECV_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(PMT_RECV_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN PMT_PROC_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(PMT_PROC_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN PMT_CRET_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(PMT_CRET_DT, 'MM/dd/yyyy') AS DATE) END,
    CASE WHEN PMT_UPDT_DT IS NULL THEN NULL ELSE CAST(PARSEDATETIME(PMT_UPDT_DT, 'MM/dd/yyyy') AS DATE) END
FROM CDW_PMT_HIST;
