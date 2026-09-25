-- Dirty rows layered on top of data-legacy.sql for edge-case contract tests.
-- Borrower with null middle initial, out-of-range credit score and unmapped status code.
MERGE INTO CDW_BORR_MSTR VALUES ('B-99901', 'Dirty', 'Borrower', NULL, 'ENC_XXX_999', '02/30/1990', '1 Nowhere Ln', NULL, 'Nowhere', 'ZZ', '00000', '000-000-0000', 'dirty@example.com', '9999', 'EMPLOYED', 'abc', '01/01/2020', '01/01/2020', 'XXX', 'PRI');
-- Loan with malformed comma amount, unmapped property/status codes and an orphan product code.
MERGE INTO CDW_LN_ACCT VALUES ('LN-9999-99901', 'B-99901', 'Dirty', 'Borrower', '9999', 'NOPE01', '12,34,567', 'N/A', '4.5%', '999', '1,000.000', '13/45/2020', '02/15/2049', '03/15/2019', '01/15/2026', 'ZZZ', '-3', '3,245.80', '82.5', '1 Nowhere Ln', 'Nowhere', 'ZZ', '00000', 'MOB', '345,000', '02/01/2019', '12/01/2025');
-- Payment with malformed amount, unmapped type/status codes.
MERGE INTO CDW_PMT_HIST VALUES ('PMT-9999999901', 'LN-9999-99901', '12/15/2025', '1.487,02', NULL, '', '355.55', '0.00', 'ZZZ', 'QQQ', '12/14/2025', '12/15/2025', '12/15/2025', '12/15/2025');
