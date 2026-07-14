-- Modern loan_products schema (product-service owned database).
CREATE TABLE loan_products (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(10) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    type            VARCHAR(5) NOT NULL,
    term_months     INTEGER NOT NULL,
    rate_type       VARCHAR(10) NOT NULL,
    min_amount      DECIMAL(12, 2),
    max_amount      DECIMAL(12, 2),
    is_active       BOOLEAN DEFAULT TRUE,
    effective_date  DATE,
    expiration_date DATE
);

CREATE INDEX idx_loan_products_type ON loan_products(type);
