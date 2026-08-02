package com.workshop.loanservice.migration;

import com.workshop.loanservice.config.MigrationProperties;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * What the migration does with data it cannot parse.
 *
 * <p>A warehouse migration that quietly coerces {@code "N/A"} to zero is worse than one that fails:
 * the numbers look plausible and nobody finds out until a borrower is billed the wrong amount. Every
 * unparseable row is therefore rejected with its identifier and reason, and in strict mode a single
 * rejection fails the run.
 *
 * <p>Runs in lenient mode so the context starts with the deliberately broken rows in place; the
 * strict case flips the mode for the duration of one test.
 */
@SpringBootTest(properties = "loanservice.migration.mode=lenient")
class MigrationBadDataIT {

    @Autowired
    private LegacyToModernMigrationService migration;
    @Autowired
    private MigrationProperties properties;
    @Autowired
    private LegacyBorrowerRepository legacyBorrowers;
    @Autowired
    private LegacyLoanAccountRepository legacyAccounts;
    @Autowired
    private BorrowerRepository borrowers;
    @Autowired
    private LoanAccountRepository accounts;

    @AfterEach
    void restoreMode() {
        properties.setMode("lenient");
    }

    @Test
    void anUnparseableValueRejectsItsRowWithAReasonInsteadOfGuessing() {
        legacyBorrowers.save(borrowerWith("B-BAD-1", "not-a-date", "72,000.00"));
        legacyBorrowers.save(borrowerWith("B-BAD-2", "01/02/1980", "N/A"));

        MigrationReport report = migration.migrate();

        assertThat(report.getRejections())
            .extracting(MigrationReport.Rejection::legacyId)
            .contains("B-BAD-1", "B-BAD-2");
        assertThat(borrowers.findByExternalId("B-BAD-1")).isEmpty();
        assertThat(borrowers.findByExternalId("B-BAD-2")).isEmpty();
    }

    /** The legacy warehouse has no foreign keys, so a loan can point at a borrower that never existed. */
    @Test
    void aDanglingForeignKeyIsRejectedRatherThanInventingAParent() {
        LegacyLoanAccount orphan = new LegacyLoanAccount();
        orphan.setLoanAccountNumber("LN-ORPHAN-1");
        orphan.setBorrowerId("B-NOBODY");
        orphan.setProductCode("FIXED30");
        legacyAccounts.save(orphan);

        MigrationReport report = migration.migrate();

        assertThat(report.getRejections())
            .anyMatch(r -> r.legacyId().equals("LN-ORPHAN-1") && r.reason().contains("unresolved borrower"));
        assertThat(accounts.findByAccountNumber("LN-ORPHAN-1")).isEmpty();
    }

    @Test
    void lenientModeMigratesEverythingItCanAndReportsTheRest() {
        legacyBorrowers.save(borrowerWith("B-BAD-3", "31/31/1980", "50,000.00"));

        MigrationReport report = migration.migrate();

        assertThat(report.getRejections()).isNotEmpty();
        // The healthy rows from the seed data are still there: one bad row does not stop the backfill.
        assertThat(borrowers.findByExternalId("B-10001")).isPresent();
    }

    @Test
    void strictModeFailsTheRunOnTheFirstRejection() {
        legacyBorrowers.save(borrowerWith("B-BAD-4", "12/32/1980", "50,000.00"));
        properties.setMode("strict");

        MigrationFailedException failure =
            catchThrowableOfType(() -> migration.migrate(), MigrationFailedException.class);

        assertThat(failure).isNotNull();
        assertThat(failure.getReport().getRejections())
            .extracting(MigrationReport.Rejection::legacyId)
            .contains("B-BAD-4");
    }

    /**
     * Recovery after a strict failure. Chunks that already committed stay committed - that is what
     * makes a 500k-row backfill resumable rather than an all-or-nothing gamble - so the guarantee
     * that matters is that a re-run adds no duplicates.
     */
    @Test
    void aRerunAfterAFailedStrictRunIsIdempotent() {
        legacyBorrowers.save(borrowerWith("B-BAD-5", "aa/bb/cccc", "50,000.00"));
        properties.setMode("strict");
        catchThrowableOfType(() -> migration.migrate(), MigrationFailedException.class);
        properties.setMode("lenient");
        long borrowersAfterFailure = borrowers.count();
        long accountsAfterFailure = accounts.count();

        MigrationReport rerun = migration.migrate();

        assertThat(borrowers.count()).isEqualTo(borrowersAfterFailure);
        assertThat(accounts.count()).isEqualTo(accountsAfterFailure);
        assertThat(rerun.getWritten().getOrDefault("borrowers", 0)).isZero();
    }

    private static LegacyBorrower borrowerWith(String id, String dateOfBirth, String annualIncome) {
        LegacyBorrower borrower = new LegacyBorrower();
        borrower.setBorrowerId(id);
        borrower.setFirstName("Test");
        borrower.setLastName("Borrower");
        borrower.setDateOfBirth(dateOfBirth);
        borrower.setAnnualIncome(annualIncome);
        borrower.setStatusCode("ACT");
        return borrower;
    }
}
