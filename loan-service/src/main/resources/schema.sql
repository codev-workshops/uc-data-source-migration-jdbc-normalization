-- Modern loan_accounts schema (loan-service owned database).
-- Denormalized borrower name/SSN columns are removed; only cross-context
-- references (borrower_id = borrower external id, product_code) are stored.
CREATE TABLE loan_accounts (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_number      VARCHAR(20) UNIQUE NOT NULL,
    borrower_id         VARCHAR(20) NOT NULL,
    product_code        VARCHAR(10) NOT NULL,
    original_amount     DECIMAL(12, 2) NOT NULL,
    current_balance     DECIMAL(12, 2) NOT NULL,
    interest_rate       DECIMAL(5, 3) NOT NULL,
    term_months         INTEGER NOT NULL,
    monthly_payment     DECIMAL(10, 2) NOT NULL,
    origination_date    DATE NOT NULL,
    maturity_date       DATE NOT NULL,
    first_payment_date  DATE,
    next_payment_date   DATE,
    status              VARCHAR(15) DEFAULT 'ACTIVE',
    delinquency_days    INTEGER DEFAULT 0,
    escrow_balance      DECIMAL(10, 2) DEFAULT 0,
    ltv_percent         DECIMAL(5, 2),
    property_address    VARCHAR(100),
    property_city       VARCHAR(50),
    property_state      VARCHAR(2),
    property_zip        VARCHAR(10),
    property_type       VARCHAR(30),
    appraised_value     DECIMAL(12, 2)
);

CREATE INDEX idx_loan_accounts_borrower ON loan_accounts(borrower_id);
CREATE INDEX idx_loan_accounts_status ON loan_accounts(status);
