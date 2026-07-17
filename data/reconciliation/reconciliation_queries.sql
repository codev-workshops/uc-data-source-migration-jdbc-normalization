-- =============================================================================
-- LEGACY vs MODERN RECONCILIATION QUERIES  (Bonus: "Data validation queries")
-- =============================================================================
-- Purpose: independently verify that the ETL (DataMigrationService) moved every
-- legacy CDW row into the modern normalized schema without losing or corrupting
-- data. These queries do NOT depend on the application code — run them straight
-- against the databases so they are a genuine cross-check of the migration.
--
-- The two data sources are separate H2 in-memory databases:
--   legacy : jdbc:h2:mem:legacydw   (CDW_* tables, all VARCHAR)
--   modern : jdbc:h2:mem:moderndw   (normalized, typed tables)
--
-- HOW TO RUN
--   1. Start the app (mvn spring-boot:run) so both in-memory DBs are populated.
--   2. Open the H2 console at http://localhost:8080/h2-console.
--   3. Run the LEGACY block connected to jdbc:h2:mem:legacydw and the MODERN
--      block connected to jdbc:h2:mem:moderndw, then compare the outputs.
--   OR use Section 4 (LINKED TABLE) to reconcile both from a single connection.
--
-- Legacy amounts are strings with thousands separators, so they are normalised
-- with REPLACE(...,',','') and CAST before aggregation; that is exactly the
-- transformation the ETL performs, so equal totals prove the conversion.
-- =============================================================================


-- =============================================================================
-- SECTION 1 — ROW COUNTS  (must be identical: 5 / 5 / 5 / 10)
-- =============================================================================

-- ---- LEGACY (connect to jdbc:h2:mem:legacydw) -------------------------------
SELECT 'borrowers'     AS entity, COUNT(*) AS row_count FROM CDW_BORR_MSTR
UNION ALL SELECT 'loan_products', COUNT(*) FROM CDW_LN_PROD
UNION ALL SELECT 'loan_accounts', COUNT(*) FROM CDW_LN_ACCT
UNION ALL SELECT 'payments',      COUNT(*) FROM CDW_PMT_HIST;

-- ---- MODERN (connect to jdbc:h2:mem:moderndw) -------------------------------
SELECT 'borrowers'     AS entity, COUNT(*) AS row_count FROM borrowers
UNION ALL SELECT 'loan_products', COUNT(*) FROM loan_products
UNION ALL SELECT 'loan_accounts', COUNT(*) FROM loan_accounts
UNION ALL SELECT 'payments',      COUNT(*) FROM payments;


-- =============================================================================
-- SECTION 2 — FINANCIAL TOTALS  (must match to the cent after type conversion)
-- =============================================================================

-- ---- LEGACY -----------------------------------------------------------------
SELECT
    SUM(CAST(REPLACE(LN_ORIG_AMT, ',', '') AS DECIMAL(15,2))) AS total_original_amount,
    SUM(CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2))) AS total_current_balance
FROM CDW_LN_ACCT;

SELECT
    SUM(CAST(REPLACE(PMT_AMT,      ',', '') AS DECIMAL(15,2))) AS total_payments,
    SUM(CAST(REPLACE(PMT_PRIN_AMT, ',', '') AS DECIMAL(15,2))) AS total_principal,
    SUM(CAST(REPLACE(PMT_INT_AMT,  ',', '') AS DECIMAL(15,2))) AS total_interest
FROM CDW_PMT_HIST;

-- ---- MODERN -----------------------------------------------------------------
SELECT
    SUM(original_amount) AS total_original_amount,
    SUM(current_balance) AS total_current_balance
FROM loan_accounts;

SELECT
    SUM(total_amount)     AS total_payments,
    SUM(principal_amount) AS total_principal,
    SUM(interest_amount)  AS total_interest
FROM payments;


-- =============================================================================
-- SECTION 3 — BUSINESS-KEY SETS  (every legacy key must exist on the modern side)
-- =============================================================================
-- Run each list on its own side and diff the (sorted) results; they must be
-- identical. Section 4 turns this into an automatic set-difference.

-- ---- LEGACY -----------------------------------------------------------------
SELECT BORR_ID     AS borrower_key      FROM CDW_BORR_MSTR ORDER BY 1;
SELECT PROD_CD     AS product_key       FROM CDW_LN_PROD   ORDER BY 1;
SELECT LN_ACCT_NBR AS loan_account_key  FROM CDW_LN_ACCT   ORDER BY 1;
SELECT PMT_SEQ_NBR AS payment_key       FROM CDW_PMT_HIST  ORDER BY 1;

-- ---- MODERN -----------------------------------------------------------------
SELECT external_id            AS borrower_key     FROM borrowers     ORDER BY 1;
SELECT code                   AS product_key      FROM loan_products ORDER BY 1;
SELECT account_number         AS loan_account_key FROM loan_accounts ORDER BY 1;
SELECT legacy_sequence_number AS payment_key      FROM payments      ORDER BY 1;


-- =============================================================================
-- SECTION 4 — SINGLE-CONNECTION RECONCILIATION VIA H2 LINKED TABLES (optional)
-- =============================================================================
-- Connect to the MODERN database and expose the legacy tables as linked tables,
-- then every check above becomes one query returning the differences directly.
-- Any non-empty result (or non-zero delta) indicates a reconciliation failure.

CREATE LINKED TABLE L_BORR ('org.h2.Driver', 'jdbc:h2:mem:legacydw', 'sa', '', 'CDW_BORR_MSTR');
CREATE LINKED TABLE L_PROD ('org.h2.Driver', 'jdbc:h2:mem:legacydw', 'sa', '', 'CDW_LN_PROD');
CREATE LINKED TABLE L_ACCT ('org.h2.Driver', 'jdbc:h2:mem:legacydw', 'sa', '', 'CDW_LN_ACCT');
CREATE LINKED TABLE L_PMT  ('org.h2.Driver', 'jdbc:h2:mem:legacydw', 'sa', '', 'CDW_PMT_HIST');

-- 4a. Row-count deltas (all zeros = OK).
SELECT 'borrowers' AS entity,
       (SELECT COUNT(*) FROM L_BORR) - (SELECT COUNT(*) FROM borrowers)     AS delta
UNION ALL SELECT 'loan_products',
       (SELECT COUNT(*) FROM L_PROD) - (SELECT COUNT(*) FROM loan_products)
UNION ALL SELECT 'loan_accounts',
       (SELECT COUNT(*) FROM L_ACCT) - (SELECT COUNT(*) FROM loan_accounts)
UNION ALL SELECT 'payments',
       (SELECT COUNT(*) FROM L_PMT)  - (SELECT COUNT(*) FROM payments);

-- 4b. Legacy keys missing from modern (must return NO rows).
SELECT BORR_ID FROM L_BORR WHERE BORR_ID NOT IN (SELECT external_id FROM borrowers);
SELECT LN_ACCT_NBR FROM L_ACCT WHERE LN_ACCT_NBR NOT IN (SELECT account_number FROM loan_accounts);
SELECT PMT_SEQ_NBR FROM L_PMT WHERE PMT_SEQ_NBR NOT IN (SELECT legacy_sequence_number FROM payments);

-- 4c. Amount delta (must be 0.00).
SELECT (SELECT SUM(CAST(REPLACE(LN_ORIG_AMT, ',', '') AS DECIMAL(15,2))) FROM L_ACCT)
     - (SELECT SUM(original_amount) FROM loan_accounts) AS original_amount_delta;

-- Clean up the links when finished.
DROP TABLE L_BORR; DROP TABLE L_PROD; DROP TABLE L_ACCT; DROP TABLE L_PMT;


-- =============================================================================
-- SECTION 5 — REFERENTIAL INTEGRITY (legacy has no FKs; modern enforces them)
-- =============================================================================
-- The legacy warehouse has no foreign keys, so it can contain orphans. These
-- queries surface them; the ETL rejects such rows (see
-- DataMigrationReferentialIntegrityTest) so the modern side cannot have any.

-- Legacy loans whose borrower/product is missing (should be empty for the seed):
SELECT LN_ACCT_NBR FROM CDW_LN_ACCT
WHERE BORR_ID NOT IN (SELECT BORR_ID FROM CDW_BORR_MSTR)
   OR PROD_CD  NOT IN (SELECT PROD_CD FROM CDW_LN_PROD);

-- Legacy payments whose loan is missing (should be empty for the seed):
SELECT PMT_SEQ_NBR FROM CDW_PMT_HIST
WHERE LN_ACCT_NBR NOT IN (SELECT LN_ACCT_NBR FROM CDW_LN_ACCT);
