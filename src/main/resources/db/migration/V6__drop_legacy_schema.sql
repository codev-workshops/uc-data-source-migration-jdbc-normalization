-- ============================================================================
-- LEGACY RETIREMENT: drop the CDW_* warehouse tables
-- ============================================================================
-- The normalized schema (V2) is now the sole source of truth. V4 already copied
-- every legacy row into the normalized tables and V5 constrains only normalized
-- tables, so no foreign key references the CDW_* tables and they can be dropped
-- without impacting the normalized schema.
-- ============================================================================

DROP TABLE IF EXISTS CDW_PMT_HIST;
DROP TABLE IF EXISTS CDW_LN_ACCT;
DROP TABLE IF EXISTS CDW_LN_PROD;
DROP TABLE IF EXISTS CDW_BORR_MSTR;
