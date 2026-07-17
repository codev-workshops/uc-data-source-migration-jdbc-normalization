-- =============================================================================
-- EDGE-CASE LEGACY SEED (test only)
-- =============================================================================
-- Exercises the status/type/property code arms that the production 5/5/5/10
-- dataset never hits (which is all ACT / SFR-CND-TWN / REG / PST), plus a few
-- unknown codes that must pass through unchanged. Loaded into an isolated
-- in-memory database by DataMigrationEdgeCaseTest so the real ETL and both
-- providers are validated against every conversion branch with real data.
-- =============================================================================

-- Borrowers: INA (inactive) and an unknown status code; the second also has
-- null middle initial / credit score / income to exercise null conversions.
INSERT INTO CDW_BORR_MSTR VALUES ('EB-1', 'Nora', 'Adams', 'K', 'ENC_E1', '05/01/1980', '1 First St', NULL, 'Reno', 'NV', '89501', '775-555-0001', 'nora@email.com', '701', 'EMPLOYED', '88,000', '01/01/2015', '01/01/2025', 'INA', 'PRI');
INSERT INTO CDW_BORR_MSTR VALUES ('EB-2', 'Owen', 'Brooks', NULL, 'ENC_E2', NULL, '2 Second St', NULL, 'Reno', 'NV', '89502', '775-555-0002', 'owen@email.com', NULL, 'RETIRED', NULL, '02/01/2016', '02/01/2025', 'XXX', 'PRI');

-- Product
INSERT INTO CDW_LN_PROD VALUES ('EP-1', 'Edge Product', 'FXD', '360', 'FIXED', '10,000', '900,000', 'ACT', '01/01/2015', '12/31/2099');

-- Loan accounts: loan statuses CLO / DFT / FRB / unknown, property MFR / unknown.
INSERT INTO CDW_LN_ACCT VALUES ('EA-1', 'EB-1', 'Nora', 'Adams', '0001', 'EP-1', '300,000', '250,000.00', '4.000', '360', '1,400.00', '01/15/2015', '01/15/2045', '02/15/2015', '01/15/2026', 'CLO', '0', '1,000.00', '70.0', '10 Elm', 'Reno', 'NV', '89501', 'MFR', '350,000', '01/01/2015', '12/01/2025');
INSERT INTO CDW_LN_ACCT VALUES ('EA-2', 'EB-2', 'Owen', 'Brooks', '0002', 'EP-1', '200,000', '180,000.00', '5.000', '360', '1,100.00', '02/15/2016', '02/15/2046', '03/15/2016', '01/15/2026', 'DFT', '30', '900.00', '75.0', '20 Oak', 'Reno', 'NV', '89502', 'QQQ', '240,000', '02/01/2016', '12/01/2025');
INSERT INTO CDW_LN_ACCT VALUES ('EA-3', 'EB-1', 'Nora', 'Adams', '0001', 'EP-1', '150,000', '140,000.00', '3.500', '180', '900.00', '03/15/2017', '03/15/2032', '04/15/2017', '01/15/2026', 'FRB', '0', '500.00', '65.0', '30 Pine', 'Reno', 'NV', '89501', 'MFR', '190,000', '03/01/2017', '12/01/2025');
INSERT INTO CDW_LN_ACCT VALUES ('EA-4', 'EB-2', 'Owen', 'Brooks', '0002', 'EP-1', '120,000', '110,000.00', '6.000', '360', '750.00', '04/15/2018', '04/15/2048', '05/15/2018', '01/15/2026', 'ZZZ', '0', '400.00', '80.0', '40 Cedar', 'Reno', 'NV', '89502', 'MFR', '150,000', '04/01/2018', '12/01/2025');

-- Payments: types EXT / PRT / PRE / unknown, statuses REV / NSF / PND / unknown.
INSERT INTO CDW_PMT_HIST VALUES ('EPMT-1', 'EA-1', '12/15/2025', '1,400.00', '400.00', '1,000.00', '0.00', '0.00', 'EXT', 'REV', '12/14/2025', '12/15/2025', '12/15/2025', '12/15/2025');
INSERT INTO CDW_PMT_HIST VALUES ('EPMT-2', 'EA-1', '11/15/2025', '1,400.00', '398.00', '1,002.00', '0.00', '25.00', 'PRT', 'NSF', '11/14/2025', '11/15/2025', '11/15/2025', '11/15/2025');
INSERT INTO CDW_PMT_HIST VALUES ('EPMT-3', 'EA-2', '12/15/2025', '1,100.00', '300.00', '800.00', '0.00', '0.00', 'PRE', 'PND', '12/14/2025', '12/15/2025', '12/15/2025', '12/15/2025');
INSERT INTO CDW_PMT_HIST VALUES ('EPMT-4', 'EA-2', '11/15/2025', '1,100.00', '298.00', '802.00', '0.00', '0.00', 'ZZ', 'ZZ', '11/14/2025', '11/15/2025', '11/15/2025', '11/15/2025');
