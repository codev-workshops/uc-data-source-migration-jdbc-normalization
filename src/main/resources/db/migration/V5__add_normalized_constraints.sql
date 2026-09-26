-- =============================================================================
-- CONSTRAINTS AND INDEXES FOR THE NORMALIZED SCHEMA
-- =============================================================================
-- Applied after the data migration so the referential integrity the legacy
-- schema never enforced is validated against the migrated rows.
-- =============================================================================

-- Foreign keys on the BIGINT surrogate ids
ALTER TABLE loan_account
    ADD CONSTRAINT fk_loan_account_borrower
    FOREIGN KEY (borrower_id) REFERENCES borrower (id);

ALTER TABLE loan_account
    ADD CONSTRAINT fk_loan_account_product
    FOREIGN KEY (product_id) REFERENCES loan_product (id);

ALTER TABLE payment
    ADD CONSTRAINT fk_payment_loan_account
    FOREIGN KEY (loan_account_id) REFERENCES loan_account (id);

-- Check constraints
ALTER TABLE borrower
    ADD CONSTRAINT ck_borrower_credit_score
    CHECK (credit_score IS NULL OR credit_score BETWEEN 300 AND 850);

ALTER TABLE loan_account
    ADD CONSTRAINT ck_loan_account_original_amount
    CHECK (original_amount IS NULL OR original_amount >= 0);

ALTER TABLE loan_account
    ADD CONSTRAINT ck_loan_account_current_balance
    CHECK (current_balance IS NULL OR current_balance >= 0);

ALTER TABLE payment
    ADD CONSTRAINT ck_payment_total_amount
    CHECK (total_amount >= 0);

-- Baseline indexes from data/modern-schema/modern_tables.sql (singular names)
CREATE INDEX idx_borrower_email ON borrower (email);
CREATE INDEX idx_borrower_status ON borrower (status);
CREATE INDEX idx_loan_account_borrower ON loan_account (borrower_id);
CREATE INDEX idx_loan_account_status ON loan_account (status);
CREATE INDEX idx_payment_loan ON payment (loan_account_id);
CREATE INDEX idx_payment_date ON payment (payment_date);

-- External-identifier lookups used by the REST API (borrower.external_id,
-- loan_product.code, loan_account.account_number, payment.external_id) are
-- already covered by the implicit indexes H2 creates for their UNIQUE
-- constraints in V2, so no separate idx_*_external_id / idx_*_code /
-- idx_*_account_number indexes are created.

-- Additional search indexes
CREATE INDEX idx_loan_account_product ON loan_account (product_id);
CREATE INDEX idx_borrower_last_name ON borrower (last_name);
CREATE INDEX idx_loan_account_property_state ON loan_account (property_state);
CREATE INDEX idx_loan_account_property_city ON loan_account (property_city);
CREATE INDEX idx_payment_status ON payment (status);
CREATE INDEX idx_payment_type ON payment (type);
