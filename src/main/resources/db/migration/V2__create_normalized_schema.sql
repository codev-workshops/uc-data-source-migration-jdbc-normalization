-- =============================================================================
-- NORMALIZED SCHEMA
-- =============================================================================
-- Target model for the migration:
--   - Descriptive column names
--   - Proper types (DATE, DECIMAL, INTEGER)
--   - Borrower data no longer embedded in loan accounts
-- Cross-table constraints are added in V5, after the data migration has run.
-- =============================================================================

CREATE TABLE borrower (
    borrower_id       VARCHAR(20) PRIMARY KEY,
    first_name        VARCHAR(50),
    last_name         VARCHAR(50),
    middle_initial    VARCHAR(1),
    ssn_encrypted     VARCHAR(100),
    date_of_birth     DATE,
    address_line1     VARCHAR(100),
    address_line2     VARCHAR(100),
    city              VARCHAR(50),
    state_code        VARCHAR(2),
    zip_code          VARCHAR(10),
    phone_number      VARCHAR(15),
    email             VARCHAR(100),
    credit_score      INTEGER,
    employment_status VARCHAR(20),
    annual_income     DECIMAL(15,2),
    created_date      DATE,
    updated_date      DATE,
    status_code       VARCHAR(5),
    record_type       VARCHAR(10)
);

CREATE TABLE loan_product (
    product_code    VARCHAR(10) PRIMARY KEY,
    description     VARCHAR(200),
    product_type    VARCHAR(5),
    term_months     INTEGER,
    rate_type       VARCHAR(10),
    min_amount      DECIMAL(15,2),
    max_amount      DECIMAL(15,2),
    status_code     VARCHAR(5),
    effective_date  DATE,
    expiration_date DATE
);

CREATE TABLE loan_account (
    loan_account_number     VARCHAR(20) PRIMARY KEY,
    borrower_id             VARCHAR(20),
    product_code            VARCHAR(10),
    original_amount         DECIMAL(15,2),
    current_balance         DECIMAL(15,2),
    interest_rate           DECIMAL(6,3),
    term_months             INTEGER,
    monthly_payment         DECIMAL(15,2),
    origination_date        DATE,
    maturity_date           DATE,
    first_payment_date      DATE,
    next_payment_date       DATE,
    status_code             VARCHAR(5),
    delinquent_days         INTEGER,
    escrow_balance          DECIMAL(15,2),
    ltv_percent             DECIMAL(6,2),
    property_address_line1  VARCHAR(100),
    property_city           VARCHAR(50),
    property_state          VARCHAR(2),
    property_zip            VARCHAR(10),
    property_type           VARCHAR(10),
    property_appraised_value DECIMAL(15,2),
    created_date            DATE,
    updated_date            DATE
);

CREATE TABLE payment (
    payment_id          VARCHAR(20) PRIMARY KEY,
    loan_account_number VARCHAR(20),
    payment_date        DATE,
    total_amount        DECIMAL(15,2),
    principal_amount    DECIMAL(15,2),
    interest_amount     DECIMAL(15,2),
    escrow_amount       DECIMAL(15,2),
    late_fee            DECIMAL(15,2),
    type_code           VARCHAR(5),
    status_code         VARCHAR(5),
    received_date       DATE,
    processed_date      DATE,
    created_date        DATE,
    updated_date        DATE
);
