package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Characterisation tests for the public query methods of {@link LoanService} run against the
 * real legacy seed data ({@code schema-legacy.sql} / {@code data-legacy.sql}) in H2.
 *
 * <p>Pins the current behaviour of {@code getAllLoans}, {@code getLoanById},
 * {@code getAllBorrowers}, {@code getBorrowerById} and {@code getPaymentsByLoan}, including the
 * manual application-level product join and the bare {@link RuntimeException} thrown on the
 * not-found paths (there is no FK / no dedicated exception type in the legacy design).
 *
 * <p>Uses its own in-memory H2 database name so the legacy schema script can run independently
 * of any other Spring context cached in the same JVM.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:querylegacydw;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class LoanServiceQueryTest {

    @Autowired
    private LoanService service;

    // ---------------------------------------------------------------------------------------
    // getAllLoans
    // ---------------------------------------------------------------------------------------

    @Test
    void getAllLoansReturnsEverySeededLoan() {
        List<LoanSummaryDto> loans = service.getAllLoans();
        assertThat(loans).extracting(LoanSummaryDto::getLoanAccountNumber)
                .containsExactlyInAnyOrder(
                        "LN-2019-00142", "LN-2020-00398", "LN-2018-00089",
                        "LN-2021-00567", "LN-2017-00034");
    }

    @Test
    void getAllLoansJoinsProductDescriptionManuallyForEveryLoan() {
        List<LoanSummaryDto> loans = service.getAllLoans();
        assertThat(loans).extracting(LoanSummaryDto::getLoanAccountNumber, LoanSummaryDto::getProductDescription)
                .containsExactlyInAnyOrder(
                        tuple("LN-2019-00142", "30-Year Fixed Rate Mortgage"),
                        tuple("LN-2020-00398", "15-Year Fixed Rate Mortgage"),
                        tuple("LN-2018-00089", "5/1 Adjustable Rate Mortgage"),
                        tuple("LN-2021-00567", "30-Year Fixed Rate Mortgage"),
                        tuple("LN-2017-00034", "FHA 30-Year Fixed"));
    }

    @Test
    void getAllLoansTranslatesEveryFieldOfSeededLoan() {
        LoanSummaryDto loan = service.getAllLoans().stream()
                .filter(l -> l.getLoanAccountNumber().equals("LN-2019-00142"))
                .findFirst().orElseThrow();
        assertSeedLoan142(loan);
    }

    // ---------------------------------------------------------------------------------------
    // getLoanById
    // ---------------------------------------------------------------------------------------

    @Test
    void getLoanByIdTranslatesSeededLoan() {
        assertSeedLoan142(service.getLoanById("LN-2019-00142"));
    }

    @Test
    void getLoanByIdJoinsProductViaSeparateLookup() {
        assertThat(service.getLoanById("LN-2018-00089").getProductDescription())
                .isEqualTo("5/1 Adjustable Rate Mortgage");
        assertThat(service.getLoanById("LN-2017-00034").getProductDescription())
                .isEqualTo("FHA 30-Year Fixed");
    }

    @Test
    void getLoanByIdIsCaseSensitiveOnKey() {
        assertThatThrownBy(() -> service.getLoanById("ln-2019-00142"))
                .isExactlyInstanceOf(RuntimeException.class);
    }

    @Test
    void getLoanByIdUnknownThrowsBareRuntimeException() {
        assertThatThrownBy(() -> service.getLoanById("UNKNOWN"))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("Loan not found: UNKNOWN");
    }

    // ---------------------------------------------------------------------------------------
    // getAllBorrowers
    // ---------------------------------------------------------------------------------------

    @Test
    void getAllBorrowersReturnsEverySeededBorrowerWithoutLoans() {
        List<BorrowerDto> borrowers = service.getAllBorrowers();
        assertThat(borrowers).extracting(BorrowerDto::getId)
                .containsExactlyInAnyOrder("B-10001", "B-10002", "B-10003", "B-10004", "B-10005");
        assertThat(borrowers).extracting(BorrowerDto::getLoans).containsOnlyNulls();
    }

    @Test
    void getAllBorrowersBuildsFullNames() {
        assertThat(service.getAllBorrowers()).extracting(BorrowerDto::getFullName)
                .containsExactlyInAnyOrder(
                        "James R. Mitchell", "Sarah L. Chen", "Michael A. Torres",
                        "Emily M. Johnson", "Robert Williams");
    }

    // ---------------------------------------------------------------------------------------
    // getBorrowerById
    // ---------------------------------------------------------------------------------------

    @Test
    void getBorrowerByIdTranslatesSeededBorrower() {
        BorrowerDto b = service.getBorrowerById("B-10001");
        assertThat(b.getId()).isEqualTo("B-10001");
        assertThat(b.getFullName()).isEqualTo("James R. Mitchell");
        assertThat(b.getEmail()).isEqualTo("j.mitchell@email.com");
        assertThat(b.getPhone()).isEqualTo("217-555-0142");
        assertThat(b.getCity()).isEqualTo("Springfield");
        assertThat(b.getState()).isEqualTo("IL");
        assertThat(b.getCreditScore()).isEqualTo(745);
        assertThat(b.getEmploymentStatus()).isEqualTo("EMPLOYED");
    }

    @Test
    void getBorrowerByIdAttachesLoansWithProductJoin() {
        BorrowerDto b = service.getBorrowerById("B-10001");
        assertThat(b.getLoans()).hasSize(1);
        assertSeedLoan142(b.getLoans().get(0));
    }

    @Test
    void getBorrowerByIdWithNullMiddleInitialOmitsIt() {
        BorrowerDto b = service.getBorrowerById("B-10005");
        assertThat(b.getFullName()).isEqualTo("Robert Williams");
        assertThat(b.getEmploymentStatus()).isEqualTo("RETIRED");
        assertThat(b.getCreditScore()).isEqualTo(658);
        assertThat(b.getLoans()).extracting(LoanSummaryDto::getLoanAccountNumber)
                .containsExactly("LN-2017-00034");
    }

    @Test
    void getBorrowerByIdUnknownThrowsBareRuntimeException() {
        assertThatThrownBy(() -> service.getBorrowerById("UNKNOWN"))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("Borrower not found: UNKNOWN");
    }

    // ---------------------------------------------------------------------------------------
    // getPaymentsByLoan
    // ---------------------------------------------------------------------------------------

    @Test
    void getPaymentsByLoanReturnsPaymentsNewestDateFirst() {
        List<PaymentDto> payments = service.getPaymentsByLoan("LN-2019-00142");
        assertThat(payments).extracting(PaymentDto::getPaymentId)
                .containsExactly("PMT-2025120001", "PMT-2025110001");
        assertThat(payments).extracting(PaymentDto::getPaymentDate)
                .containsExactly("12/15/2025", "11/15/2025");
    }

    @Test
    void getPaymentsByLoanTranslatesEveryField() {
        PaymentDto p = service.getPaymentsByLoan("LN-2018-00089").get(1);
        assertThat(p.getPaymentId()).isEqualTo("PMT-2025110003");
        assertThat(p.getLoanAccountNumber()).isEqualTo("LN-2018-00089");
        assertThat(p.getPaymentDate()).isEqualTo("11/01/2025");
        assertThat(p.getTotalAmount()).isEqualTo(new BigDecimal("1077.05"));
        assertThat(p.getPrincipalAmount()).isEqualTo(new BigDecimal("295.82"));
        assertThat(p.getInterestAmount()).isEqualTo(new BigDecimal("781.23"));
        assertThat(p.getEscrowAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(p.getLateFee()).isEqualTo(new BigDecimal("47.50"));
        assertThat(p.getType()).isEqualTo("Regular");
        assertThat(p.getStatus()).isEqualTo("Posted");
    }

    @Test
    void getPaymentsByLoanUnknownReturnsEmptyListNotException() {
        assertThat(service.getPaymentsByLoan("UNKNOWN")).isEmpty();
    }

    // ---------------------------------------------------------------------------------------

    private static void assertSeedLoan142(LoanSummaryDto loan) {
        assertThat(loan.getLoanAccountNumber()).isEqualTo("LN-2019-00142");
        assertThat(loan.getBorrowerName()).isEqualTo("James Mitchell");
        assertThat(loan.getProductDescription()).isEqualTo("30-Year Fixed Rate Mortgage");
        assertThat(loan.getOriginalAmount()).isEqualTo(new BigDecimal("285000"));
        assertThat(loan.getCurrentBalance()).isEqualTo(new BigDecimal("271432.56"));
        assertThat(loan.getInterestRate()).isEqualTo(new BigDecimal("4.750"));
        assertThat(loan.getMonthlyPayment()).isEqualTo(new BigDecimal("1487.02"));
        assertThat(loan.getStatus()).isEqualTo("Active");
        assertThat(loan.getOriginationDate()).isEqualTo("02/15/2019");
        assertThat(loan.getPropertyAddress()).isEqualTo("742 Elm Street, Springfield, IL 62701");
        assertThat(loan.getPropertyType()).isEqualTo("Single Family Residence");
    }
}
