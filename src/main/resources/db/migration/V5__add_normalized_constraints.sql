-- =============================================================================
-- CONSTRAINTS AND INDEXES FOR THE NORMALIZED SCHEMA
-- =============================================================================
-- Applied after the data migration so the referential integrity the legacy
-- schema never enforced is validated against the migrated rows.
-- =============================================================================

ALTER TABLE borrower ALTER COLUMN last_name SET NOT NULL;
ALTER TABLE loan_account ALTER COLUMN borrower_id SET NOT NULL;
ALTER TABLE loan_account ALTER COLUMN product_code SET NOT NULL;
ALTER TABLE payment ALTER COLUMN loan_account_number SET NOT NULL;

ALTER TABLE loan_account
    ADD CONSTRAINT fk_loan_account_borrower
    FOREIGN KEY (borrower_id) REFERENCES borrower (borrower_id);

ALTER TABLE loan_account
    ADD CONSTRAINT fk_loan_account_product
    FOREIGN KEY (product_code) REFERENCES loan_product (product_code);

ALTER TABLE payment
    ADD CONSTRAINT fk_payment_loan_account
    FOREIGN KEY (loan_account_number) REFERENCES loan_account (loan_account_number);

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
    CHECK (total_amount IS NULL OR total_amount >= 0);

CREATE INDEX idx_loan_account_borrower_id ON loan_account (borrower_id);
CREATE INDEX idx_loan_account_product_code ON loan_account (product_code);
CREATE INDEX idx_payment_loan_account_number ON payment (loan_account_number);
