package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
    "app.migration.enabled=true",
    "app.service.mode=modern"
})
class DataMigrationValidationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testBorrowerCountMatches() {
        int legacyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CDW_BORR_MSTR", Integer.class);
        int modernCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM borrowers", Integer.class);
        assertEquals(legacyCount, modernCount, "Borrower row counts should match");
    }

    @Test
    void testLoanProductCountMatches() {
        int legacyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CDW_LN_PROD", Integer.class);
        int modernCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM loan_products", Integer.class);
        assertEquals(legacyCount, modernCount, "Loan product row counts should match");
    }

    @Test
    void testLoanAccountCountMatches() {
        int legacyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CDW_LN_ACCT", Integer.class);
        int modernCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM loan_accounts", Integer.class);
        assertEquals(legacyCount, modernCount, "Loan account row counts should match");
    }

    @Test
    void testPaymentCountMatches() {
        int legacyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CDW_PMT_HIST", Integer.class);
        int modernCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class);
        assertEquals(legacyCount, modernCount, "Payment row counts should match");
    }

    @Test
    void testBorrowerDataIntegrity() {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            "SELECT b.external_id, b.first_name, l.BORR_FST_NM, b.last_name, l.BORR_LST_NM, " +
            "b.credit_score, CAST(l.BORR_CRDT_SCR AS INTEGER) AS legacy_credit_score " +
            "FROM borrowers b JOIN CDW_BORR_MSTR l ON b.external_id = l.BORR_ID"
        );
        for (Map<String, Object> row : results) {
            assertEquals(row.get("BORR_FST_NM"), row.get("FIRST_NAME"),
                "First name mismatch for " + row.get("EXTERNAL_ID"));
            assertEquals(row.get("BORR_LST_NM"), row.get("LAST_NAME"),
                "Last name mismatch for " + row.get("EXTERNAL_ID"));
            assertEquals(row.get("LEGACY_CREDIT_SCORE"), row.get("CREDIT_SCORE"),
                "Credit score mismatch for " + row.get("EXTERNAL_ID"));
        }
    }

    @Test
    void testAmountConversions() {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            "SELECT la.account_number, la.original_amount, " +
            "CAST(REPLACE(ll.LN_ORIG_AMT, ',', '') AS DECIMAL(12,2)) AS legacy_parsed " +
            "FROM loan_accounts la JOIN CDW_LN_ACCT ll ON la.account_number = ll.LN_ACCT_NBR"
        );
        for (Map<String, Object> row : results) {
            assertEquals(0, ((java.math.BigDecimal) row.get("ORIGINAL_AMOUNT")).compareTo(
                (java.math.BigDecimal) row.get("LEGACY_PARSED")),
                "Amount mismatch for " + row.get("ACCOUNT_NUMBER"));
        }
    }

    @Test
    void testDateConversions() {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            "SELECT b.external_id, b.date_of_birth, " +
            "CAST(PARSEDATETIME(l.BORR_DOB_DT, 'MM/dd/yyyy') AS DATE) AS legacy_dob " +
            "FROM borrowers b JOIN CDW_BORR_MSTR l ON b.external_id = l.BORR_ID"
        );
        for (Map<String, Object> row : results) {
            assertEquals(row.get("LEGACY_DOB").toString(), row.get("DATE_OF_BIRTH").toString(),
                "Date mismatch for " + row.get("EXTERNAL_ID"));
        }
    }

    @Test
    void testForeignKeyIntegrity() {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            "SELECT la.account_number, b.external_id, ll.BORR_ID " +
            "FROM loan_accounts la " +
            "JOIN borrowers b ON la.borrower_id = b.id " +
            "JOIN CDW_LN_ACCT ll ON la.account_number = ll.LN_ACCT_NBR"
        );
        for (Map<String, Object> row : results) {
            assertEquals(row.get("BORR_ID"), row.get("EXTERNAL_ID"),
                "FK integrity mismatch for loan " + row.get("ACCOUNT_NUMBER"));
        }
    }

    @Test
    void testPaymentForeignKeys() {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            "SELECT p.legacy_payment_id, la.account_number, lp.LN_ACCT_NBR " +
            "FROM payments p " +
            "JOIN loan_accounts la ON p.loan_account_id = la.id " +
            "JOIN CDW_PMT_HIST lp ON p.legacy_payment_id = lp.PMT_SEQ_NBR"
        );
        assertTrue(!results.isEmpty(), "Should have payment results");
        for (Map<String, Object> row : results) {
            assertEquals(row.get("LN_ACCT_NBR"), row.get("ACCOUNT_NUMBER"),
                "Payment FK mismatch for " + row.get("LEGACY_PAYMENT_ID"));
        }
    }
}
