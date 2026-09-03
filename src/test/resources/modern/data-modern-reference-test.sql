-- TEST-ONLY copy of docs/proposed-target-schema.sql reference seed rows (H2).
-- Replace with src/main/resources/data-modern-reference.sql once Phase 2 is merged.

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
