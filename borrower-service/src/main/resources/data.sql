-- Migrated from legacy CDW_BORR_MSTR: dates parsed to DATE, credit score to
-- INTEGER, income de-comma'd to DECIMAL, status ACT expanded to ACTIVE.
INSERT INTO borrowers (external_id, first_name, last_name, middle_initial, ssn_hash, date_of_birth, address_line1, address_line2, city, state, zip_code, phone, email, credit_score, employment_status, annual_income, status) VALUES
    ('B-10001', 'James', 'Mitchell', 'R', 'ENC_XXX_001', DATE '1978-03-15', '742 Elm Street', 'Apt 3B', 'Springfield', 'IL', '62701', '217-555-0142', 'j.mitchell@email.com', 745, 'EMPLOYED', 92500.00, 'ACTIVE'),
    ('B-10002', 'Sarah', 'Chen', 'L', 'ENC_XXX_002', DATE '1985-07-22', '1100 Oak Avenue', NULL, 'Portland', 'OR', '97201', '503-555-0198', 's.chen@email.com', 780, 'EMPLOYED', 125000.00, 'ACTIVE'),
    ('B-10003', 'Michael', 'Torres', 'A', 'ENC_XXX_003', DATE '1972-11-08', '305 Pine Road', NULL, 'Austin', 'TX', '78701', '512-555-0167', 'm.torres@email.com', 692, 'SELF-EMP', 78000.00, 'ACTIVE'),
    ('B-10004', 'Emily', 'Johnson', 'M', 'ENC_XXX_004', DATE '1990-02-28', '89 Maple Drive', 'Suite 12', 'Denver', 'CO', '80202', '303-555-0134', 'e.johnson@email.com', 810, 'EMPLOYED', 145000.00, 'ACTIVE'),
    ('B-10005', 'Robert', 'Williams', NULL, 'ENC_XXX_005', DATE '1968-06-14', '2200 Cedar Lane', NULL, 'Phoenix', 'AZ', '85001', '602-555-0156', 'r.williams@email.com', 658, 'RETIRED', 65000.00, 'ACTIVE');
