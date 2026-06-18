package com.workshop.loanservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.migration.MigrationService;
import com.workshop.loanservice.service.LegacyLoanDataProvider;
import com.workshop.loanservice.service.ModernLoanDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconciliation that drives BOTH providers directly (no HTTP) within a single
 * context and asserts they produce identical API output, plus that monetary
 * totals reconcile across the two data sources. Complements the HTTP golden
 * tests by comparing legacy and modern to each other (not just to the golden
 * files), so any divergence is caught regardless of the captured baseline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CrossSourceReconciliationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private LegacyLoanDataProvider legacy;

    @Autowired
    private ModernLoanDataProvider modern;

    @Autowired
    private MigrationService migrationService;

    @BeforeEach
    void migrate() {
        migrationService.migrate();
    }

    @Test
    void allLoansIdenticalAcrossSources() {
        assertIdentical(legacy.getAllLoans(), modern.getAllLoans(), "GET /api/loans");
    }

    @Test
    void loanByIdIdenticalAcrossSources() {
        assertIdentical(legacy.getLoanById("LN-2019-00142"),
                modern.getLoanById("LN-2019-00142"), "GET /api/loans/{id}");
    }

    @Test
    void allBorrowersIdenticalAcrossSources() {
        assertIdentical(legacy.getAllBorrowers(), modern.getAllBorrowers(), "GET /api/borrowers");
    }

    @Test
    void borrowerByIdIdenticalAcrossSources() {
        assertIdentical(legacy.getBorrowerById("B-10001"),
                modern.getBorrowerById("B-10001"), "GET /api/borrowers/{id}");
    }

    @Test
    void paymentsIdenticalAcrossSources() {
        assertIdentical(legacy.getPaymentsByLoan("LN-2019-00142"),
                modern.getPaymentsByLoan("LN-2019-00142"), "GET /api/loans/{id}/payments");
    }

    @Test
    void loanMonetaryTotalsReconcile() {
        List<LoanSummaryDto> legacyLoans = legacy.getAllLoans();
        List<LoanSummaryDto> modernLoans = modern.getAllLoans();

        assertThat(sum(legacyLoans, LoanSummaryDto::getOriginalAmount))
                .isEqualByComparingTo(sum(modernLoans, LoanSummaryDto::getOriginalAmount));
        assertThat(sum(legacyLoans, LoanSummaryDto::getCurrentBalance))
                .isEqualByComparingTo(sum(modernLoans, LoanSummaryDto::getCurrentBalance));
        assertThat(sum(legacyLoans, LoanSummaryDto::getMonthlyPayment))
                .isEqualByComparingTo(sum(modernLoans, LoanSummaryDto::getMonthlyPayment));
    }

    @Test
    void paymentMonetaryTotalsReconcile() {
        for (String loanId : List.of("LN-2019-00142", "LN-2020-00398", "LN-2018-00089",
                "LN-2021-00567", "LN-2017-00034")) {
            List<PaymentDto> legacyPmts = legacy.getPaymentsByLoan(loanId);
            List<PaymentDto> modernPmts = modern.getPaymentsByLoan(loanId);
            assertThat(sum(modernPmts, PaymentDto::getTotalAmount))
                    .as("total payments for %s", loanId)
                    .isEqualByComparingTo(sum(legacyPmts, PaymentDto::getTotalAmount));
        }
    }

    private <T> void assertIdentical(T legacyValue, T modernValue, String endpoint) {
        try {
            List<String> diffs = JsonCompare.diff(
                    MAPPER.valueToTree(legacyValue), MAPPER.valueToTree(modernValue));
            assertThat(diffs)
                    .as("legacy vs modern output for %s", endpoint)
                    .isEmpty();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compare " + endpoint, e);
        }
    }

    private <T> BigDecimal sum(List<T> items, java.util.function.Function<T, BigDecimal> field) {
        return items.stream()
                .map(field)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
