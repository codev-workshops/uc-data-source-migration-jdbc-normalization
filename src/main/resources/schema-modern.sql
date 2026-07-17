-- =============================================================================
-- MODERN SCHEMA (Target State)
-- =============================================================================
-- This is the target schema that the application should be migrated to.
-- Key improvements over legacy:
--   1. Proper data types (DATE, DECIMAL, INTEGER, BOOLEAN)
--   2. Clear, readable column names
--   3. Normalized structure (no duplicated borrower data in loans)
--   4. Foreign key constraints for referential integrity
--   5. Proper indexing
--   6. Timestamps instead of string dates
--   7. Enum-like status fields with CHECK constraints
-- =============================================================================

-- Idempotent re-init (safe for restarts and repeated in-memory initialization)
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS loan_accounts CASCADE;
DROP TABLE IF EXISTS loan_products CASCADE;
DROP TABLE IF EXISTS borrowers CASCADE;

CREATE TABLE borrowers (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    external_id     VARCHAR(20) UNIQUE NOT NULL,
    first_name      VARCHAR(50) NOT NULL,
    last_name       VARCHAR(50) NOT NULL,
    middle_initial  VARCHAR(1),
    ssn_hash        VARCHAR(100),
    date_of_birth   DATE,
    address_line1   VARCHAR(100),
    address_line2   VARCHAR(100),
    city            VARCHAR(50),
    state           VARCHAR(2),
    zip_code        VARCHAR(10),
    phone           VARCHAR(15),
    email           VARCHAR(100),
    credit_score    INTEGER,
    employment_status VARCHAR(20),
    annual_income   DECIMAL(12, 2),
    status          VARCHAR(10) DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loan_products (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(10) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    type            VARCHAR(5) NOT NULL,          -- FXD, ARM, FHA, VA
    term_months     INTEGER NOT NULL,
    rate_type       VARCHAR(10) NOT NULL,          -- FIXED, VARIABLE
    min_amount      DECIMAL(12, 2),
    max_amount      DECIMAL(12, 2),
    is_active       BOOLEAN DEFAULT TRUE,
    effective_date  DATE,
    expiration_date DATE
);

CREATE TABLE loan_accounts (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_number      VARCHAR(20) UNIQUE NOT NULL,
    borrower_id         BIGINT NOT NULL,
    product_id          BIGINT NOT NULL,
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
    appraised_value     DECIMAL(12, 2),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (borrower_id) REFERENCES borrowers(id),
    FOREIGN KEY (product_id) REFERENCES loan_products(id)
);

CREATE TABLE payments (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- Preserves the legacy CDW_PMT_HIST.PMT_SEQ_NBR business key. The modern PK
    -- is an auto-generated surrogate (see column_mappings.md: "Auto-generated;
    -- legacy ID stored if needed"), but the REST API must keep returning the
    -- legacy sequence number as `paymentId`, so it is retained here — mirroring
    -- the external_id/account_number/code pattern used on the other tables.
    legacy_sequence_number VARCHAR(20) UNIQUE,
    loan_account_id     BIGINT NOT NULL,
    payment_date        DATE NOT NULL,
    total_amount        DECIMAL(10, 2) NOT NULL,
    principal_amount    DECIMAL(10, 2),
    interest_amount     DECIMAL(10, 2),
    escrow_amount       DECIMAL(10, 2),
    late_fee            DECIMAL(10, 2) DEFAULT 0,
    type                VARCHAR(15) NOT NULL,      -- REGULAR, EXTRA, PARTIAL, PREPAYMENT
    status              VARCHAR(15) NOT NULL,      -- POSTED, REVERSED, NSF, PENDING
    received_date       DATE,
    processed_date      DATE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (loan_account_id) REFERENCES loan_accounts(id)
);

-- Indexes for common queries
CREATE INDEX idx_borrowers_email ON borrowers(email);
CREATE INDEX idx_borrowers_status ON borrowers(status);
CREATE INDEX idx_loan_accounts_borrower ON loan_accounts(borrower_id);
CREATE INDEX idx_loan_accounts_status ON loan_accounts(status);
CREATE INDEX idx_payments_loan ON payments(loan_account_id);
CREATE INDEX idx_payments_date ON payments(payment_date);
CREATE INDEX idx_payments_legacy_seq ON payments(legacy_sequence_number);
