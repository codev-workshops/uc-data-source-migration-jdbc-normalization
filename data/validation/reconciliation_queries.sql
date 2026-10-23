-- =============================================================================
-- LEGACY vs MODERN RECONCILIATION QUERIES
-- =============================================================================
-- The legacy CDW tables and the modern schema live in separate databases, so every check is a
-- PAIR of queries: the "@legacy" query runs against legacyDataSource (jdbc:h2:mem:legacydw), the
-- "@modern" query against modernDataSource (jdbc:h2:mem:modern). Both return the same columns;
-- a check passes when both result sets contain the same rows (order-insensitive, numbers compared
-- by value, so 285000 equals 285000.00). Any row present on only one side is a mismatch.
--
-- Legacy values are normalised the way DataMigrationService does it: keys and codes TRIMmed,
-- amounts stripped of thousands separators and CAST to DECIMAL (blank -> NULL), MM/DD/YYYY
-- dates rearranged to ISO YYYY-MM-DD. Sums are COALESCEd to 0 because the modern
-- escrow_balance/late_fee columns default blank legacy values to 0.
--
-- Run them all with ReconciliationQueriesTest (src/test/java/.../validation), or paste a pair
-- into the H2 console of each database (dev profile).
-- Format: "-- @check <name>", then "-- @legacy" and "-- @modern", each followed by one query.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Row counts per table
-- -----------------------------------------------------------------------------

-- @check row_counts
-- @legacy
SELECT 'borrowers' AS entity, COUNT(*) AS row_count FROM CDW_BORR_MSTR
UNION ALL SELECT 'loan_products', COUNT(*) FROM CDW_LN_PROD
UNION ALL SELECT 'loan_accounts', COUNT(*) FROM CDW_LN_ACCT
UNION ALL SELECT 'payments', COUNT(*) FROM CDW_PMT_HIST;
-- @modern
SELECT 'borrowers' AS entity, COUNT(*) AS row_count FROM borrowers
UNION ALL SELECT 'loan_products', COUNT(*) FROM loan_products
UNION ALL SELECT 'loan_accounts', COUNT(*) FROM loan_accounts
UNION ALL SELECT 'payments', COUNT(*) FROM payments;


-- -----------------------------------------------------------------------------
-- 2. Monetary sums (legacy VARCHAR amounts: strip commas, CAST to DECIMAL)
-- -----------------------------------------------------------------------------

-- @check borrower_income_total
-- @legacy
SELECT COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(BORR_ANN_INCM), ',', ''), '') AS DECIMAL(15, 2))), 0) AS annual_income
FROM CDW_BORR_MSTR;
-- @modern
SELECT COALESCE(SUM(annual_income), 0) AS annual_income
FROM borrowers;

-- @check loan_amount_totals
-- @legacy
SELECT COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(LN_ORIG_AMT), ',', ''), '') AS DECIMAL(15, 2))), 0)   AS original_amount,
       COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(LN_CURR_BAL), ',', ''), '') AS DECIMAL(15, 2))), 0)   AS current_balance,
       COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(LN_PMT_AMT), ',', ''), '') AS DECIMAL(15, 2))), 0)    AS monthly_payment,
       COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(LN_ESCROW_BAL), ',', ''), '') AS DECIMAL(15, 2))), 0) AS escrow_balance
FROM CDW_LN_ACCT;
-- @modern
SELECT COALESCE(SUM(original_amount), 0) AS original_amount,
       COALESCE(SUM(current_balance), 0) AS current_balance,
       COALESCE(SUM(monthly_payment), 0) AS monthly_payment,
       COALESCE(SUM(escrow_balance), 0)  AS escrow_balance
FROM loan_accounts;

-- @check payment_amount_totals
-- @legacy
SELECT COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(PMT_AMT), ',', ''), '') AS DECIMAL(15, 2))), 0)        AS total_amount,
       COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(PMT_PRIN_AMT), ',', ''), '') AS DECIMAL(15, 2))), 0)   AS principal_amount,
       COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(PMT_INT_AMT), ',', ''), '') AS DECIMAL(15, 2))), 0)    AS interest_amount,
       COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(PMT_ESCROW_AMT), ',', ''), '') AS DECIMAL(15, 2))), 0) AS escrow_amount,
       COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(PMT_LATE_FEE), ',', ''), '') AS DECIMAL(15, 2))), 0)   AS late_fee
FROM CDW_PMT_HIST;
-- @modern
SELECT COALESCE(SUM(total_amount), 0)     AS total_amount,
       COALESCE(SUM(principal_amount), 0) AS principal_amount,
       COALESCE(SUM(interest_amount), 0)  AS interest_amount,
       COALESCE(SUM(escrow_amount), 0)    AS escrow_amount,
       COALESCE(SUM(late_fee), 0)         AS late_fee
FROM payments;

-- Per-loan payment count and total, to pinpoint which loan a total mismatch comes from
-- @check payment_totals_by_loan
-- @legacy
SELECT TRIM(LN_ACCT_NBR) AS account_number,
       COUNT(*) AS payment_count,
       COALESCE(SUM(CAST(NULLIF(REPLACE(TRIM(PMT_AMT), ',', ''), '') AS DECIMAL(15, 2))), 0) AS total_amount
FROM CDW_PMT_HIST
GROUP BY TRIM(LN_ACCT_NBR);
-- @modern
SELECT la.account_number,
       COUNT(*) AS payment_count,
       COALESCE(SUM(p.total_amount), 0) AS total_amount
FROM payments p
JOIN loan_accounts la ON la.id = p.loan_account_id
GROUP BY la.account_number;

-- Row-level loan values by business key (amounts, rate, origination date)
-- @check loan_values_by_account
-- @legacy
SELECT TRIM(LN_ACCT_NBR) AS account_number,
       CAST(NULLIF(REPLACE(TRIM(LN_ORIG_AMT), ',', ''), '') AS DECIMAL(15, 2)) AS original_amount,
       CAST(NULLIF(REPLACE(TRIM(LN_CURR_BAL), ',', ''), '') AS DECIMAL(15, 2)) AS current_balance,
       CAST(NULLIF(TRIM(LN_INT_RT), '') AS DECIMAL(8, 3))                      AS interest_rate,
       CAST(NULLIF(REPLACE(TRIM(LN_PMT_AMT), ',', ''), '') AS DECIMAL(15, 2))  AS monthly_payment,
       SUBSTRING(TRIM(LN_ORIG_DT), 7, 4) || '-' || SUBSTRING(TRIM(LN_ORIG_DT), 1, 2)
           || '-' || SUBSTRING(TRIM(LN_ORIG_DT), 4, 2)                         AS origination_date
FROM CDW_LN_ACCT;
-- @modern
SELECT account_number,
       original_amount,
       current_balance,
       interest_rate,
       monthly_payment,
       CAST(origination_date AS VARCHAR) AS origination_date
FROM loan_accounts;


-- -----------------------------------------------------------------------------
-- 3. Business keys and FK consistency
--    BORR_ID -> borrowers.external_id, PROD_CD -> loan_products.code,
--    LN_ACCT_NBR -> loan_accounts.account_number, PMT_SEQ_NBR -> payments.external_id
-- -----------------------------------------------------------------------------

-- @check borrower_keys
-- @legacy
SELECT TRIM(BORR_ID) AS external_id FROM CDW_BORR_MSTR;
-- @modern
SELECT external_id FROM borrowers;

-- @check product_keys
-- @legacy
SELECT TRIM(PROD_CD) AS code FROM CDW_LN_PROD;
-- @modern
SELECT code FROM loan_products;

-- Every loan points at the same borrower and product business keys
-- @check loan_foreign_keys
-- @legacy
SELECT TRIM(LN_ACCT_NBR) AS account_number, TRIM(BORR_ID) AS borrower_external_id, TRIM(PROD_CD) AS product_code
FROM CDW_LN_ACCT;
-- @modern
SELECT la.account_number, b.external_id AS borrower_external_id, p.code AS product_code
FROM loan_accounts la
JOIN borrowers b ON b.id = la.borrower_id
JOIN loan_products p ON p.id = la.product_id;

-- Every payment points at the same loan business key
-- @check payment_foreign_keys
-- @legacy
SELECT TRIM(PMT_SEQ_NBR) AS external_id, TRIM(LN_ACCT_NBR) AS account_number
FROM CDW_PMT_HIST;
-- @modern
SELECT p.external_id, la.account_number
FROM payments p
JOIN loan_accounts la ON la.id = p.loan_account_id;

-- Legacy has no FK constraints, so count dangling references; modern FKs guarantee 0
-- @check orphan_references
-- @legacy
SELECT 'loan_without_borrower' AS orphan_type, COUNT(*) AS orphan_count
FROM CDW_LN_ACCT a LEFT JOIN CDW_BORR_MSTR b ON TRIM(b.BORR_ID) = TRIM(a.BORR_ID)
WHERE b.BORR_ID IS NULL
UNION ALL
SELECT 'loan_without_product', COUNT(*)
FROM CDW_LN_ACCT a LEFT JOIN CDW_LN_PROD p ON TRIM(p.PROD_CD) = TRIM(a.PROD_CD)
WHERE p.PROD_CD IS NULL
UNION ALL
SELECT 'payment_without_loan', COUNT(*)
FROM CDW_PMT_HIST p LEFT JOIN CDW_LN_ACCT a ON TRIM(a.LN_ACCT_NBR) = TRIM(p.LN_ACCT_NBR)
WHERE a.LN_ACCT_NBR IS NULL;
-- @modern
SELECT 'loan_without_borrower' AS orphan_type, COUNT(*) AS orphan_count
FROM loan_accounts la LEFT JOIN borrowers b ON b.id = la.borrower_id
WHERE b.id IS NULL
UNION ALL
SELECT 'loan_without_product', COUNT(*)
FROM loan_accounts la LEFT JOIN loan_products p ON p.id = la.product_id
WHERE p.id IS NULL
UNION ALL
SELECT 'payment_without_loan', COUNT(*)
FROM payments p LEFT JOIN loan_accounts la ON la.id = p.loan_account_id
WHERE la.id IS NULL;
