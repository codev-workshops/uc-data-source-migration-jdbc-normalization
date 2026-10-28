-- =============================================================================
-- V3: Audit columns for the modern schema
-- =============================================================================
-- borrowers, loan_accounts and payments already have created_at / updated_at
-- (from V2); loan_products gets them here. All four tables get created_by.
-- =============================================================================

ALTER TABLE borrowers     ADD COLUMN created_by VARCHAR(50);
ALTER TABLE loan_products ADD COLUMN created_by VARCHAR(50);
ALTER TABLE loan_accounts ADD COLUMN created_by VARCHAR(50);
ALTER TABLE payments      ADD COLUMN created_by VARCHAR(50);

ALTER TABLE loan_products ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE loan_products ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
