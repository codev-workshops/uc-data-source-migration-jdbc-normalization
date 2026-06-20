-- =============================================================================
-- RECONCILIATION QUERIES: legacy CDW vs modern normalized schema
-- =============================================================================
-- The two schemas live in separate H2 in-memory databases
-- (jdbc:h2:mem:legacydw and jdbc:h2:mem:moderndb), so these queries are run
-- per-database and their outputs compared. Sections marked [LEGACY] run against
-- legacydw; sections marked [MODERN] run against moderndb. Legacy amounts are
-- stored as VARCHAR with thousands separators, so they are cleaned + cast before
-- aggregation; modern columns are already typed.
--
-- To open an H2 console against the running app, enable spring.h2.console and
-- point the JDBC URL at the database above, or run via any H2 client.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. ROW COUNTS  (expect equal counts on both sides)
-- -----------------------------------------------------------------------------
-- [LEGACY]
SELECT 'borrowers' AS entity, COUNT(*) AS n FROM CDW_BORR_MSTR
UNION ALL SELECT 'products',  COUNT(*) FROM CDW_LN_PROD
UNION ALL SELECT 'loans',     COUNT(*) FROM CDW_LN_ACCT
UNION ALL SELECT 'payments',  COUNT(*) FROM CDW_PMT_HIST;

-- [MODERN]
SELECT 'borrowers' AS entity, COUNT(*) AS n FROM borrowers
UNION ALL SELECT 'products',  COUNT(*) FROM loan_products
UNION ALL SELECT 'loans',     COUNT(*) FROM loan_accounts
UNION ALL SELECT 'payments',  COUNT(*) FROM payments;


-- -----------------------------------------------------------------------------
-- 2. MONETARY TOTALS  (exact decimal reconciliation; totals must match)
-- -----------------------------------------------------------------------------
-- [LEGACY] strip thousands separators, then cast to DECIMAL
SELECT
    SUM(CAST(REPLACE(LN_ORIG_AMT, ',', '') AS DECIMAL(15,2)))  AS sum_original,
    SUM(CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2)))  AS sum_balance,
    SUM(CAST(REPLACE(LN_PMT_AMT,  ',', '') AS DECIMAL(15,2)))  AS sum_monthly
FROM CDW_LN_ACCT;

-- [MODERN]
SELECT
    SUM(original_amount) AS sum_original,
    SUM(current_balance) AS sum_balance,
    SUM(monthly_payment) AS sum_monthly
FROM loan_accounts;

-- [LEGACY] payment totals
SELECT SUM(CAST(REPLACE(PMT_AMT, ',', '') AS DECIMAL(15,2))) AS sum_payments
FROM CDW_PMT_HIST;

-- [MODERN] payment totals
SELECT SUM(total_amount) AS sum_payments FROM payments;


-- -----------------------------------------------------------------------------
-- 3. BUSINESS-KEY COVERAGE  (every legacy key must exist exactly once in modern)
-- -----------------------------------------------------------------------------
-- [LEGACY] distinct business keys
SELECT COUNT(DISTINCT BORR_ID)     AS borrower_keys FROM CDW_BORR_MSTR;
SELECT COUNT(DISTINCT LN_ACCT_NBR) AS loan_keys     FROM CDW_LN_ACCT;
SELECT COUNT(DISTINCT PMT_SEQ_NBR) AS payment_keys  FROM CDW_PMT_HIST;

-- [MODERN] distinct business keys (external_id preserves PMT_SEQ_NBR)
SELECT COUNT(DISTINCT external_id)    AS borrower_keys FROM borrowers;
SELECT COUNT(DISTINCT account_number) AS loan_keys     FROM loan_accounts;
SELECT COUNT(DISTINCT external_id)    AS payment_keys  FROM payments;


-- -----------------------------------------------------------------------------
-- 4. FOREIGN-KEY INTEGRITY  (modern only; all must return 0 rows)
-- -----------------------------------------------------------------------------
-- [MODERN] loan accounts with a dangling borrower or product
SELECT la.account_number
FROM loan_accounts la
LEFT JOIN borrowers b     ON b.id = la.borrower_id
LEFT JOIN loan_products p ON p.id = la.product_id
WHERE b.id IS NULL OR p.id IS NULL;

-- [MODERN] payments with a dangling loan account
SELECT pm.external_id
FROM payments pm
LEFT JOIN loan_accounts la ON la.id = pm.loan_account_id
WHERE la.id IS NULL;


-- -----------------------------------------------------------------------------
-- 5. STATUS / CODE DISTRIBUTION  (legacy codes vs migrated values)
-- -----------------------------------------------------------------------------
-- [LEGACY] raw loan status codes (e.g. ACT, CLO, DFT, FRB)
SELECT LN_STAT_CD AS status, COUNT(*) AS n FROM CDW_LN_ACCT GROUP BY LN_STAT_CD;

-- [MODERN] migrated loan statuses (e.g. ACTIVE, CLOSED, DEFAULT, FORBEARANCE)
SELECT status, COUNT(*) AS n FROM loan_accounts GROUP BY status;


-- -----------------------------------------------------------------------------
-- 6. PER-ROW SPOT CHECK  (join the two extracts in your client and diff)
-- -----------------------------------------------------------------------------
-- [LEGACY]
SELECT LN_ACCT_NBR AS k,
       CAST(REPLACE(LN_ORIG_AMT, ',', '') AS DECIMAL(15,2)) AS original_amount,
       CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2)) AS current_balance,
       LN_STAT_CD AS status_code
FROM CDW_LN_ACCT ORDER BY LN_ACCT_NBR;

-- [MODERN]
SELECT account_number AS k, original_amount, current_balance, status
FROM loan_accounts ORDER BY account_number;
