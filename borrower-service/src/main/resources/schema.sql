-- Modern borrowers schema (borrower-service owned database).
CREATE TABLE borrowers (
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
    annual_income     DECIMAL(12, 2),
    status            VARCHAR(10) DEFAULT 'ACTIVE'
);

CREATE INDEX idx_borrowers_email ON borrowers(email);
CREATE INDEX idx_borrowers_status ON borrowers(status);
