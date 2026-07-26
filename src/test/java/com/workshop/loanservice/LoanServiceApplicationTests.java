package com.workshop.loanservice;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-layer characterization tests for {@link LoanService}.
 *
 * These assertions describe the API contract produced from the legacy data
 * source and must keep passing unchanged after the migration to the modern
 * schema — they are the proof that the migration is behaviour preserving.
 */
@SpringBootTest
class LoanServiceApplicationTests {

    @Autowired
    private LoanService loanService;

    @Test
    void contextLoads() {
        assertThat(loanService).isNotNull();
    }

    @Test
    void getAllLoansReturnsEveryLoanWithTranslatedFields() {
        List<LoanSummaryDto> loans = loanService.getAllLoans();

        assertThat(loans).hasSize(5);
        assertThat(loans).extracting(LoanSummaryDto::getLoanAccountNumber)
                .containsExactlyInAnyOrder("LN-2019-00142", "LN-2020-00398",
                        "LN-2018-00089", "LN-2021-00567", "LN-2017-00034");

        Map<String, LoanSummaryDto> byNumber = loans.stream()
                .collect(Collectors.toMap(LoanSummaryDto::getLoanAccountNumber, Function.identity()));

        LoanSummaryDto mitchell = byNumber.get("LN-2019-00142");
        assertThat(mitchell.getBorrowerName()).isEqualTo("James Mitchell");
        assertThat(mitchell.getProductDescription()).isEqualTo("30-Year Fixed Rate Mortgage");
        assertThat(mitchell.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(mitchell.getCurrentBalance()).isEqualByComparingTo("271432.56");
        assertThat(mitchell.getInterestRate()).isEqualByComparingTo("4.750");
        assertThat(mitchell.getMonthlyPayment()).isEqualByComparingTo("1487.02");
        assertThat(mitchell.getStatus()).isEqualTo("Active");
        assertThat(mitchell.getOriginationDate()).isEqualTo("02/15/2019");
        assertThat(mitchell.getPropertyAddress()).isEqualTo("742 Elm Street, Springfield, IL 62701");
        assertThat(mitchell.getPropertyType()).isEqualTo("Single Family Residence");

        assertThat(byNumber.get("LN-2020-00398").getPropertyType()).isEqualTo("Condominium");
        assertThat(byNumber.get("LN-2021-00567").getPropertyType()).isEqualTo("Townhouse");
        assertThat(byNumber.get("LN-2020-00398").getProductDescription())
                .isEqualTo("15-Year Fixed Rate Mortgage");
        assertThat(byNumber.get("LN-2018-00089").getProductDescription())
                .isEqualTo("5/1 Adjustable Rate Mortgage");
        assertThat(byNumber.get("LN-2017-00034").getProductDescription())
                .isEqualTo("FHA 30-Year Fixed");
    }

    @Test
    void getLoanByIdReturnsSingleLoan() {
        LoanSummaryDto loan = loanService.getLoanById("LN-2018-00089");

        assertThat(loan.getLoanAccountNumber()).isEqualTo("LN-2018-00089");
        assertThat(loan.getBorrowerName()).isEqualTo("Michael Torres");
        assertThat(loan.getProductDescription()).isEqualTo("5/1 Adjustable Rate Mortgage");
        assertThat(loan.getOriginalAmount()).isEqualByComparingTo("195000");
        assertThat(loan.getCurrentBalance()).isEqualByComparingTo("178234.12");
        assertThat(loan.getInterestRate()).isEqualByComparingTo("5.250");
        assertThat(loan.getMonthlyPayment()).isEqualByComparingTo("1077.05");
        assertThat(loan.getOriginationDate()).isEqualTo("07/01/2018");
        assertThat(loan.getPropertyAddress()).isEqualTo("305 Pine Road, Austin, TX 78701");
    }

    @Test
    void getLoanByIdFailsForUnknownLoan() {
        assertThatThrownBy(() -> loanService.getLoanById("LN-DOES-NOT-EXIST"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LN-DOES-NOT-EXIST");
    }

    @Test
    void getAllBorrowersReturnsTranslatedBorrowers() {
        List<BorrowerDto> borrowers = loanService.getAllBorrowers();

        assertThat(borrowers).hasSize(5);

        Map<String, BorrowerDto> byId = borrowers.stream()
                .collect(Collectors.toMap(BorrowerDto::getId, Function.identity()));
        assertThat(byId.keySet()).containsExactlyInAnyOrder("B-10001", "B-10002",
                "B-10003", "B-10004", "B-10005");

        BorrowerDto james = byId.get("B-10001");
        assertThat(james.getFullName()).isEqualTo("James R. Mitchell");
        assertThat(james.getEmail()).isEqualTo("j.mitchell@email.com");
        assertThat(james.getPhone()).isEqualTo("217-555-0142");
        assertThat(james.getCity()).isEqualTo("Springfield");
        assertThat(james.getState()).isEqualTo("IL");
        assertThat(james.getCreditScore()).isEqualTo(745);
        assertThat(james.getEmploymentStatus()).isEqualTo("EMPLOYED");
        assertThat(james.getLoans()).isNull();

        // Borrower without a middle initial must not gain stray whitespace.
        assertThat(byId.get("B-10005").getFullName()).isEqualTo("Robert Williams");
        assertThat(byId.get("B-10005").getEmploymentStatus()).isEqualTo("RETIRED");
        assertThat(byId.get("B-10003").getCreditScore()).isEqualTo(692);
    }

    @Test
    void getBorrowerByIdAttachesLoans() {
        BorrowerDto borrower = loanService.getBorrowerById("B-10002");

        assertThat(borrower.getId()).isEqualTo("B-10002");
        assertThat(borrower.getFullName()).isEqualTo("Sarah L. Chen");
        assertThat(borrower.getCreditScore()).isEqualTo(780);
        assertThat(borrower.getLoans()).hasSize(1);

        LoanSummaryDto loan = borrower.getLoans().get(0);
        assertThat(loan.getLoanAccountNumber()).isEqualTo("LN-2020-00398");
        assertThat(loan.getBorrowerName()).isEqualTo("Sarah Chen");
        assertThat(loan.getCurrentBalance()).isEqualByComparingTo("312876.43");
        assertThat(loan.getStatus()).isEqualTo("Active");
    }

    @Test
    void getBorrowerByIdFailsForUnknownBorrower() {
        assertThatThrownBy(() -> loanService.getBorrowerById("B-99999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("B-99999");
    }

    @Test
    void getPaymentsByLoanReturnsPaymentsNewestFirst() {
        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2019-00142");

        assertThat(payments).hasSize(2);
        assertThat(payments).extracting(PaymentDto::getPaymentDate)
                .containsExactly("12/15/2025", "11/15/2025");

        PaymentDto latest = payments.get(0);
        assertThat(latest.getPaymentId()).isEqualTo("PMT-2025120001");
        assertThat(latest.getLoanAccountNumber()).isEqualTo("LN-2019-00142");
        assertThat(latest.getTotalAmount()).isEqualByComparingTo("1487.02");
        assertThat(latest.getPrincipalAmount()).isEqualByComparingTo("456.78");
        assertThat(latest.getInterestAmount()).isEqualByComparingTo("1074.69");
        assertThat(latest.getEscrowAmount()).isEqualByComparingTo("355.55");
        assertThat(latest.getLateFee()).isEqualByComparingTo("0.00");
        assertThat(latest.getType()).isEqualTo("Regular");
        assertThat(latest.getStatus()).isEqualTo("Posted");
    }

    @Test
    void getPaymentsByLoanKeepsLateFees() {
        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2018-00089");

        assertThat(payments).hasSize(2);
        assertThat(payments.get(1).getPaymentId()).isEqualTo("PMT-2025110003");
        assertThat(payments.get(1).getLateFee()).isEqualByComparingTo("47.50");
        assertThat(payments.get(0).getLateFee()).isEqualByComparingTo("0.00");
    }

    @Test
    void getPaymentsByLoanReturnsEmptyListForUnknownLoan() {
        assertThat(loanService.getPaymentsByLoan("LN-DOES-NOT-EXIST")).isEmpty();
    }

    @Test
    void allLoansHaveTenPaymentsInTotal() {
        int total = loanService.getAllLoans().stream()
                .mapToInt(loan -> loanService.getPaymentsByLoan(loan.getLoanAccountNumber()).size())
                .sum();

        assertThat(total).isEqualTo(10);
    }

    @Test
    void everyBorrowerHasExactlyOneLoan() {
        for (BorrowerDto summary : loanService.getAllBorrowers()) {
            BorrowerDto detail = loanService.getBorrowerById(summary.getId());
            assertThat(detail.getLoans()).hasSize(1);
            assertThat(detail.getLoans().get(0).getStatus()).isEqualTo("Active");
        }
    }
}
