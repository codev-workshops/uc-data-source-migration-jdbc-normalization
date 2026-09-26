-- Payment whose amount is not numeric, triggering parseLegacyAmount failures.
INSERT INTO CDW_PMT_HIST VALUES ('PMT-BAD-0001', 'LN-2019-00142', '12/20/2025', 'abc', '456.78', '1,074.69', '355.55', '0.00', 'REG', 'PST', '12/19/2025', '12/20/2025', '12/20/2025', '12/20/2025');
