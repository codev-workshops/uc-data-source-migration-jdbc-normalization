-- =============================================================================
-- MODERN SEED DATA
-- Transformed from legacy CDW tables to modern normalized schema.
-- Status/type values use title-case to match LoanService expand methods.
-- =============================================================================

-- Borrowers (from CDW_BORR_MSTR)
-- Insert order: B-10001→1, B-10002→2, B-10003→3, B-10004→4, B-10005→5
INSERT INTO borrowers (external_id, first_name, last_name, middle_initial, ssn_hash, date_of_birth, address_line1, address_line2, city, state, zip_code, phone, email, credit_score, employment_status, annual_income, status)
VALUES ('B-10001', 'James', 'Mitchell', 'R', 'ENC_XXX_001', DATE '1978-03-15', '742 Elm Street', 'Apt 3B', 'Springfield', 'IL', '62701', '217-555-0142', 'j.mitchell@email.com', 745, 'EMPLOYED', 92500.00, 'Active');

INSERT INTO borrowers (external_id, first_name, last_name, middle_initial, ssn_hash, date_of_birth, address_line1, address_line2, city, state, zip_code, phone, email, credit_score, employment_status, annual_income, status)
VALUES ('B-10002', 'Sarah', 'Chen', 'L', 'ENC_XXX_002', DATE '1985-07-22', '1100 Oak Avenue', NULL, 'Portland', 'OR', '97201', '503-555-0198', 's.chen@email.com', 780, 'EMPLOYED', 125000.00, 'Active');

INSERT INTO borrowers (external_id, first_name, last_name, middle_initial, ssn_hash, date_of_birth, address_line1, address_line2, city, state, zip_code, phone, email, credit_score, employment_status, annual_income, status)
VALUES ('B-10003', 'Michael', 'Torres', 'A', 'ENC_XXX_003', DATE '1972-11-08', '305 Pine Road', NULL, 'Austin', 'TX', '78701', '512-555-0167', 'm.torres@email.com', 692, 'SELF-EMP', 78000.00, 'Active');

INSERT INTO borrowers (external_id, first_name, last_name, middle_initial, ssn_hash, date_of_birth, address_line1, address_line2, city, state, zip_code, phone, email, credit_score, employment_status, annual_income, status)
VALUES ('B-10004', 'Emily', 'Johnson', 'M', 'ENC_XXX_004', DATE '1990-02-28', '89 Maple Drive', 'Suite 12', 'Denver', 'CO', '80202', '303-555-0134', 'e.johnson@email.com', 810, 'EMPLOYED', 145000.00, 'Active');

INSERT INTO borrowers (external_id, first_name, last_name, middle_initial, ssn_hash, date_of_birth, address_line1, address_line2, city, state, zip_code, phone, email, credit_score, employment_status, annual_income, status)
VALUES ('B-10005', 'Robert', 'Williams', NULL, 'ENC_XXX_005', DATE '1968-06-14', '2200 Cedar Lane', NULL, 'Phoenix', 'AZ', '85001', '602-555-0156', 'r.williams@email.com', 658, 'RETIRED', 65000.00, 'Active');

-- Loan Products (from CDW_LN_PROD)
-- Insert order: FXD30→1, FXD15→2, ARM51→3, FHA30→4, VA30→5
INSERT INTO loan_products (code, name, type, term_months, rate_type, min_amount, max_amount, is_active, effective_date, expiration_date)
VALUES ('FXD30', '30-Year Fixed Rate Mortgage', 'FXD', 360, 'FIXED', 50000.00, 1500000.00, TRUE, DATE '2020-01-01', DATE '2099-12-31');

INSERT INTO loan_products (code, name, type, term_months, rate_type, min_amount, max_amount, is_active, effective_date, expiration_date)
VALUES ('FXD15', '15-Year Fixed Rate Mortgage', 'FXD', 180, 'FIXED', 50000.00, 1000000.00, TRUE, DATE '2020-01-01', DATE '2099-12-31');

INSERT INTO loan_products (code, name, type, term_months, rate_type, min_amount, max_amount, is_active, effective_date, expiration_date)
VALUES ('ARM51', '5/1 Adjustable Rate Mortgage', 'ARM', 360, 'VARIABLE', 75000.00, 1200000.00, TRUE, DATE '2020-01-01', DATE '2099-12-31');

INSERT INTO loan_products (code, name, type, term_months, rate_type, min_amount, max_amount, is_active, effective_date, expiration_date)
VALUES ('FHA30', 'FHA 30-Year Fixed', 'FHA', 360, 'FIXED', 25000.00, 472030.00, TRUE, DATE '2020-01-01', DATE '2099-12-31');

INSERT INTO loan_products (code, name, type, term_months, rate_type, min_amount, max_amount, is_active, effective_date, expiration_date)
VALUES ('VA30', 'VA 30-Year Fixed', 'VA', 360, 'FIXED', 0.00, 750000.00, TRUE, DATE '2020-01-01', DATE '2099-12-31');

-- Loan Accounts (from CDW_LN_ACCT)
-- Insert order: LN-2019-00142→1, LN-2020-00398→2, LN-2018-00089→3, LN-2021-00567→4, LN-2017-00034→5
-- borrower_id FK: B-10001→1, B-10002→2, B-10003→3, B-10004→4, B-10005→5
-- product_id FK: FXD30→1, FXD15→2, ARM51→3, FHA30→4, VA30→5
INSERT INTO loan_accounts (account_number, borrower_id, product_id, original_amount, current_balance, interest_rate, term_months, monthly_payment, origination_date, maturity_date, first_payment_date, next_payment_date, status, delinquency_days, escrow_balance, ltv_percent, property_address, property_city, property_state, property_zip, property_type, appraised_value)
VALUES ('LN-2019-00142', 1, 1, 285000.00, 271432.56, 4.750, 360, 1487.02, DATE '2019-02-15', DATE '2049-02-15', DATE '2019-03-15', DATE '2026-01-15', 'Active', 0, 3245.80, 82.50, '742 Elm Street', 'Springfield', 'IL', '62701', 'Single Family Residence', 345000.00);

INSERT INTO loan_accounts (account_number, borrower_id, product_id, original_amount, current_balance, interest_rate, term_months, monthly_payment, origination_date, maturity_date, first_payment_date, next_payment_date, status, delinquency_days, escrow_balance, ltv_percent, property_address, property_city, property_state, property_zip, property_type, appraised_value)
VALUES ('LN-2020-00398', 2, 2, 420000.00, 312876.43, 3.125, 180, 2924.18, DATE '2020-04-01', DATE '2035-04-01', DATE '2020-05-01', DATE '2026-01-01', 'Active', 0, 4890.12, 68.20, '1100 Oak Avenue', 'Portland', 'OR', '97201', 'Condominium', 615000.00);

INSERT INTO loan_accounts (account_number, borrower_id, product_id, original_amount, current_balance, interest_rate, term_months, monthly_payment, origination_date, maturity_date, first_payment_date, next_payment_date, status, delinquency_days, escrow_balance, ltv_percent, property_address, property_city, property_state, property_zip, property_type, appraised_value)
VALUES ('LN-2018-00089', 3, 3, 195000.00, 178234.12, 5.250, 360, 1077.05, DATE '2018-07-01', DATE '2048-07-01', DATE '2018-08-01', DATE '2026-01-01', 'Active', 15, 2100.00, 75.00, '305 Pine Road', 'Austin', 'TX', '78701', 'Single Family Residence', 260000.00);

INSERT INTO loan_accounts (account_number, borrower_id, product_id, original_amount, current_balance, interest_rate, term_months, monthly_payment, origination_date, maturity_date, first_payment_date, next_payment_date, status, delinquency_days, escrow_balance, ltv_percent, property_address, property_city, property_state, property_zip, property_type, appraised_value)
VALUES ('LN-2021-00567', 4, 1, 525000.00, 498123.78, 3.875, 360, 2468.35, DATE '2021-10-01', DATE '2051-10-01', DATE '2021-11-01', DATE '2026-01-01', 'Active', 0, 6750.00, 72.80, '89 Maple Drive', 'Denver', 'CO', '80202', 'Townhouse', 721000.00);

INSERT INTO loan_accounts (account_number, borrower_id, product_id, original_amount, current_balance, interest_rate, term_months, monthly_payment, origination_date, maturity_date, first_payment_date, next_payment_date, status, delinquency_days, escrow_balance, ltv_percent, property_address, property_city, property_state, property_zip, property_type, appraised_value)
VALUES ('LN-2017-00034', 5, 4, 165000.00, 142567.90, 4.250, 360, 811.61, DATE '2017-03-01', DATE '2047-03-01', DATE '2017-04-01', DATE '2026-01-01', 'Active', 0, 1890.45, 80.00, '2200 Cedar Lane', 'Phoenix', 'AZ', '85001', 'Single Family Residence', 206000.00);

-- Payments (from CDW_PMT_HIST)
-- loan_account_id FK: LN-2019-00142→1, LN-2020-00398→2, LN-2018-00089→3, LN-2021-00567→4, LN-2017-00034→5
INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (1, DATE '2025-12-15', 1487.02, 456.78, 1074.69, 355.55, 0.00, 'Regular', 'Posted', DATE '2025-12-14', DATE '2025-12-15');

INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (1, DATE '2025-11-15', 1487.02, 454.97, 1076.50, 355.55, 0.00, 'Regular', 'Posted', DATE '2025-11-14', DATE '2025-11-15');

INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (2, DATE '2025-12-01', 2924.18, 1842.56, 815.50, 266.12, 0.00, 'Regular', 'Posted', DATE '2025-11-30', DATE '2025-12-01');

INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (2, DATE '2025-11-01', 2924.18, 1837.76, 820.30, 266.12, 0.00, 'Regular', 'Posted', DATE '2025-10-31', DATE '2025-11-01');

INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (3, DATE '2025-12-01', 1077.05, 297.12, 779.93, 0.00, 0.00, 'Regular', 'Posted', DATE '2025-12-05', DATE '2025-12-06');

INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (3, DATE '2025-11-01', 1077.05, 295.82, 781.23, 0.00, 47.50, 'Regular', 'Posted', DATE '2025-11-18', DATE '2025-11-19');

INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (4, DATE '2025-12-01', 2468.35, 857.23, 1611.12, 0.00, 0.00, 'Regular', 'Posted', DATE '2025-11-29', DATE '2025-12-01');

INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (4, DATE '2025-11-01', 2468.35, 854.46, 1613.89, 0.00, 0.00, 'Regular', 'Posted', DATE '2025-10-31', DATE '2025-11-01');

INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (5, DATE '2025-12-01', 811.61, 306.45, 505.16, 0.00, 0.00, 'Regular', 'Posted', DATE '2025-11-30', DATE '2025-12-01');

INSERT INTO payments (loan_account_id, payment_date, total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status, received_date, processed_date)
VALUES (5, DATE '2025-11-01', 811.61, 305.37, 506.24, 0.00, 0.00, 'Regular', 'Posted', DATE '2025-10-30', DATE '2025-11-01');
