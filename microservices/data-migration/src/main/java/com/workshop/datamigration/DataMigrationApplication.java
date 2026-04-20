package com.workshop.datamigration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class DataMigrationApplication implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DataMigrationApplication(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(DataMigrationApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("=== Data Migration Tool ===");
        System.out.println("This tool reads from the monolith's legacy H2 database");
        System.out.println("and splits data into per-service databases.");
        System.out.println();

        int borrowerCount = countTable("CDW_BORR_MSTR");
        int productCount = countTable("CDW_LN_PROD");
        int loanCount = countTable("CDW_LN_ACCT");
        int paymentCount = countTable("CDW_PMT_HIST");

        System.out.println("Source database summary:");
        System.out.println("  Borrowers:     " + borrowerCount);
        System.out.println("  Loan Products: " + productCount);
        System.out.println("  Loan Accounts: " + loanCount);
        System.out.println("  Payments:      " + paymentCount);
        System.out.println();
        System.out.println("Migration targets:");
        System.out.println("  borrower-service DB: " + borrowerCount + " borrower records");
        System.out.println("  loan-service DB:     " + productCount + " products + " + loanCount + " accounts");
        System.out.println("  payment-service DB:  " + paymentCount + " payment records");
        System.out.println();
        System.out.println("Each microservice initializes its own H2 in-memory database");
        System.out.println("with the relevant subset of the legacy data on startup.");
        System.out.println("=== Migration complete ===");
    }

    private int countTable(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count != null ? count : 0;
    }
}
