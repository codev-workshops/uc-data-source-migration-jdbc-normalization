import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Builds the 500,000-loan legacy warehouse the load test runs against.
 *
 * <p>It writes the <em>legacy</em> CDW tables, not the modern ones, because the migration itself is
 * one of the workloads being measured: the load test starts the application against this file
 * database and lets the backfill produce the modern side, exactly as a real cutover would.
 *
 * <p>Everything here is deliberately in the legacy shape - dates as {@code MM/DD/YYYY} strings,
 * amounts as comma-formatted strings, codes rather than canonical values - so the parser and the
 * translator are on the measured path rather than bypassed.
 *
 * <p>Usage: {@code java perf/GenerateLoadData.java [output-dir] [loans]}
 */
public class GenerateLoadData {

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String[] PRODUCT_CODES = {"FXD30", "FXD15", "ARM51", "FHA30", "VA30"};
    private static final String[] LOAN_STATUS = {"ACT", "ACT", "ACT", "ACT", "DFT", "CLO", "FRB"};
    private static final String[] PROPERTY_TYPES = {"SFR", "CND", "MFR", "TWN"};
    private static final int PAYMENTS_PER_LOAN = 4;
    private static final int BATCH = 5_000;

    public static void main(String[] args) throws Exception {
        String outputDir = args.length > 0 ? args[0] : "perf/data";
        int loans = args.length > 1 ? Integer.parseInt(args[1]) : 500_000;
        String url = "jdbc:h2:file:./" + outputDir + "/legacydw;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            if (alreadyPopulated(connection, loans)) {
                System.out.println("Warehouse already populated at " + outputDir + " - nothing to do.");
                return;
            }
            long start = System.currentTimeMillis();
            createSchema(connection);
            connection.setAutoCommit(false);
            insertProducts(connection);
            insertBorrowers(connection, loans);
            insertLoans(connection, loans);
            insertPayments(connection, loans);
            connection.commit();
            System.out.printf("Generated %,d borrowers, %,d loans, %,d payments in %,d ms%n",
                loans, loans, loans * PAYMENTS_PER_LOAN, System.currentTimeMillis() - start);
        }
    }

    private static boolean alreadyPopulated(Connection connection, int expectedLoans) {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM CDW_LN_ACCT")) {
            return rows.next() && rows.getInt(1) >= expectedLoans;
        } catch (Exception tableDoesNotExistYet) {
            return false;
        }
    }

    private static void createSchema(Connection connection) throws Exception {
        exec(connection, "DROP TABLE IF EXISTS CDW_PMT_HIST");
        exec(connection, "DROP TABLE IF EXISTS CDW_LN_ACCT");
        exec(connection, "DROP TABLE IF EXISTS CDW_LN_PROD");
        exec(connection, "DROP TABLE IF EXISTS CDW_BORR_MSTR");
        for (String statement : readSchema().split(";")) {
            if (!statement.isBlank()) {
                exec(connection, statement);
            }
        }
    }

    private static String readSchema() throws Exception {
        return new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Path.of("src/main/resources/schema-legacy.sql")));
    }

    private static void exec(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void insertProducts(Connection connection) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO CDW_LN_PROD VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            for (String code : PRODUCT_CODES) {
                insert.setString(1, code);
                insert.setString(2, code + " mortgage product");
                insert.setString(3, "FXD");
                insert.setString(4, "360");
                insert.setString(5, "FIXED");
                insert.setString(6, "0.00");
                insert.setString(7, "750,000.00");
                insert.setString(8, "ACT");
                insert.setString(9, "01/01/2015");
                insert.setString(10, "12/31/2099");
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void insertBorrowers(Connection connection, int count) throws Exception {
        Random random = new Random(42);
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO CDW_BORR_MSTR VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (int i = 1; i <= count; i++) {
                int column = 0;
                insert.setString(++column, "B-" + (100000 + i));
                insert.setString(++column, "First" + i);
                insert.setString(++column, "Last" + i);
                insert.setString(++column, "X");
                insert.setString(++column, "ENC_" + Integer.toHexString(i * 2654435761L != 0 ? i * 31 : i));
                insert.setString(++column, LocalDate.of(1950 + random.nextInt(45), 1 + random.nextInt(12),
                    1 + random.nextInt(28)).format(LEGACY_DATE));
                insert.setString(++column, i + " Main St");
                insert.setString(++column, null);
                insert.setString(++column, "Springfield");
                insert.setString(++column, "TX");
                insert.setString(++column, "7" + String.format("%04d", i % 10000));
                insert.setString(++column, "555-01" + String.format("%05d", i % 100000));
                insert.setString(++column, "borrower" + i + "@example.com");
                insert.setString(++column, String.valueOf(580 + random.nextInt(240)));
                insert.setString(++column, "EMPLOYED");
                insert.setString(++column, money(40_000 + random.nextInt(160_000)));
                insert.setString(++column, "01/15/2018");
                insert.setString(++column, "06/30/2024");
                insert.setString(++column, "ACT");
                insert.setString(++column, "MSTR");
                insert.addBatch();
                if (i % BATCH == 0) {
                    insert.executeBatch();
                }
            }
            insert.executeBatch();
        }
    }

    private static void insertLoans(Connection connection, int count) throws Exception {
        Random random = new Random(43);
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO CDW_LN_ACCT VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (int i = 1; i <= count; i++) {
                int original = 100_000 + random.nextInt(650_000);
                int balance = (int) (original * (0.4 + random.nextDouble() * 0.6));
                LocalDate origination = LocalDate.of(2005 + random.nextInt(19), 1 + random.nextInt(12), 1 + random.nextInt(28));
                int column = 0;
                insert.setString(++column, accountNumber(i));
                insert.setString(++column, "B-" + (100000 + i));
                insert.setString(++column, "First" + i);
                insert.setString(++column, "Last" + i);
                insert.setString(++column, String.format("%04d", i % 10000));
                insert.setString(++column, PRODUCT_CODES[i % PRODUCT_CODES.length]);
                insert.setString(++column, money(original));
                insert.setString(++column, money(balance));
                insert.setString(++column, String.format("%.3f", 2.5 + random.nextDouble() * 5));
                insert.setString(++column, "360");
                insert.setString(++column, money(original / 240));
                insert.setString(++column, origination.format(LEGACY_DATE));
                insert.setString(++column, origination.plusYears(30).format(LEGACY_DATE));
                insert.setString(++column, origination.plusMonths(1).format(LEGACY_DATE));
                insert.setString(++column, LocalDate.of(2026, 1, 1).format(LEGACY_DATE));
                insert.setString(++column, LOAN_STATUS[i % LOAN_STATUS.length]);
                insert.setString(++column, String.valueOf(random.nextInt(4) == 0 ? random.nextInt(90) : 0));
                insert.setString(++column, money(random.nextInt(8000)));
                insert.setString(++column, String.format("%.2f", 40 + random.nextDouble() * 55));
                insert.setString(++column, i + " Property Ave");
                insert.setString(++column, "Springfield");
                insert.setString(++column, "TX");
                insert.setString(++column, "7" + String.format("%04d", i % 10000));
                insert.setString(++column, PROPERTY_TYPES[i % PROPERTY_TYPES.length]);
                insert.setString(++column, money(original + random.nextInt(120_000)));
                insert.setString(++column, "01/15/2018");
                insert.setString(++column, "06/30/2024");
                insert.addBatch();
                if (i % BATCH == 0) {
                    insert.executeBatch();
                }
            }
            insert.executeBatch();
        }
    }

    private static void insertPayments(Connection connection, int loans) throws Exception {
        Random random = new Random(44);
        String[] typeCodes = {"REG", "REG", "REG", "EXT", "PRT"};
        String[] statusCodes = {"PST", "PST", "PST", "PST", "NSF"};
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO CDW_PMT_HIST VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            long sequence = 0;
            for (int loan = 1; loan <= loans; loan++) {
                for (int n = 0; n < PAYMENTS_PER_LOAN; n++) {
                    LocalDate paidOn = LocalDate.of(2025, 1 + (n * 3) % 12, 15);
                    int total = 1_200 + random.nextInt(2_400);
                    int column = 0;
                    insert.setString(++column, "PMT-" + String.format("%011d", ++sequence));
                    insert.setString(++column, accountNumber(loan));
                    insert.setString(++column, paidOn.format(LEGACY_DATE));
                    insert.setString(++column, money(total));
                    insert.setString(++column, money(total / 3));
                    insert.setString(++column, money(total / 2));
                    insert.setString(++column, money(total / 8));
                    insert.setString(++column, "0.00");
                    insert.setString(++column, typeCodes[(loan + n) % typeCodes.length]);
                    insert.setString(++column, statusCodes[(loan + n) % statusCodes.length]);
                    insert.setString(++column, paidOn.format(LEGACY_DATE));
                    insert.setString(++column, paidOn.format(LEGACY_DATE));
                    insert.setString(++column, paidOn.format(LEGACY_DATE));
                    insert.setString(++column, paidOn.format(LEGACY_DATE));
                    insert.addBatch();
                }
                if (loan % (BATCH / PAYMENTS_PER_LOAN) == 0) {
                    insert.executeBatch();
                }
            }
            insert.executeBatch();
        }
    }

    /** Matches the legacy identifier shape, and stays inside the 20-character column. */
    private static String accountNumber(int i) {
        return "LN-2020-" + String.format("%08d", i);
    }

    /** Legacy amounts are comma-formatted strings, which is exactly what the parser has to cope with. */
    private static String money(long value) {
        return String.format("%,d.00", value);
    }

    private static String money(BigDecimal value) {
        return String.format("%,.2f", value);
    }
}
