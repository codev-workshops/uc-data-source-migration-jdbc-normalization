import java.sql.*;
import java.util.*;

public class ScaleBench {
    static Connection c;
    static final int BORROWERS = 500_000;
    static final int LOANS = 500_000;
    static final int PMT_PER_LOAN = 4; // 2M payments

    public static void main(String[] a) throws Exception {
        c = DriverManager.getConnection("jdbc:h2:mem:bench;DB_CLOSE_DELAY=-1;QUERY_CACHE_SIZE=0", "sa", "");
        schema();
        long t = System.currentTimeMillis();
        load();
        System.out.println("LOAD total ms=" + (System.currentTimeMillis() - t));
        counts();
        System.out.println("\n================ BASELINE INDEXES (as in modern_tables.sql) ================");
        queries();
        System.out.println("\n================ AFTER PROPOSED INDEXES ================");
        exec("CREATE INDEX idx_payments_loan_date ON payments(loan_account_id, payment_date DESC)");
        exec("CREATE INDEX idx_loan_accounts_status_id ON loan_accounts(status, id)");
        exec("DROP INDEX idx_payments_loan");
        exec("DROP INDEX idx_borrowers_email");
        queries();
        writeBench();
        concurrency();
    }

    static void exec(String sql) throws Exception { try (Statement s = c.createStatement()) { s.execute(sql); } }

    static void schema() throws Exception {
        exec("CREATE TABLE borrowers (id BIGINT PRIMARY KEY AUTO_INCREMENT, external_id VARCHAR(20) UNIQUE NOT NULL," +
            "first_name VARCHAR(50) NOT NULL,last_name VARCHAR(50) NOT NULL,middle_initial VARCHAR(1),ssn_hash VARCHAR(100)," +
            "date_of_birth DATE,address_line1 VARCHAR(100),address_line2 VARCHAR(100),city VARCHAR(50),state VARCHAR(2)," +
            "zip_code VARCHAR(10),phone VARCHAR(15),email VARCHAR(100),credit_score INTEGER,employment_status VARCHAR(20)," +
            "annual_income DECIMAL(12,2),status VARCHAR(10) DEFAULT 'ACTIVE',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        exec("CREATE TABLE loan_products (id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(10) UNIQUE NOT NULL," +
            "name VARCHAR(200) NOT NULL,type VARCHAR(5) NOT NULL,term_months INTEGER NOT NULL,rate_type VARCHAR(10) NOT NULL," +
            "min_amount DECIMAL(12,2),max_amount DECIMAL(12,2),is_active BOOLEAN DEFAULT TRUE,effective_date DATE,expiration_date DATE)");
        exec("CREATE TABLE loan_accounts (id BIGINT PRIMARY KEY AUTO_INCREMENT, account_number VARCHAR(20) UNIQUE NOT NULL," +
            "borrower_id BIGINT NOT NULL,product_id BIGINT NOT NULL,original_amount DECIMAL(12,2) NOT NULL,current_balance DECIMAL(12,2) NOT NULL," +
            "interest_rate DECIMAL(5,3) NOT NULL,term_months INTEGER NOT NULL,monthly_payment DECIMAL(10,2) NOT NULL,origination_date DATE NOT NULL," +
            "maturity_date DATE NOT NULL,first_payment_date DATE,next_payment_date DATE,status VARCHAR(15) DEFAULT 'ACTIVE'," +
            "delinquency_days INTEGER DEFAULT 0,escrow_balance DECIMAL(10,2) DEFAULT 0,ltv_percent DECIMAL(5,2),property_address VARCHAR(100)," +
            "property_city VARCHAR(50),property_state VARCHAR(2),property_zip VARCHAR(10),property_type VARCHAR(30),appraised_value DECIMAL(12,2)," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "FOREIGN KEY (borrower_id) REFERENCES borrowers(id),FOREIGN KEY (product_id) REFERENCES loan_products(id))");
        exec("CREATE TABLE payments (id BIGINT PRIMARY KEY AUTO_INCREMENT, loan_account_id BIGINT NOT NULL,payment_date DATE NOT NULL," +
            "total_amount DECIMAL(10,2) NOT NULL,principal_amount DECIMAL(10,2),interest_amount DECIMAL(10,2),escrow_amount DECIMAL(10,2)," +
            "late_fee DECIMAL(10,2) DEFAULT 0,type VARCHAR(15) NOT NULL,status VARCHAR(15) NOT NULL,received_date DATE,processed_date DATE," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "FOREIGN KEY (loan_account_id) REFERENCES loan_accounts(id))");
        exec("CREATE INDEX idx_borrowers_email ON borrowers(email)");
        exec("CREATE INDEX idx_borrowers_status ON borrowers(status)");
        exec("CREATE INDEX idx_loan_accounts_borrower ON loan_accounts(borrower_id)");
        exec("CREATE INDEX idx_loan_accounts_status ON loan_accounts(status)");
        exec("CREATE INDEX idx_payments_loan ON payments(loan_account_id)");
        exec("CREATE INDEX idx_payments_date ON payments(payment_date)");
    }

    static void load() throws Exception {
        c.setAutoCommit(false);
        String[] codes = {"FXD30", "FXD15", "ARM51", "FHA30", "VA30"};
        try (PreparedStatement p = c.prepareStatement("INSERT INTO loan_products(code,name,type,term_months,rate_type,min_amount,max_amount,is_active,effective_date,expiration_date) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            for (String code : codes) {
                p.setString(1, code); p.setString(2, code + " product"); p.setString(3, "FXD"); p.setInt(4, 360);
                p.setString(5, "FIXED"); p.setBigDecimal(6, new java.math.BigDecimal("0")); p.setBigDecimal(7, new java.math.BigDecimal("750000"));
                p.setBoolean(8, true); p.setDate(9, java.sql.Date.valueOf("2020-01-01")); p.setDate(10, java.sql.Date.valueOf("2099-12-31")); p.addBatch();
            }
            p.executeBatch();
        }
        long t = System.currentTimeMillis();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO borrowers(external_id,first_name,last_name,middle_initial,ssn_hash,date_of_birth,address_line1,city,state,zip_code,phone,email,credit_score,employment_status,annual_income,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (int i = 1; i <= BORROWERS; i++) {
                p.setString(1, "B-" + i); p.setString(2, "First" + i); p.setString(3, "Last" + i); p.setString(4, "A");
                p.setString(5, "ENC_XXX_" + i); p.setDate(6, java.sql.Date.valueOf("1980-01-01")); p.setString(7, i + " Elm Street");
                p.setString(8, "Springfield"); p.setString(9, "IL"); p.setString(10, "62701"); p.setString(11, "217-555-0142");
                p.setString(12, "user" + i + "@email.com"); p.setInt(13, 600 + (i % 200)); p.setString(14, "EMPLOYED");
                p.setBigDecimal(15, new java.math.BigDecimal("92500.00")); p.setString(16, i % 10 == 0 ? "INACTIVE" : "ACTIVE");
                p.addBatch();
                if (i % 10_000 == 0) { p.executeBatch(); c.commit(); }
            }
            p.executeBatch(); c.commit();
        }
        System.out.println("borrowers ms=" + (System.currentTimeMillis() - t));
        t = System.currentTimeMillis();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO loan_accounts(account_number,borrower_id,product_id,original_amount,current_balance,interest_rate,term_months,monthly_payment,origination_date,maturity_date,first_payment_date,next_payment_date,status,delinquency_days,escrow_balance,ltv_percent,property_address,property_city,property_state,property_zip,property_type,appraised_value) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (int i = 1; i <= LOANS; i++) {
                p.setString(1, "LN-2019-" + i); p.setLong(2, i); p.setLong(3, 1 + (i % 5));
                p.setBigDecimal(4, new java.math.BigDecimal("285000.00")); p.setBigDecimal(5, new java.math.BigDecimal("271432.56"));
                p.setBigDecimal(6, new java.math.BigDecimal("4.750")); p.setInt(7, 360); p.setBigDecimal(8, new java.math.BigDecimal("1487.02"));
                p.setDate(9, java.sql.Date.valueOf("2019-02-15")); p.setDate(10, java.sql.Date.valueOf("2049-02-15")); p.setDate(11, java.sql.Date.valueOf("2019-03-15"));
                p.setDate(12, java.sql.Date.valueOf("2026-01-15")); p.setString(13, i % 20 == 0 ? "CLOSED" : "ACTIVE"); p.setInt(14, 0);
                p.setBigDecimal(15, new java.math.BigDecimal("3245.80")); p.setBigDecimal(16, new java.math.BigDecimal("82.50"));
                p.setString(17, i + " Elm Street"); p.setString(18, "Springfield"); p.setString(19, "IL"); p.setString(20, "62701");
                p.setString(21, "Single Family"); p.setBigDecimal(22, new java.math.BigDecimal("345000.00"));
                p.addBatch();
                if (i % 10_000 == 0) { p.executeBatch(); c.commit(); }
            }
            p.executeBatch(); c.commit();
        }
        System.out.println("loan_accounts ms=" + (System.currentTimeMillis() - t));
        t = System.currentTimeMillis();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO payments(loan_account_id,payment_date,total_amount,principal_amount,interest_amount,escrow_amount,late_fee,type,status,received_date,processed_date) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            int n = 0;
            for (int i = 1; i <= LOANS; i++) {
                for (int k = 0; k < PMT_PER_LOAN; k++) {
                    p.setLong(1, i); p.setDate(2, java.sql.Date.valueOf("2025-0" + (k + 1) + "-15"));
                    p.setBigDecimal(3, new java.math.BigDecimal("1487.02")); p.setBigDecimal(4, new java.math.BigDecimal("456.78"));
                    p.setBigDecimal(5, new java.math.BigDecimal("1074.69")); p.setBigDecimal(6, new java.math.BigDecimal("355.55"));
                    p.setBigDecimal(7, new java.math.BigDecimal("0.00")); p.setString(8, "REGULAR"); p.setString(9, "POSTED");
                    p.setDate(10, java.sql.Date.valueOf("2025-0" + (k + 1) + "-14")); p.setDate(11, java.sql.Date.valueOf("2025-0" + (k + 1) + "-15"));
                    p.addBatch();
                    if (++n % 10_000 == 0) { p.executeBatch(); c.commit(); }
                }
            }
            p.executeBatch(); c.commit();
        }
        System.out.println("payments ms=" + (System.currentTimeMillis() - t));
        c.setAutoCommit(true);
        exec("ANALYZE");
    }

    static void counts() throws Exception {
        for (String t : new String[]{"borrowers", "loan_products", "loan_accounts", "payments"}) {
            try (ResultSet r = c.createStatement().executeQuery("SELECT COUNT(*) FROM " + t)) { r.next(); System.out.println(t + "=" + r.getLong(1)); }
        }
    }

    static void timed(String label, String sql, boolean explain) throws Exception {
        run(sql); // warm JIT
        double[] d = new double[5]; int rows = 0;
        for (int i = 0; i < 5; i++) { long t0 = System.nanoTime(); rows = run(sql); d[i] = (System.nanoTime() - t0) / 1e6; }
        Arrays.sort(d);
        System.out.printf("%-52s median %8.2f ms (min %.2f max %.2f)  rows=%d%n", label, d[2], d[0], d[4], rows);
        if (explain) {
            try (ResultSet r = c.createStatement().executeQuery("EXPLAIN ANALYZE " + sql)) {
                r.next();
                for (String line : r.getString(1).split("\n")) if (line.contains("scan") || line.contains("index") || line.contains("PUBLIC.")) System.out.println("      | " + line.trim());
            }
        }
    }

    static int run(String sql) throws Exception {
        int n = 0;
        try (ResultSet r = c.createStatement().executeQuery(sql)) { while (r.next()) n++; }
        return n;
    }

    static void queries() throws Exception {
        timed("Q1 v1 unbounded GET /api/loans (join)",
            "SELECT la.*, b.first_name, b.last_name, lp.name FROM loan_accounts la JOIN borrowers b ON b.id=la.borrower_id JOIN loan_products lp ON lp.id=la.product_id", false);
        timed("Q2 v1 unbounded GET /api/borrowers", "SELECT * FROM borrowers", false);
        timed("Q3 GET /api/loans/{id} by account_number",
            "SELECT la.*, b.first_name, b.last_name, lp.name FROM loan_accounts la JOIN borrowers b ON b.id=la.borrower_id JOIN loan_products lp ON lp.id=la.product_id WHERE la.account_number='LN-2019-499999'", true);
        timed("Q4 payments by loan ordered desc",
            "SELECT * FROM payments p JOIN loan_accounts la ON la.id=p.loan_account_id WHERE la.account_number='LN-2019-499999' ORDER BY p.payment_date DESC", true);
        timed("Q5 v2 offset page 0 size 50",
            "SELECT la.*, b.first_name, b.last_name FROM loan_accounts la JOIN borrowers b ON b.id=la.borrower_id ORDER BY la.id LIMIT 50 OFFSET 0", false);
        timed("Q6 v2 offset page deep (offset 450000)",
            "SELECT la.*, b.first_name, b.last_name FROM loan_accounts la JOIN borrowers b ON b.id=la.borrower_id ORDER BY la.id LIMIT 50 OFFSET 450000", false);
        timed("Q7 v2 keyset page deep (id > 450000)",
            "SELECT la.*, b.first_name, b.last_name FROM loan_accounts la JOIN borrowers b ON b.id=la.borrower_id WHERE la.id > 450000 ORDER BY la.id LIMIT 50", false);
        timed("Q8 COUNT(*) for Page total", "SELECT COUNT(*) FROM loan_accounts", false);
        timed("Q9 filter by status ACTIVE page",
            "SELECT la.id FROM loan_accounts la WHERE la.status='ACTIVE' ORDER BY la.id LIMIT 50", true);
        timed("Q10 batch-fetch 50 borrowers (IN clause)",
            "SELECT b.* FROM borrowers b WHERE b.id BETWEEN 1 AND 50", false);
        nPlusOne();
    }

    static void nPlusOne() throws Exception {
        long t0 = System.nanoTime();
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM borrowers WHERE id = ?")) {
            for (int i = 1; i <= 50; i++) { p.setLong(1, i); try (ResultSet r = p.executeQuery()) { r.next(); } }
        }
        System.out.printf("%-52s %8.2f ms%n", "Q11 N+1: 50 individual borrower selects", (System.nanoTime() - t0) / 1e6);
    }

    static void concurrency() throws Exception {
        System.out.println("\n================ CONCURRENCY ================");
        for (boolean hot : new boolean[]{true, false}) {
            for (int threads : new int[]{1, 4, 8, 16}) {
                int perThread = 500;
                java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();
                java.util.concurrent.atomic.AtomicLong maxWaitUs = new java.util.concurrent.atomic.AtomicLong();
                List<Thread> ts = new ArrayList<>();
                for (int th = 0; th < threads; th++) {
                    final int id = th;
                    Thread thr = new Thread(() -> {
                        try (Connection cc = DriverManager.getConnection("jdbc:h2:mem:bench;DB_CLOSE_DELAY=-1", "sa", "")) {
                            cc.setAutoCommit(false);
                            start.await();
                            for (int i = 0; i < perThread; i++) {
                                long w0 = System.nanoTime();
                                try (PreparedStatement p = cc.prepareStatement("UPDATE loan_accounts SET current_balance = current_balance - 1 WHERE id = ?")) {
                                    p.setLong(1, hot ? 1 : (id * perThread + i + 1));
                                    p.executeUpdate();
                                    cc.commit();
                                } catch (SQLException e) { errors.incrementAndGet(); cc.rollback(); }
                                long w = (System.nanoTime() - w0) / 1000;
                                maxWaitUs.accumulateAndGet(w, Math::max);
                            }
                        } catch (Exception e) { errors.incrementAndGet(); }
                    });
                    ts.add(thr); thr.start();
                }
                long t0 = System.nanoTime();
                start.countDown();
                for (Thread thr : ts) thr.join();
                double ms = (System.nanoTime() - t0) / 1e6;
                int total = threads * perThread;
                System.out.printf("%-14s threads=%2d  %6.0f ms  %8.0f writes/min  errors=%d  maxLatency=%.1f ms%n",
                    hot ? "SAME ROW" : "DISTINCT ROWS", threads, ms, total / (ms / 1000) * 60, errors.get(), maxWaitUs.get() / 1000.0);
            }
        }
    }

    static void writeBench() throws Exception {
        System.out.println("\n================ WRITE PATH ================");
        // single-row autocommit inserts (worst case: per-row commit)
        long t = System.nanoTime();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO payments(loan_account_id,payment_date,total_amount,type,status) VALUES (?,?,?,?,?)")) {
            for (int i = 0; i < 2000; i++) {
                p.setLong(1, 1 + i); p.setDate(2, java.sql.Date.valueOf("2026-01-15")); p.setBigDecimal(3, new java.math.BigDecimal("100.00"));
                p.setString(4, "REGULAR"); p.setString(5, "POSTED"); p.executeUpdate();
            }
        }
        double ms = (System.nanoTime() - t) / 1e6;
        System.out.printf("2000 single-row commits: %.1f ms total, %.3f ms/write, %.0f writes/min%n", ms, ms / 2000, 2000 / (ms / 1000) * 60);
        // read-modify-write on the same hot row (lost-update pattern, serialized)
        t = System.nanoTime();
        c.setAutoCommit(false);
        for (int i = 0; i < 2000; i++) {
            try (PreparedStatement p = c.prepareStatement("UPDATE loan_accounts SET current_balance = current_balance - 1 WHERE id = 1")) { p.executeUpdate(); }
            c.commit();
        }
        c.setAutoCommit(true);
        ms = (System.nanoTime() - t) / 1e6;
        System.out.printf("2000 hot-row balance updates (id=1): %.1f ms total, %.3f ms/write, %.0f writes/min%n", ms, ms / 2000, 2000 / (ms / 1000) * 60);
    }
}
