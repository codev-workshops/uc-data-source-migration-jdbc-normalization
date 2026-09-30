package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.workshop.loanservice.validation.LegacyDataValidator.BORROWER_TABLE;
import static com.workshop.loanservice.validation.LegacyDataValidator.LOAN_TABLE;
import static com.workshop.loanservice.validation.LegacyDataValidator.PAYMENT_TABLE;
import static com.workshop.loanservice.validation.LegacyDataValidator.PRODUCT_TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class LegacyDataValidatorTest {

    private static ValidationResult result;

    @BeforeAll
    static void runValidator(@Autowired LegacyDataValidator validator) {
        result = validator.validate();
    }

    @Test
    void validatesEverySeededRecord() {
        assertThat(result.recordIdsByTable()).containsOnlyKeys(
                BORROWER_TABLE, PRODUCT_TABLE, LOAN_TABLE, PAYMENT_TABLE);
        assertThat(result.recordIdsByTable().get(BORROWER_TABLE)).hasSize(5);
        assertThat(result.recordIdsByTable().get(PRODUCT_TABLE)).hasSize(5);
        assertThat(result.recordIdsByTable().get(LOAN_TABLE)).hasSize(5);
        assertThat(result.recordIdsByTable().get(PAYMENT_TABLE)).hasSize(10);
    }

    @Test
    void seedDataHasNoErrors() {
        assertThat(result.findings()).noneMatch(f -> f.severity() == Severity.ERROR);
    }

    @Test
    void reportsKnownSeedAnomaliesAsWarnings() {
        assertThat(result.findings())
                .extracting(ValidationFinding::table, ValidationFinding::recordId,
                        ValidationFinding::column)
                .containsExactlyInAnyOrder(
                        tuple(BORROWER_TABLE, "B-10002", "BORR_ADDR_LN2"),
                        tuple(BORROWER_TABLE, "B-10003", "BORR_ADDR_LN2"),
                        tuple(BORROWER_TABLE, "B-10005", "BORR_MID_INIT"),
                        tuple(BORROWER_TABLE, "B-10005", "BORR_ADDR_LN2"),
                        tuple(LOAN_TABLE, "LN-2018-00089", "LN_STAT_CD"),
                        tuple(PAYMENT_TABLE, "PMT-2025110001", "PMT_AMT"),
                        tuple(PAYMENT_TABLE, "PMT-2025110003", "PMT_AMT"),
                        tuple(PAYMENT_TABLE, "PMT-2025120001", "PMT_AMT"),
                        tuple(PAYMENT_TABLE, "PMT-2025120003", "PMT_LATE_FEE"));
    }

    @Test
    void flagsOrphanedLoansAndDenormalizedNameDrift(@Autowired LegacyDataValidator validator) {
        LegacyBorrower borrower = new LegacyBorrower();
        borrower.setBorrowerId("B-1");
        borrower.setFirstName("James");
        borrower.setLastName("Mitchell");
        LegacyLoanAccount drifted = loan("LN-1", "B-1", "FXD30", "Jim");
        LegacyLoanAccount orphan = loan("LN-2", "B-404", "NOPE", "James");

        List<ValidationFinding> findings = validator
                .loanRules(Map.of("B-1", borrower), Set.of("FXD30"))
                .validate(List.of(drifted, orphan));

        assertThat(findings)
                .extracting(ValidationFinding::severity, ValidationFinding::recordId,
                        ValidationFinding::column)
                .containsExactlyInAnyOrder(
                        tuple(Severity.WARNING, "LN-1", "BORR_FST_NM"),
                        tuple(Severity.ERROR, "LN-2", "BORR_ID"),
                        tuple(Severity.ERROR, "LN-2", "PROD_CD"),
                        tuple(Severity.ERROR, "LN-1", "LN_STAT_CD"),
                        tuple(Severity.ERROR, "LN-2", "LN_STAT_CD"),
                        tuple(Severity.ERROR, "LN-1", "PROP_TYP_CD"),
                        tuple(Severity.ERROR, "LN-2", "PROP_TYP_CD"));
    }

    private static LegacyLoanAccount loan(String number, String borrowerId, String product,
                                          String firstName) {
        LegacyLoanAccount loan = new LegacyLoanAccount();
        loan.setLoanAccountNumber(number);
        loan.setBorrowerId(borrowerId);
        loan.setProductCode(product);
        loan.setBorrowerFirstName(firstName);
        loan.setBorrowerLastName("Mitchell");
        return loan;
    }
}
