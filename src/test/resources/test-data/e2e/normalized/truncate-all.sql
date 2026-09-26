-- Empties the normalized tables for empty-database scenarios, child rows first so the V5
-- foreign keys stay satisfied.
DELETE FROM payment;
DELETE FROM loan_account;
DELETE FROM loan_product;
DELETE FROM borrower;
