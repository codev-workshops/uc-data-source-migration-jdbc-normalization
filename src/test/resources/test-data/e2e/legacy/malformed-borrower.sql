-- Borrower whose credit score is not numeric, triggering parseLegacyInteger failures.
INSERT INTO CDW_BORR_MSTR VALUES ('B-BAD-01', 'Nina', 'Brooks', 'K', 'ENC_XXX_901', '05/05/1980', '17 Willow Way', NULL, 'Boise', 'ID', '83702', '208-555-0111', 'n.brooks@email.com', 'N/A', 'EMPLOYED', '88,000', '01/05/2021', '10/10/2025', 'ACT', 'PRI');
