-- =============================================================================
-- V4: Loan payment history (payment lifecycle event tracking)
-- =============================================================================
-- event_type values: SCHEDULED, POSTED, REVERSED, LATE
-- =============================================================================

CREATE TABLE loan_payment_history (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_account_id     BIGINT NOT NULL,
    payment_id          BIGINT,
    event_type          VARCHAR(20),
    event_date          TIMESTAMP,
    amount              DECIMAL(10, 2),
    details             VARCHAR(500),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(50),

    FOREIGN KEY (loan_account_id) REFERENCES loan_accounts(id),
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_loan_payment_history_loan ON loan_payment_history(loan_account_id);
