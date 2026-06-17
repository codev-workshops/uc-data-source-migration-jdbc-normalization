-- =============================================================================
-- DATA MIGRATION: Legacy (CDW) → Modern Schema
-- =============================================================================
-- INSERT-SELECT statements that transfer data from legacy tables to modern
-- tables with all necessary type conversions and transformations.
-- =============================================================================

-- 1. Migrate borrowers from CDW_BORR_MSTR
INSERT INTO borrowers (external_id, first_name, last_name, middle_initial, ssn_hash,
    date_of_birth, address_line1, address_line2, city, state, zip_code,
    phone, email, credit_score, employment_status, annual_income, status,
    created_at, updated_at)
SELECT
    BORR_ID,
    BORR_FST_NM,
    BORR_LST_NM,
    BORR_MID_INIT,
    BORR_SSN_ENCR,
    PARSEDATETIME(BORR_DOB_DT, 'MM/dd/yyyy'),
    BORR_ADDR_LN1,
    BORR_ADDR_LN2,
    BORR_CTY_NM,
    BORR_ST_CD,
    BORR_ZIP_CD,
    BORR_PH_NBR,
    BORR_EMAIL_ADDR,
    CAST(BORR_CRDT_SCR AS INTEGER),
    BORR_EMP_STAT,
    CAST(REPLACE(BORR_ANN_INCM, ',', '') AS DECIMAL(12,2)),
    CASE BORR_STAT_CD
        WHEN 'ACT' THEN 'ACTIVE'
        WHEN 'INA' THEN 'INACTIVE'
        ELSE BORR_STAT_CD
    END,
    PARSEDATETIME(BORR_CRET_DT, 'MM/dd/yyyy'),
    PARSEDATETIME(BORR_UPDT_DT, 'MM/dd/yyyy')
FROM CDW_BORR_MSTR;

-- 2. Migrate loan products from CDW_LN_PROD
INSERT INTO loan_products (code, name, type, term_months, rate_type,
    min_amount, max_amount, is_active, effective_date, expiration_date)
SELECT
    PROD_CD,
    PROD_DESC_TXT,
    PROD_TYP_CD,
    CAST(PROD_TERM_MOS AS INTEGER),
    PROD_RT_TYP,
    CAST(REPLACE(PROD_MIN_AMT, ',', '') AS DECIMAL(12,2)),
    CAST(REPLACE(PROD_MAX_AMT, ',', '') AS DECIMAL(12,2)),
    CASE PROD_STAT_CD
        WHEN 'ACT' THEN TRUE
        WHEN 'INA' THEN FALSE
        ELSE TRUE
    END,
    PARSEDATETIME(PROD_EFF_DT, 'MM/dd/yyyy'),
    PARSEDATETIME(PROD_EXP_DT, 'MM/dd/yyyy')
FROM CDW_LN_PROD;

-- 3. Migrate loan accounts from CDW_LN_ACCT
INSERT INTO loan_accounts (account_number, borrower_id, product_id,
    original_amount, current_balance, interest_rate, term_months,
    monthly_payment, origination_date, maturity_date, first_payment_date,
    next_payment_date, status, delinquency_days, escrow_balance,
    ltv_percent, property_address, property_city, property_state,
    property_zip, property_type, appraised_value, created_at, updated_at)
SELECT
    LN_ACCT_NBR,
    (SELECT id FROM borrowers WHERE external_id = CDW_LN_ACCT.BORR_ID),
    (SELECT id FROM loan_products WHERE code = CDW_LN_ACCT.PROD_CD),
    CAST(REPLACE(LN_ORIG_AMT, ',', '') AS DECIMAL(12,2)),
    CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(12,2)),
    CAST(LN_INT_RT AS DECIMAL(5,3)),
    CAST(LN_TERM_MOS AS INTEGER),
    CAST(REPLACE(LN_PMT_AMT, ',', '') AS DECIMAL(10,2)),
    PARSEDATETIME(LN_ORIG_DT, 'MM/dd/yyyy'),
    PARSEDATETIME(LN_MAT_DT, 'MM/dd/yyyy'),
    PARSEDATETIME(LN_1ST_PMT_DT, 'MM/dd/yyyy'),
    PARSEDATETIME(LN_NXT_PMT_DT, 'MM/dd/yyyy'),
    CASE LN_STAT_CD
        WHEN 'ACT' THEN 'ACTIVE'
        WHEN 'CLO' THEN 'CLOSED'
        WHEN 'DFT' THEN 'DEFAULT'
        WHEN 'FRB' THEN 'FORBEARANCE'
        ELSE LN_STAT_CD
    END,
    CAST(LN_DLQ_DAYS AS INTEGER),
    CAST(REPLACE(LN_ESCROW_BAL, ',', '') AS DECIMAL(10,2)),
    CAST(LN_LTV_PCT AS DECIMAL(5,2)),
    PROP_ADDR_LN1,
    PROP_CTY_NM,
    PROP_ST_CD,
    PROP_ZIP_CD,
    CASE PROP_TYP_CD
        WHEN 'SFR' THEN 'Single Family'
        WHEN 'CND' THEN 'Condominium'
        WHEN 'MFR' THEN 'Multi-Family'
        WHEN 'TWN' THEN 'Townhouse'
        ELSE PROP_TYP_CD
    END,
    CAST(REPLACE(PROP_APRS_VAL, ',', '') AS DECIMAL(12,2)),
    PARSEDATETIME(LN_CRET_DT, 'MM/dd/yyyy'),
    PARSEDATETIME(LN_UPDT_DT, 'MM/dd/yyyy')
FROM CDW_LN_ACCT;

-- 4. Migrate payments from CDW_PMT_HIST
INSERT INTO payments (legacy_payment_id, loan_account_id, payment_date,
    total_amount, principal_amount, interest_amount, escrow_amount,
    late_fee, type, status, received_date, processed_date,
    created_at, updated_at)
SELECT
    PMT_SEQ_NBR,
    (SELECT id FROM loan_accounts WHERE account_number = CDW_PMT_HIST.LN_ACCT_NBR),
    PARSEDATETIME(PMT_DT, 'MM/dd/yyyy'),
    CAST(REPLACE(PMT_AMT, ',', '') AS DECIMAL(10,2)),
    CAST(REPLACE(PMT_PRIN_AMT, ',', '') AS DECIMAL(10,2)),
    CAST(REPLACE(PMT_INT_AMT, ',', '') AS DECIMAL(10,2)),
    CAST(REPLACE(PMT_ESCROW_AMT, ',', '') AS DECIMAL(10,2)),
    CAST(REPLACE(PMT_LATE_FEE, ',', '') AS DECIMAL(10,2)),
    CASE PMT_TYP_CD
        WHEN 'REG' THEN 'REGULAR'
        WHEN 'EXT' THEN 'EXTRA'
        WHEN 'PRT' THEN 'PARTIAL'
        WHEN 'PRE' THEN 'PREPAYMENT'
        ELSE PMT_TYP_CD
    END,
    CASE PMT_STAT_CD
        WHEN 'PST' THEN 'POSTED'
        WHEN 'REV' THEN 'REVERSED'
        WHEN 'NSF' THEN 'NSF'
        WHEN 'PND' THEN 'PENDING'
        ELSE PMT_STAT_CD
    END,
    PARSEDATETIME(PMT_RECV_DT, 'MM/dd/yyyy'),
    PARSEDATETIME(PMT_PROC_DT, 'MM/dd/yyyy'),
    PARSEDATETIME(PMT_CRET_DT, 'MM/dd/yyyy'),
    PARSEDATETIME(PMT_UPDT_DT, 'MM/dd/yyyy')
FROM CDW_PMT_HIST;
