-- =============================================================================
-- NORMALIZED SCHEMA (modern target model, singular table names)
-- =============================================================================
-- Aligned with data/modern-schema/modern_tables.sql:
--   - BIGINT surrogate primary keys, natural keys kept as UNIQUE columns
--   - Proper types (DATE, TIMESTAMP, DECIMAL, INTEGER, BOOLEAN)
--   - Status / type columns hold the expanded modern values (ACTIVE, POSTED, ...)
--   - Borrower data no longer embedded in loan accounts
-- Foreign keys, check constraints and indexes are added in V5, after the data
-- migration (V4) has resolved the legacy string identifiers to surrogate ids.
-- =============================================================================

CREATE TABLE borrower (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    external_id       VARCHAR(20) UNIQUE NOT NULL,
    first_name        VARCHAR(50) NOT NULL,
    last_name         VARCHAR(50) NOT NULL,
    middle_initial    VARCHAR(1),
    ssn_hash          VARCHAR(100),
    date_of_birth     DATE,
    address_line1     VARCHAR(100),
    address_line2     VARCHAR(100),
    city              VARCHAR(50),
    state             VARCHAR(2),
    zip_code          VARCHAR(10),
    phone             VARCHAR(15),
    email             VARCHAR(100),
    credit_score      INTEGER,
    employment_status VARCHAR(20),
    annual_income     DECIMAL(12,2),
    status            VARCHAR(10) DEFAULT 'ACTIVE',
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loan_product (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(10) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    type            VARCHAR(5),
    term_months     INTEGER,
    rate_type       VARCHAR(10),
    min_amount      DECIMAL(12,2),
    max_amount      DECIMAL(12,2),
    is_active       BOOLEAN DEFAULT TRUE,
    effective_date  DATE,
    expiration_date DATE
);

CREATE TABLE loan_account (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_number      VARCHAR(20) UNIQUE NOT NULL,
    borrower_id         BIGINT NOT NULL,
    product_id          BIGINT NOT NULL,
    original_amount     DECIMAL(12,2),
    current_balance     DECIMAL(12,2),
    interest_rate       DECIMAL(5,3),
    term_months         INTEGER,
    monthly_payment     DECIMAL(10,2),
    origination_date    DATE,
    maturity_date       DATE,
    first_payment_date  DATE,
    next_payment_date   DATE,
    status              VARCHAR(15) DEFAULT 'ACTIVE',
    delinquency_days    INTEGER DEFAULT 0,
    escrow_balance      DECIMAL(10,2) DEFAULT 0,
    ltv_percent         DECIMAL(5,2),
    property_address    VARCHAR(100),
    property_city       VARCHAR(50),
    property_state      VARCHAR(2),
    property_zip        VARCHAR(10),
    property_type       VARCHAR(30),
    appraised_value     DECIMAL(12,2),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- external_id carries the legacy PMT_SEQ_NBR so the public API keeps exposing the
-- historical payment identifier while id stays an internal surrogate.
CREATE TABLE payment (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    external_id         VARCHAR(20) UNIQUE NOT NULL,
    loan_account_id     BIGINT NOT NULL,
    payment_date        DATE NOT NULL,
    total_amount        DECIMAL(10,2) NOT NULL,
    principal_amount    DECIMAL(10,2),
    interest_amount     DECIMAL(10,2),
    escrow_amount       DECIMAL(10,2),
    late_fee            DECIMAL(10,2) DEFAULT 0,
    type                VARCHAR(15) NOT NULL,
    status              VARCHAR(15) NOT NULL,
    received_date       DATE,
    processed_date      DATE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
