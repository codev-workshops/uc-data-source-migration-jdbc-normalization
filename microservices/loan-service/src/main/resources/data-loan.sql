-- Loan Products seed data
INSERT INTO CDW_LN_PROD VALUES ('FXD30', '30-Year Fixed Rate Mortgage', 'FXD', '360', 'FIXED', '50,000', '1,500,000', 'ACT', '01/01/2020', '12/31/2099');
INSERT INTO CDW_LN_PROD VALUES ('FXD15', '15-Year Fixed Rate Mortgage', 'FXD', '180', 'FIXED', '50,000', '1,000,000', 'ACT', '01/01/2020', '12/31/2099');
INSERT INTO CDW_LN_PROD VALUES ('ARM51', '5/1 Adjustable Rate Mortgage', 'ARM', '360', 'VARIABLE', '75,000', '1,200,000', 'ACT', '01/01/2020', '12/31/2099');
INSERT INTO CDW_LN_PROD VALUES ('FHA30', 'FHA 30-Year Fixed', 'FHA', '360', 'FIXED', '25,000', '472,030', 'ACT', '01/01/2020', '12/31/2099');
INSERT INTO CDW_LN_PROD VALUES ('VA30', 'VA 30-Year Fixed', 'VA', '360', 'FIXED', '0', '750,000', 'ACT', '01/01/2020', '12/31/2099');

-- Loan Accounts seed data
INSERT INTO CDW_LN_ACCT VALUES ('LN-2019-00142', 'B-10001', 'James', 'Mitchell', '0142', 'FXD30', '285,000', '271,432.56', '4.750', '360', '1,487.02', '02/15/2019', '02/15/2049', '03/15/2019', '01/15/2026', 'ACT', '0', '3,245.80', '82.5', '742 Elm Street', 'Springfield', 'IL', '62701', 'SFR', '345,000', '02/01/2019', '12/01/2025');
INSERT INTO CDW_LN_ACCT VALUES ('LN-2020-00398', 'B-10002', 'Sarah', 'Chen', '0198', 'FXD15', '420,000', '312,876.43', '3.125', '180', '2,924.18', '04/01/2020', '04/01/2035', '05/01/2020', '01/01/2026', 'ACT', '0', '4,890.12', '68.2', '1100 Oak Avenue', 'Portland', 'OR', '97201', 'CND', '615,000', '03/20/2020', '12/01/2025');
INSERT INTO CDW_LN_ACCT VALUES ('LN-2018-00089', 'B-10003', 'Michael', 'Torres', '0167', 'ARM51', '195,000', '178,234.12', '5.250', '360', '1,077.05', '07/01/2018', '07/01/2048', '08/01/2018', '01/01/2026', 'ACT', '15', '2,100.00', '75.0', '305 Pine Road', 'Austin', 'TX', '78701', 'SFR', '260,000', '06/15/2018', '12/01/2025');
INSERT INTO CDW_LN_ACCT VALUES ('LN-2021-00567', 'B-10004', 'Emily', 'Johnson', '0134', 'FXD30', '525,000', '498,123.78', '3.875', '360', '2,468.35', '10/01/2021', '10/01/2051', '11/01/2021', '01/01/2026', 'ACT', '0', '6,750.00', '72.8', '89 Maple Drive', 'Denver', 'CO', '80202', 'TWN', '721,000', '09/15/2021', '12/01/2025');
INSERT INTO CDW_LN_ACCT VALUES ('LN-2017-00034', 'B-10005', 'Robert', 'Williams', '0156', 'FHA30', '165,000', '142,567.90', '4.250', '360', '811.61', '03/01/2017', '03/01/2047', '04/01/2017', '01/01/2026', 'ACT', '0', '1,890.45', '80.0', '2200 Cedar Lane', 'Phoenix', 'AZ', '85001', 'SFR', '206,000', '02/20/2017', '12/01/2025');
