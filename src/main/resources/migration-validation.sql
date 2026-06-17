-- =============================================================================
-- MIGRATION VALIDATION QUERIES
-- =============================================================================
-- Compare legacy vs modern data to verify migration correctness.
-- =============================================================================

-- Row count checks
SELECT 'borrowers' AS tbl,
       (SELECT COUNT(*) FROM CDW_BORR_MSTR) AS legacy_count,
       (SELECT COUNT(*) FROM borrowers) AS modern_count,
       CASE WHEN (SELECT COUNT(*) FROM CDW_BORR_MSTR) = (SELECT COUNT(*) FROM borrowers) THEN 'PASS' ELSE 'FAIL' END AS result;

SELECT 'loan_products' AS tbl,
       (SELECT COUNT(*) FROM CDW_LN_PROD) AS legacy_count,
       (SELECT COUNT(*) FROM loan_products) AS modern_count,
       CASE WHEN (SELECT COUNT(*) FROM CDW_LN_PROD) = (SELECT COUNT(*) FROM loan_products) THEN 'PASS' ELSE 'FAIL' END AS result;

SELECT 'loan_accounts' AS tbl,
       (SELECT COUNT(*) FROM CDW_LN_ACCT) AS legacy_count,
       (SELECT COUNT(*) FROM loan_accounts) AS modern_count,
       CASE WHEN (SELECT COUNT(*) FROM CDW_LN_ACCT) = (SELECT COUNT(*) FROM loan_accounts) THEN 'PASS' ELSE 'FAIL' END AS result;

SELECT 'payments' AS tbl,
       (SELECT COUNT(*) FROM CDW_PMT_HIST) AS legacy_count,
       (SELECT COUNT(*) FROM payments) AS modern_count,
       CASE WHEN (SELECT COUNT(*) FROM CDW_PMT_HIST) = (SELECT COUNT(*) FROM payments) THEN 'PASS' ELSE 'FAIL' END AS result;

-- Data integrity checks: verify borrower fields match after conversion
SELECT b.external_id,
       l.BORR_ID,
       b.first_name,
       l.BORR_FST_NM,
       CASE WHEN b.first_name = l.BORR_FST_NM THEN 'PASS' ELSE 'FAIL' END AS name_check
FROM borrowers b
JOIN CDW_BORR_MSTR l ON b.external_id = l.BORR_ID;

-- Verify amounts: compare modern DECIMAL to legacy parsed VARCHAR
SELECT la.account_number,
       la.original_amount,
       CAST(REPLACE(ll.LN_ORIG_AMT, ',', '') AS DECIMAL(12,2)) AS legacy_parsed,
       CASE WHEN la.original_amount = CAST(REPLACE(ll.LN_ORIG_AMT, ',', '') AS DECIMAL(12,2)) THEN 'PASS' ELSE 'FAIL' END AS amount_check
FROM loan_accounts la
JOIN CDW_LN_ACCT ll ON la.account_number = ll.LN_ACCT_NBR;

-- Verify FK integrity
SELECT la.account_number, la.borrower_id, b.external_id, ll.BORR_ID,
       CASE WHEN b.external_id = ll.BORR_ID THEN 'PASS' ELSE 'FAIL' END AS fk_check
FROM loan_accounts la
JOIN borrowers b ON la.borrower_id = b.id
JOIN CDW_LN_ACCT ll ON la.account_number = ll.LN_ACCT_NBR;

-- Verify date conversions
SELECT b.external_id, b.date_of_birth,
       l.BORR_DOB_DT,
       CASE WHEN b.date_of_birth = PARSEDATETIME(l.BORR_DOB_DT, 'MM/dd/yyyy') THEN 'PASS' ELSE 'FAIL' END AS date_check
FROM borrowers b
JOIN CDW_BORR_MSTR l ON b.external_id = l.BORR_ID;

-- Verify payment FK chain
SELECT p.id, p.loan_account_id, la.account_number, lp.LN_ACCT_NBR,
       CASE WHEN la.account_number = lp.LN_ACCT_NBR THEN 'PASS' ELSE 'FAIL' END AS payment_fk_check
FROM payments p
JOIN loan_accounts la ON p.loan_account_id = la.id
JOIN CDW_PMT_HIST lp ON p.legacy_payment_id = lp.PMT_SEQ_NBR;
