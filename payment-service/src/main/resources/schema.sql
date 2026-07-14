-- Modern payments schema (payment-service owned database).
-- The loan is referenced by account number (business key) instead of a shared
-- loan_accounts FK, keeping the payment context's database independent.
CREATE TABLE payments (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    sequence_number     VARCHAR(20) UNIQUE,
    loan_account_number VARCHAR(20) NOT NULL,
    payment_date        DATE NOT NULL,
    total_amount        DECIMAL(10, 2) NOT NULL,
    principal_amount    DECIMAL(10, 2),
    interest_amount     DECIMAL(10, 2),
    escrow_amount       DECIMAL(10, 2),
    late_fee            DECIMAL(10, 2) DEFAULT 0,
    type                VARCHAR(15) NOT NULL,
    status              VARCHAR(15) NOT NULL,
    received_date       DATE,
    processed_date      DATE
);

CREATE INDEX idx_payments_loan ON payments(loan_account_number);
CREATE INDEX idx_payments_date ON payments(payment_date);
