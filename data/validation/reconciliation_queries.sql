-- =============================================================================
-- LEGACY vs MODERN RECONCILIATION QUERIES
-- =============================================================================
-- Validation queries comparing the legacy CDW tables (H2 database `legacydw`)
-- with the modern normalized tables (H2 database `moderndb`) after the
-- legacy-to-modern migration.
--
-- The two schemas live in SEPARATE H2 instances, so each query below is
-- labelled with the datasource it must run against. Where a check needs both
-- sides, run the legacy and modern halves separately and compare the results
-- (or run them via a client connected to both, e.g. H2 linked tables).
--
-- Expected seed-data results: 5 borrowers, 5 products, 5 loan accounts,
-- 10 payments on both sides; all orphan checks return 0 rows.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ROW COUNTS PER TABLE
-- -----------------------------------------------------------------------------

-- [legacy] row counts
SELECT 'CDW_BORR_MSTR' AS table_name, COUNT(*) AS row_count FROM CDW_BORR_MSTR
UNION ALL
SELECT 'CDW_LN_PROD',  COUNT(*) FROM CDW_LN_PROD
UNION ALL
SELECT 'CDW_LN_ACCT',  COUNT(*) FROM CDW_LN_ACCT
UNION ALL
SELECT 'CDW_PMT_HIST', COUNT(*) FROM CDW_PMT_HIST;

-- [modern] row counts (must match the legacy counts above, minus any
-- records the migration reported as skipped-malformed)
SELECT 'borrowers' AS table_name, COUNT(*) AS row_count FROM borrowers
UNION ALL
SELECT 'loan_products', COUNT(*) FROM loan_products
UNION ALL
SELECT 'loan_accounts', COUNT(*) FROM loan_accounts
UNION ALL
SELECT 'payments',      COUNT(*) FROM payments;

-- -----------------------------------------------------------------------------
-- 2. AMOUNT SUMS PER LOAN
-- -----------------------------------------------------------------------------
-- Legacy stores amounts as comma-grouped VARCHAR; strip the commas and cast
-- before summing so the two sides are comparable.

-- [legacy] payment amount sums per loan
SELECT LN_ACCT_NBR                                            AS loan_account,
       SUM(CAST(REPLACE(PMT_AMT,        ',', '') AS DECIMAL(12,2))) AS total_paid,
       SUM(CAST(REPLACE(PMT_PRIN_AMT,   ',', '') AS DECIMAL(12,2))) AS principal_paid,
       SUM(CAST(REPLACE(PMT_INT_AMT,    ',', '') AS DECIMAL(12,2))) AS interest_paid,
       SUM(CAST(REPLACE(PMT_ESCROW_AMT, ',', '') AS DECIMAL(12,2))) AS escrow_paid,
       SUM(CAST(REPLACE(PMT_LATE_FEE,   ',', '') AS DECIMAL(12,2))) AS late_fees,
       COUNT(*)                                               AS payment_count
FROM CDW_PMT_HIST
GROUP BY LN_ACCT_NBR
ORDER BY LN_ACCT_NBR;

-- [modern] payment amount sums per loan (join back to the account number so
-- results line up with the legacy query above)
SELECT la.account_number        AS loan_account,
       SUM(p.total_amount)      AS total_paid,
       SUM(p.principal_amount)  AS principal_paid,
       SUM(p.interest_amount)   AS interest_paid,
       SUM(p.escrow_amount)     AS escrow_paid,
       SUM(p.late_fee)          AS late_fees,
       COUNT(*)                 AS payment_count
FROM payments p
JOIN loan_accounts la ON la.id = p.loan_account_id
GROUP BY la.account_number
ORDER BY la.account_number;

-- [legacy] loan balance figures per account
SELECT LN_ACCT_NBR AS loan_account,
       CAST(REPLACE(LN_ORIG_AMT,  ',', '') AS DECIMAL(12,2)) AS original_amount,
       CAST(REPLACE(LN_CURR_BAL,  ',', '') AS DECIMAL(12,2)) AS current_balance,
       CAST(REPLACE(LN_PMT_AMT,   ',', '') AS DECIMAL(12,2)) AS monthly_payment
FROM CDW_LN_ACCT
ORDER BY LN_ACCT_NBR;

-- [modern] loan balance figures per account (must match the legacy values)
SELECT account_number AS loan_account,
       original_amount,
       current_balance,
       monthly_payment
FROM loan_accounts
ORDER BY account_number;

-- -----------------------------------------------------------------------------
-- 3. ORPHANED FOREIGN KEY CHECKS
-- -----------------------------------------------------------------------------
-- The modern schema enforces FKs, so these should always return 0 rows there;
-- the legacy schema has NO FK constraints, so orphans are possible and any hit
-- flags a record the migration would have skipped (unresolvable reference).

-- [legacy] loan accounts referencing a borrower that does not exist
SELECT a.LN_ACCT_NBR, a.BORR_ID
FROM CDW_LN_ACCT a
LEFT JOIN CDW_BORR_MSTR b ON b.BORR_ID = a.BORR_ID
WHERE b.BORR_ID IS NULL;

-- [legacy] loan accounts referencing a product that does not exist
SELECT a.LN_ACCT_NBR, a.PROD_CD
FROM CDW_LN_ACCT a
LEFT JOIN CDW_LN_PROD p ON p.PROD_CD = a.PROD_CD
WHERE p.PROD_CD IS NULL;

-- [legacy] payments referencing a loan account that does not exist
SELECT h.PMT_SEQ_NBR, h.LN_ACCT_NBR
FROM CDW_PMT_HIST h
LEFT JOIN CDW_LN_ACCT a ON a.LN_ACCT_NBR = h.LN_ACCT_NBR
WHERE a.LN_ACCT_NBR IS NULL;

-- [modern] defensive orphan checks (FK constraints should make these empty)
SELECT la.id, la.account_number, la.borrower_id
FROM loan_accounts la
LEFT JOIN borrowers b ON b.id = la.borrower_id
WHERE b.id IS NULL;

SELECT la.id, la.account_number, la.product_id
FROM loan_accounts la
LEFT JOIN loan_products lp ON lp.id = la.product_id
WHERE lp.id IS NULL;

SELECT p.id, p.payment_number, p.loan_account_id
FROM payments p
LEFT JOIN loan_accounts la ON la.id = p.loan_account_id
WHERE la.id IS NULL;

-- -----------------------------------------------------------------------------
-- 4. NATURAL-KEY COVERAGE (every legacy key present on the modern side)
-- -----------------------------------------------------------------------------
-- Run the legacy list and modern list and diff them; each pair should be
-- identical.

-- [legacy] natural keys
SELECT BORR_ID FROM CDW_BORR_MSTR ORDER BY BORR_ID;
SELECT PROD_CD FROM CDW_LN_PROD ORDER BY PROD_CD;
SELECT LN_ACCT_NBR FROM CDW_LN_ACCT ORDER BY LN_ACCT_NBR;
SELECT PMT_SEQ_NBR FROM CDW_PMT_HIST ORDER BY PMT_SEQ_NBR;

-- [modern] migrated natural keys
SELECT external_id FROM borrowers ORDER BY external_id;
SELECT code FROM loan_products ORDER BY code;
SELECT account_number FROM loan_accounts ORDER BY account_number;
SELECT payment_number FROM payments ORDER BY payment_number;
