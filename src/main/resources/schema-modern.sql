-- =============================================================================
-- PROPOSED TARGET SCHEMA (normalized, strongly typed)
-- =============================================================================
-- Derived from docs/MIGRATION_ANALYSIS.md. Design goals:
--   * Real types: DATE, DECIMAL, INTEGER/BIGINT, BOOLEAN — no VARCHAR-for-everything
--   * Surrogate BIGINT primary keys; legacy string identifiers preserved as
--     UNIQUE "legacy_*" natural keys for traceability and ID resolution
--   * Enforced foreign keys between borrower -> loan -> payment and loan -> product
--   * Duplicated borrower data removed from loan accounts (single source of truth)
--   * Cryptic codes replaced by small reference tables with human-readable labels
--   * CHECK constraints for ranges the legacy schema could not enforce
--
-- Dialect: ANSI SQL, tested mentally against H2 / PostgreSQL. Adjust
-- IDENTITY syntax (e.g. BIGSERIAL / GENERATED ALWAYS AS IDENTITY) per engine.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Reference / lookup tables (replace cryptic status codes)
-- -----------------------------------------------------------------------------

CREATE TABLE borrower_status (
    code            VARCHAR(10)   PRIMARY KEY,           -- legacy BORR_STAT_CD (e.g. ACT)
    label           VARCHAR(50)   NOT NULL UNIQUE        -- e.g. 'Active'
);

CREATE TABLE borrower_record_type (
    code            VARCHAR(10)   PRIMARY KEY,           -- legacy BORR_REC_TYP (e.g. PRI)
    label           VARCHAR(50)   NOT NULL UNIQUE        -- e.g. 'Primary'
);

CREATE TABLE employment_status (
    code            VARCHAR(20)   PRIMARY KEY,           -- EMPLOYED, SELF_EMPLOYED, RETIRED, ...
    label           VARCHAR(50)   NOT NULL UNIQUE
);

CREATE TABLE product_type (
    code            VARCHAR(10)   PRIMARY KEY,           -- FXD, ARM, FHA, VA
    label           VARCHAR(50)   NOT NULL UNIQUE
);

CREATE TABLE rate_type (
    code            VARCHAR(10)   PRIMARY KEY,           -- FIXED, VARIABLE
    label           VARCHAR(50)   NOT NULL UNIQUE
);

CREATE TABLE loan_status (
    code            VARCHAR(10)   PRIMARY KEY,           -- ACT, CLO, DFT, FRB
    label           VARCHAR(50)   NOT NULL UNIQUE,       -- Active, Closed, Default, Forbearance
    is_open         BOOLEAN       NOT NULL DEFAULT TRUE  -- FALSE for Closed
);

CREATE TABLE property_type (
    code            VARCHAR(10)   PRIMARY KEY,           -- SFR, CND, MFR, TWN
    label           VARCHAR(50)   NOT NULL UNIQUE
);

CREATE TABLE payment_type (
    code            VARCHAR(10)   PRIMARY KEY,           -- REG, EXT, PRT, PRE
    label           VARCHAR(50)   NOT NULL UNIQUE
);

CREATE TABLE payment_status (
    code            VARCHAR(10)   PRIMARY KEY,           -- PST, REV, NSF, PND
    label           VARCHAR(50)   NOT NULL UNIQUE,
    is_final        BOOLEAN       NOT NULL DEFAULT TRUE  -- FALSE for Pending
);


-- -----------------------------------------------------------------------------
-- Core entities
-- -----------------------------------------------------------------------------

-- Reusable postal address. Referenced by borrowers (mailing) and loans (property).
-- Removes the borrower-address / property-address duplication for owner-occupied loans.
CREATE TABLE address (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    line1           VARCHAR(100)  NOT NULL,
    line2           VARCHAR(100),
    city            VARCHAR(50)   NOT NULL,
    state_code      CHAR(2)       NOT NULL,
    postal_code     VARCHAR(10)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_address_state CHECK (state_code = UPPER(state_code))
);

CREATE TABLE borrower (
    id                      BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    legacy_borrower_id      VARCHAR(20)   NOT NULL UNIQUE,   -- CDW_BORR_MSTR.BORR_ID ('B-10001')
    first_name              VARCHAR(50)   NOT NULL,
    last_name               VARCHAR(50)   NOT NULL,
    middle_initial          CHAR(1),
    ssn_encrypted           VARCHAR(100)  NOT NULL,          -- opaque ciphertext, never plaintext
    ssn_last4               CHAR(4),                         -- moved here from CDW_LN_ACCT.BORR_SSN_LST4
    date_of_birth           DATE          NOT NULL,
    mailing_address_id      BIGINT        NOT NULL,
    phone_number            VARCHAR(20),
    email_address           VARCHAR(100),
    credit_score            SMALLINT,
    employment_status_code  VARCHAR(20)   NOT NULL,
    annual_income           DECIMAL(15,2),
    status_code             VARCHAR(10)   NOT NULL,
    record_type_code        VARCHAR(10)   NOT NULL,
    created_at              TIMESTAMP     NOT NULL,          -- BORR_CRET_DT
    updated_at              TIMESTAMP     NOT NULL,          -- BORR_UPDT_DT
    CONSTRAINT fk_borrower_address     FOREIGN KEY (mailing_address_id)     REFERENCES address (id),
    CONSTRAINT fk_borrower_emp_status  FOREIGN KEY (employment_status_code) REFERENCES employment_status (code),
    CONSTRAINT fk_borrower_status      FOREIGN KEY (status_code)            REFERENCES borrower_status (code),
    CONSTRAINT fk_borrower_rec_type    FOREIGN KEY (record_type_code)       REFERENCES borrower_record_type (code),
    CONSTRAINT ck_borrower_credit_score CHECK (credit_score IS NULL OR credit_score BETWEEN 300 AND 850),
    CONSTRAINT ck_borrower_income       CHECK (annual_income IS NULL OR annual_income >= 0),
    CONSTRAINT ck_borrower_ssn_last4    CHECK (ssn_last4 IS NULL OR LENGTH(ssn_last4) = 4)  -- digit check enforced by loader
);

CREATE TABLE loan_product (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_code        VARCHAR(10)   NOT NULL UNIQUE,   -- CDW_LN_PROD.PROD_CD ('FXD30'), still the business key
    description         VARCHAR(200)  NOT NULL,
    product_type_code   VARCHAR(10)   NOT NULL,
    term_months         SMALLINT      NOT NULL,
    rate_type_code      VARCHAR(10)   NOT NULL,
    min_amount          DECIMAL(15,2) NOT NULL,
    max_amount          DECIMAL(15,2) NOT NULL,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,   -- PROD_STAT_CD = 'ACT'
    effective_date      DATE          NOT NULL,
    expiry_date         DATE,                                  -- NULL = open-ended (legacy '12/31/2099')
    CONSTRAINT fk_product_type      FOREIGN KEY (product_type_code) REFERENCES product_type (code),
    CONSTRAINT fk_product_rate_type FOREIGN KEY (rate_type_code)    REFERENCES rate_type (code),
    CONSTRAINT ck_product_term      CHECK (term_months > 0),
    CONSTRAINT ck_product_amounts   CHECK (min_amount >= 0 AND max_amount >= min_amount),
    CONSTRAINT ck_product_dates     CHECK (expiry_date IS NULL OR expiry_date >= effective_date)
);

-- The mortgaged property. Separated from the loan so the address is reusable
-- and property attributes (type, appraisal) have a single home.
CREATE TABLE property (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    address_id          BIGINT        NOT NULL,
    property_type_code  VARCHAR(10)   NOT NULL,
    appraised_value     DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_property_address FOREIGN KEY (address_id)         REFERENCES address (id),
    CONSTRAINT fk_property_type    FOREIGN KEY (property_type_code) REFERENCES property_type (code),
    CONSTRAINT ck_property_value   CHECK (appraised_value > 0)
);

CREATE TABLE loan_account (
    id                      BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_number          VARCHAR(20)   NOT NULL UNIQUE,   -- CDW_LN_ACCT.LN_ACCT_NBR ('LN-2019-00142'), business key
    borrower_id             BIGINT        NOT NULL,          -- resolved from BORR_ID; NO copied name / ssn columns
    product_id              BIGINT        NOT NULL,          -- resolved from PROD_CD
    property_id             BIGINT        NOT NULL,
    original_amount         DECIMAL(15,2) NOT NULL,
    current_balance         DECIMAL(15,2) NOT NULL,
    interest_rate           DECIMAL(6,3)  NOT NULL,          -- percent, e.g. 4.750
    term_months             SMALLINT      NOT NULL,
    monthly_payment_amount  DECIMAL(15,2) NOT NULL,
    origination_date        DATE          NOT NULL,
    maturity_date           DATE          NOT NULL,
    first_payment_date      DATE          NOT NULL,
    next_payment_date       DATE,
    status_code             VARCHAR(10)   NOT NULL,
    delinquency_days        INTEGER       NOT NULL DEFAULT 0,
    escrow_balance          DECIMAL(15,2) NOT NULL DEFAULT 0,
    loan_to_value_pct       DECIMAL(6,2),                    -- e.g. 82.50 (derivable; kept for parity)
    created_at              TIMESTAMP     NOT NULL,          -- LN_CRET_DT
    updated_at              TIMESTAMP     NOT NULL,          -- LN_UPDT_DT
    CONSTRAINT fk_loan_borrower FOREIGN KEY (borrower_id) REFERENCES borrower (id),
    CONSTRAINT fk_loan_product  FOREIGN KEY (product_id)  REFERENCES loan_product (id),
    CONSTRAINT fk_loan_property FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT fk_loan_status   FOREIGN KEY (status_code) REFERENCES loan_status (code),
    CONSTRAINT ck_loan_amounts  CHECK (original_amount > 0 AND current_balance >= 0 AND monthly_payment_amount >= 0),
    CONSTRAINT ck_loan_rate     CHECK (interest_rate >= 0 AND interest_rate < 100),
    CONSTRAINT ck_loan_term     CHECK (term_months > 0),
    CONSTRAINT ck_loan_dlq      CHECK (delinquency_days >= 0),
    CONSTRAINT ck_loan_ltv      CHECK (loan_to_value_pct IS NULL OR loan_to_value_pct >= 0),
    CONSTRAINT ck_loan_dates    CHECK (maturity_date > origination_date AND first_payment_date >= origination_date)
);

CREATE TABLE payment (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    legacy_payment_id   VARCHAR(20)   NOT NULL UNIQUE,   -- CDW_PMT_HIST.PMT_SEQ_NBR ('PMT-2025120001')
    loan_account_id     BIGINT        NOT NULL,          -- resolved from LN_ACCT_NBR
    payment_date        DATE          NOT NULL,          -- PMT_DT (due/effective date)
    total_amount        DECIMAL(15,2) NOT NULL,
    principal_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,
    interest_amount     DECIMAL(15,2) NOT NULL DEFAULT 0,
    escrow_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    late_fee_amount     DECIMAL(15,2) NOT NULL DEFAULT 0,
    payment_type_code   VARCHAR(10)   NOT NULL,
    status_code         VARCHAR(10)   NOT NULL,
    received_date       DATE,
    processed_date      DATE,
    created_at          TIMESTAMP     NOT NULL,          -- PMT_CRET_DT
    updated_at          TIMESTAMP     NOT NULL,          -- PMT_UPDT_DT
    CONSTRAINT fk_payment_loan   FOREIGN KEY (loan_account_id)   REFERENCES loan_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_type   FOREIGN KEY (payment_type_code) REFERENCES payment_type (code),
    CONSTRAINT fk_payment_status FOREIGN KEY (status_code)       REFERENCES payment_status (code),
    CONSTRAINT ck_payment_amounts CHECK (
        total_amount >= 0 AND principal_amount >= 0 AND interest_amount >= 0
        AND escrow_amount >= 0 AND late_fee_amount >= 0
    ),
    -- Split must reconcile to the total (late fee is billed on top of the scheduled payment).
    CONSTRAINT ck_payment_split CHECK (total_amount = principal_amount + interest_amount + escrow_amount),
    CONSTRAINT ck_payment_dates CHECK (processed_date IS NULL OR received_date IS NULL OR processed_date >= received_date)
);


-- -----------------------------------------------------------------------------
-- Indexes for the access paths used by the application
-- -----------------------------------------------------------------------------
CREATE INDEX ix_loan_account_borrower   ON loan_account (borrower_id);            -- findByBorrowerId
CREATE INDEX ix_loan_account_product    ON loan_account (product_id);
CREATE INDEX ix_loan_account_status     ON loan_account (status_code);
CREATE INDEX ix_payment_loan_date       ON payment (loan_account_id, payment_date DESC);  -- payments by loan, newest first
CREATE INDEX ix_borrower_last_first     ON borrower (last_name, first_name);

