-- Intentionally (near-)empty legacy seed (test only). The referential-integrity
-- tests in DataMigrationReferentialIntegrityTest insert their own controlled
-- rows and invoke the ETL on demand (loanservice.migrate-on-startup=false).
-- The DELETEs are no-ops on the freshly created tables and just give the script
-- executable statements.
DELETE FROM CDW_PMT_HIST;
DELETE FROM CDW_LN_ACCT;
DELETE FROM CDW_LN_PROD;
DELETE FROM CDW_BORR_MSTR;
