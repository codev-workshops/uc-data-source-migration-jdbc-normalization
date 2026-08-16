-- =============================================================================
-- DATA VALIDATION QUERIES (legacy CDW vs modern normalized)
-- =============================================================================
-- The two schemas live in separate databases, so a single cross-database JOIN is
-- not available. Each check is therefore a *pair* of queries that must return
-- identical result sets: the legacy query converts CDW's VARCHAR values to real
-- types and expands its status codes, so it produces exactly what the modern
-- query reads straight out of typed columns.
--
-- Run them by hand against either database (the H2 console at /h2-console), or
-- let SqlReconciliationService execute the whole file:
--
--     GET /api/admin/reconciliation/sql
--
-- SqlReconciliationService parses each check from three comment markers: a "check"
-- marker naming it, then a "legacy" marker and a "modern" marker introducing the
-- query for each data source (see the pairs below for the exact syntax).
-- Column labels must match between the two, and both must ORDER BY so the
-- comparison is not at the mercy of row order.
-- =============================================================================


-- @check borrowers.row_count
-- @legacy
SELECT COUNT(*) AS row_count FROM CDW_BORR_MSTR;
-- @modern
SELECT COUNT(*) AS row_count FROM borrowers;


-- @check loan_products.row_count
-- @legacy
SELECT COUNT(*) AS row_count FROM CDW_LN_PROD;
-- @modern
SELECT COUNT(*) AS row_count FROM loan_products;


-- @check loan_accounts.row_count
-- @legacy
SELECT COUNT(*) AS row_count FROM CDW_LN_ACCT;
-- @modern
SELECT COUNT(*) AS row_count FROM loan_accounts;


-- @check payments.row_count
-- @legacy
SELECT COUNT(*) AS row_count FROM CDW_PMT_HIST;
-- @modern
SELECT COUNT(*) AS row_count FROM payments;


-- Aggregates catch a whole class of migration bug that counts miss: rows present
-- but with mangled amounts.
-- @check loan_accounts.amount_totals
-- @legacy
SELECT CAST(SUM(CAST(REPLACE(LN_ORIG_AMT, ',', '') AS DECIMAL(16, 2))) AS DECIMAL(16, 2)) AS original_amount,
       CAST(SUM(CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(16, 2))) AS DECIMAL(16, 2)) AS current_balance,
       CAST(SUM(CAST(REPLACE(LN_ESCROW_BAL, ',', '') AS DECIMAL(16, 2))) AS DECIMAL(16, 2)) AS escrow_balance,
       CAST(SUM(CAST(REPLACE(PROP_APRS_VAL, ',', '') AS DECIMAL(16, 2))) AS DECIMAL(16, 2)) AS appraised_value
FROM CDW_LN_ACCT;
-- @modern
SELECT CAST(SUM(original_amount) AS DECIMAL(16, 2)) AS original_amount,
       CAST(SUM(current_balance) AS DECIMAL(16, 2)) AS current_balance,
       CAST(SUM(escrow_balance) AS DECIMAL(16, 2)) AS escrow_balance,
       CAST(SUM(appraised_value) AS DECIMAL(16, 2)) AS appraised_value
FROM loan_accounts;


-- @check payments.amount_totals
-- @legacy
SELECT CAST(SUM(CAST(REPLACE(PMT_AMT, ',', '') AS DECIMAL(16, 2))) AS DECIMAL(16, 2)) AS total_amount,
       CAST(SUM(CAST(REPLACE(PMT_PRIN_AMT, ',', '') AS DECIMAL(16, 2))) AS DECIMAL(16, 2)) AS principal_amount,
       CAST(SUM(CAST(REPLACE(PMT_INT_AMT, ',', '') AS DECIMAL(16, 2))) AS DECIMAL(16, 2)) AS interest_amount,
       CAST(SUM(CAST(REPLACE(PMT_ESCROW_AMT, ',', '') AS DECIMAL(16, 2))) AS DECIMAL(16, 2)) AS escrow_amount,
       CAST(SUM(CAST(REPLACE(PMT_LATE_FEE, ',', '') AS DECIMAL(16, 2))) AS DECIMAL(16, 2)) AS late_fee
FROM CDW_PMT_HIST;
-- @modern
SELECT CAST(SUM(total_amount) AS DECIMAL(16, 2)) AS total_amount,
       CAST(SUM(principal_amount) AS DECIMAL(16, 2)) AS principal_amount,
       CAST(SUM(interest_amount) AS DECIMAL(16, 2)) AS interest_amount,
       CAST(SUM(escrow_amount) AS DECIMAL(16, 2)) AS escrow_amount,
       CAST(SUM(late_fee) AS DECIMAL(16, 2)) AS late_fee
FROM payments;


-- The code-expansion rules, checked in bulk: a wrong mapping shows up as a
-- status present on one side only.
-- @check borrowers.by_status
-- @legacy
SELECT CASE BORR_STAT_CD WHEN 'ACT' THEN 'ACTIVE' WHEN 'INA' THEN 'INACTIVE' ELSE BORR_STAT_CD END AS status,
       COUNT(*) AS row_count
FROM CDW_BORR_MSTR
GROUP BY 1
ORDER BY 1;
-- @modern
SELECT status, COUNT(*) AS row_count
FROM borrowers
GROUP BY status
ORDER BY status;


-- @check loan_accounts.by_status
-- @legacy
SELECT CASE LN_STAT_CD
           WHEN 'ACT' THEN 'ACTIVE'
           WHEN 'CLO' THEN 'CLOSED'
           WHEN 'DFT' THEN 'DEFAULT'
           WHEN 'FRB' THEN 'FORBEARANCE'
           ELSE LN_STAT_CD END AS status,
       COUNT(*) AS row_count
FROM CDW_LN_ACCT
GROUP BY 1
ORDER BY 1;
-- @modern
SELECT status, COUNT(*) AS row_count
FROM loan_accounts
GROUP BY status
ORDER BY status;


-- @check payments.by_type_and_status
-- @legacy
SELECT CASE PMT_TYP_CD
           WHEN 'REG' THEN 'REGULAR'
           WHEN 'EXT' THEN 'EXTRA'
           WHEN 'PRT' THEN 'PARTIAL'
           WHEN 'PRE' THEN 'PREPAYMENT'
           ELSE PMT_TYP_CD END AS type,
       CASE PMT_STAT_CD
           WHEN 'PST' THEN 'POSTED'
           WHEN 'REV' THEN 'REVERSED'
           WHEN 'NSF' THEN 'NSF'
           WHEN 'PND' THEN 'PENDING'
           ELSE PMT_STAT_CD END AS status,
       COUNT(*) AS row_count
FROM CDW_PMT_HIST
GROUP BY 1, 2
ORDER BY 1, 2;
-- @modern
SELECT type, status, COUNT(*) AS row_count
FROM payments
GROUP BY type, status
ORDER BY type, status;


-- Row-level checks: every business key, with the values that were type-converted.
-- @check borrowers.by_key
-- @legacy
SELECT BORR_ID AS external_id,
       BORR_FST_NM AS first_name,
       BORR_LST_NM AS last_name,
       CAST(PARSEDATETIME(BORR_DOB_DT, 'MM/dd/yyyy') AS DATE) AS date_of_birth,
       CAST(BORR_CRDT_SCR AS INTEGER) AS credit_score,
       CAST(REPLACE(BORR_ANN_INCM, ',', '') AS DECIMAL(16, 2)) AS annual_income
FROM CDW_BORR_MSTR
ORDER BY 1;
-- @modern
SELECT external_id,
       first_name,
       last_name,
       date_of_birth,
       credit_score,
       CAST(annual_income AS DECIMAL(16, 2)) AS annual_income
FROM borrowers
ORDER BY external_id;


-- Also proves the surrogate foreign keys resolve back to the right business keys.
-- @check loan_accounts.by_key
-- @legacy
SELECT LN_ACCT_NBR AS account_number,
       BORR_ID AS borrower_external_id,
       PROD_CD AS product_code,
       CAST(REPLACE(LN_ORIG_AMT, ',', '') AS DECIMAL(16, 2)) AS original_amount,
       CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(16, 2)) AS current_balance,
       CAST(LN_INT_RT AS DECIMAL(6, 3)) AS interest_rate,
       CAST(LN_TERM_MOS AS INTEGER) AS term_months,
       CAST(PARSEDATETIME(LN_ORIG_DT, 'MM/dd/yyyy') AS DATE) AS origination_date,
       CAST(PARSEDATETIME(LN_MAT_DT, 'MM/dd/yyyy') AS DATE) AS maturity_date,
       CASE LN_STAT_CD
           WHEN 'ACT' THEN 'ACTIVE'
           WHEN 'CLO' THEN 'CLOSED'
           WHEN 'DFT' THEN 'DEFAULT'
           WHEN 'FRB' THEN 'FORBEARANCE'
           ELSE LN_STAT_CD END AS status,
       CASE PROP_TYP_CD
           WHEN 'SFR' THEN 'Single Family Residence'
           WHEN 'CND' THEN 'Condominium'
           WHEN 'MFR' THEN 'Multi-Family Residence'
           WHEN 'TWN' THEN 'Townhouse'
           ELSE PROP_TYP_CD END AS property_type
FROM CDW_LN_ACCT
ORDER BY 1;
-- @modern
SELECT la.account_number,
       b.external_id AS borrower_external_id,
       p.code AS product_code,
       CAST(la.original_amount AS DECIMAL(16, 2)) AS original_amount,
       CAST(la.current_balance AS DECIMAL(16, 2)) AS current_balance,
       CAST(la.interest_rate AS DECIMAL(6, 3)) AS interest_rate,
       la.term_months,
       la.origination_date,
       la.maturity_date,
       la.status,
       la.property_type
FROM loan_accounts la
JOIN borrowers b ON b.id = la.borrower_id
JOIN loan_products p ON p.id = la.product_id
ORDER BY la.account_number;


-- @check payments.by_key
-- @legacy
SELECT PMT_SEQ_NBR AS external_id,
       LN_ACCT_NBR AS account_number,
       CAST(PARSEDATETIME(PMT_DT, 'MM/dd/yyyy') AS DATE) AS payment_date,
       CAST(REPLACE(PMT_AMT, ',', '') AS DECIMAL(16, 2)) AS total_amount,
       CAST(REPLACE(PMT_PRIN_AMT, ',', '') AS DECIMAL(16, 2)) AS principal_amount,
       CAST(REPLACE(PMT_INT_AMT, ',', '') AS DECIMAL(16, 2)) AS interest_amount,
       CAST(REPLACE(PMT_LATE_FEE, ',', '') AS DECIMAL(16, 2)) AS late_fee,
       CASE PMT_TYP_CD
           WHEN 'REG' THEN 'REGULAR'
           WHEN 'EXT' THEN 'EXTRA'
           WHEN 'PRT' THEN 'PARTIAL'
           WHEN 'PRE' THEN 'PREPAYMENT'
           ELSE PMT_TYP_CD END AS type,
       CASE PMT_STAT_CD
           WHEN 'PST' THEN 'POSTED'
           WHEN 'REV' THEN 'REVERSED'
           WHEN 'NSF' THEN 'NSF'
           WHEN 'PND' THEN 'PENDING'
           ELSE PMT_STAT_CD END AS status
FROM CDW_PMT_HIST
ORDER BY 1;
-- @modern
SELECT pm.external_id,
       la.account_number,
       pm.payment_date,
       CAST(pm.total_amount AS DECIMAL(16, 2)) AS total_amount,
       CAST(pm.principal_amount AS DECIMAL(16, 2)) AS principal_amount,
       CAST(pm.interest_amount AS DECIMAL(16, 2)) AS interest_amount,
       CAST(pm.late_fee AS DECIMAL(16, 2)) AS late_fee,
       pm.type,
       pm.status
FROM payments pm
JOIN loan_accounts la ON la.id = pm.loan_account_id
ORDER BY pm.external_id;


-- Referential integrity. CDW has no foreign keys, so this is the one check whose
-- point is that both sides report zero: any legacy orphan would have been
-- rejected by the migration instead of silently landing in the modern schema.
-- @check loan_accounts.orphans
-- @legacy
SELECT COUNT(*) AS orphan_count
FROM CDW_LN_ACCT a
WHERE NOT EXISTS (SELECT 1 FROM CDW_BORR_MSTR b WHERE b.BORR_ID = a.BORR_ID)
   OR NOT EXISTS (SELECT 1 FROM CDW_LN_PROD p WHERE p.PROD_CD = a.PROD_CD);
-- @modern
SELECT COUNT(*) AS orphan_count
FROM loan_accounts la
WHERE NOT EXISTS (SELECT 1 FROM borrowers b WHERE b.id = la.borrower_id)
   OR NOT EXISTS (SELECT 1 FROM loan_products p WHERE p.id = la.product_id);


-- @check payments.orphans
-- @legacy
SELECT COUNT(*) AS orphan_count
FROM CDW_PMT_HIST h
WHERE NOT EXISTS (SELECT 1 FROM CDW_LN_ACCT a WHERE a.LN_ACCT_NBR = h.LN_ACCT_NBR);
-- @modern
SELECT COUNT(*) AS orphan_count
FROM payments pm
WHERE NOT EXISTS (SELECT 1 FROM loan_accounts la WHERE la.id = pm.loan_account_id);


-- Denormalization check, legacy-only in spirit: CDW_LN_ACCT repeats the borrower
-- name, so it can disagree with the master. The modern schema cannot, because the
-- name lives in exactly one place — which is the whole point of the migration.
-- @check loan_accounts.denormalized_name_conflicts
-- @legacy
SELECT COUNT(*) AS conflict_count
FROM CDW_LN_ACCT a
JOIN CDW_BORR_MSTR b ON b.BORR_ID = a.BORR_ID
WHERE a.BORR_FST_NM <> b.BORR_FST_NM
   OR a.BORR_LST_NM <> b.BORR_LST_NM;
-- @modern
SELECT CAST(0 AS BIGINT) AS conflict_count;
