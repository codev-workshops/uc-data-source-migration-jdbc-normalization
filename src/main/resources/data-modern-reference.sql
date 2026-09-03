-- =============================================================================
-- MODERN SCHEMA REFERENCE DATA
-- =============================================================================
-- Seed rows for the lookup tables defined in schema-modern.sql. Labels mirror
-- the LoanService.expand* mappings; codes are the legacy abbreviations except
-- BORR_EMP_STAT 'SELF-EMP', which is normalised to 'SELF_EMPLOYED' (D3).
--
-- Every code present in data-legacy.sql is covered:
--   borrower_status ACT, borrower_record_type PRI,
--   employment_status EMPLOYED / SELF_EMPLOYED (<- SELF-EMP) / RETIRED,
--   product_type FXD / ARM / FHA / VA, rate_type FIXED / VARIABLE,
--   loan_status ACT, property_type SFR / CND / TWN,
--   payment_type REG, payment_status PST.
--
-- TODO(Q6): the full production code sets for BORR_STAT_CD, BORR_REC_TYP and
-- BORR_EMP_STAT are unconfirmed (docs/DESIGN_DECISIONS.md, open question Q6).
-- The rows beyond the seed-data codes (INA, CO, UNEMPLOYED) are the designed
-- defaults; extend them before the Phase 4 load or unknown codes will quarantine.
-- =============================================================================

INSERT INTO borrower_status (code, label) VALUES ('ACT', 'Active'), ('INA', 'Inactive');
INSERT INTO borrower_record_type (code, label) VALUES ('PRI', 'Primary'), ('CO', 'Co-Borrower');
INSERT INTO employment_status (code, label) VALUES
    ('EMPLOYED', 'Employed'), ('SELF_EMPLOYED', 'Self-Employed'), ('RETIRED', 'Retired'), ('UNEMPLOYED', 'Unemployed');
INSERT INTO product_type (code, label) VALUES
    ('FXD', 'Fixed Rate'), ('ARM', 'Adjustable Rate'), ('FHA', 'FHA Insured'), ('VA', 'VA Guaranteed');
INSERT INTO rate_type (code, label) VALUES ('FIXED', 'Fixed'), ('VARIABLE', 'Variable');
INSERT INTO loan_status (code, label, is_open) VALUES
    ('ACT', 'Active', TRUE), ('CLO', 'Closed', FALSE), ('DFT', 'Default', TRUE), ('FRB', 'Forbearance', TRUE);
INSERT INTO property_type (code, label) VALUES
    ('SFR', 'Single Family Residence'), ('CND', 'Condominium'), ('MFR', 'Multi-Family Residence'), ('TWN', 'Townhouse');
INSERT INTO payment_type (code, label) VALUES
    ('REG', 'Regular'), ('EXT', 'Extra'), ('PRT', 'Partial'), ('PRE', 'Prepayment');
INSERT INTO payment_status (code, label, is_final) VALUES
    ('PST', 'Posted', TRUE), ('REV', 'Reversed', TRUE), ('NSF', 'Non-Sufficient Funds', TRUE), ('PND', 'Pending', FALSE);
