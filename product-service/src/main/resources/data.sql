-- Migrated from legacy CDW_LN_PROD: amounts de-comma'd to DECIMAL, term parsed
-- to INTEGER, ACT status expanded to a BOOLEAN, dates parsed to DATE.
INSERT INTO loan_products (code, name, type, term_months, rate_type, min_amount, max_amount, is_active, effective_date, expiration_date) VALUES
    ('FXD30', '30-Year Fixed Rate Mortgage', 'FXD', 360, 'FIXED', 50000.00, 1500000.00, TRUE, DATE '2020-01-01', DATE '2099-12-31'),
    ('FXD15', '15-Year Fixed Rate Mortgage', 'FXD', 180, 'FIXED', 50000.00, 1000000.00, TRUE, DATE '2020-01-01', DATE '2099-12-31'),
    ('ARM51', '5/1 Adjustable Rate Mortgage', 'ARM', 360, 'VARIABLE', 75000.00, 1200000.00, TRUE, DATE '2020-01-01', DATE '2099-12-31'),
    ('FHA30', 'FHA 30-Year Fixed', 'FHA', 360, 'FIXED', 25000.00, 472030.00, TRUE, DATE '2020-01-01', DATE '2099-12-31'),
    ('VA30', 'VA 30-Year Fixed', 'VA', 360, 'FIXED', 0.00, 750000.00, TRUE, DATE '2020-01-01', DATE '2099-12-31');
