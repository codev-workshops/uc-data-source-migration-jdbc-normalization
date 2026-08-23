package com.workshop.loanservice;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level behaviour against the modern schema (no mocks).
 */
@SpringBootTest
class LoanServiceTest {

    @Autowired
    private LoanService loanService;

    @Test
    void listsLoansWithBorrowerAndProductResolvedThroughForeignKeys() {
        List<LoanSummaryDto> loans = loanService.getAllLoans();

        assertThat(loans).extracting(LoanSummaryDto::getLoanAccountNumber)
                .containsExactly("LN-2019-00142", "LN-2020-00398", "LN-2018-00089",
                        "LN-2021-00567", "LN-2017-00034");
        assertThat(loans).extracting(LoanSummaryDto::getBorrowerName)
                .containsExactly("James Mitchell", "Sarah Chen", "Michael Torres",
                        "Emily Johnson", "Robert Williams");
        assertThat(loans).extracting(LoanSummaryDto::getProductDescription)
                .containsExactly("30-Year Fixed Rate Mortgage", "15-Year Fixed Rate Mortgage",
                        "5/1 Adjustable Rate Mortgage", "30-Year Fixed Rate Mortgage", "FHA 30-Year Fixed");
        assertThat(loans).allSatisfy(loan -> assertThat(loan.getStatus()).isEqualTo("Active"));
    }

    @Test
    void retrievesASingleLoan() {
        LoanSummaryDto loan = loanService.getLoanById("LN-2020-00398");

        assertThat(loan.getBorrowerName()).isEqualTo("Sarah Chen");
        assertThat(loan.getCurrentBalance()).isEqualByComparingTo("312876.43");
        assertThat(loan.getInterestRate()).isEqualByComparingTo("3.125");
        assertThat(loan.getOriginationDate()).isEqualTo("04/01/2020");
        assertThat(loan.getPropertyAddress()).isEqualTo("1100 Oak Avenue, Portland, OR 97201");
        assertThat(loan.getPropertyType()).isEqualTo("Condominium");
    }

    @Test
    void missingLoanFails() {
        assertThatThrownBy(() -> loanService.getLoanById("LN-DOES-NOT-EXIST"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Loan not found: LN-DOES-NOT-EXIST");
    }

    @Test
    void listsBorrowersWithoutLoans() {
        List<BorrowerDto> borrowers = loanService.getAllBorrowers();

        assertThat(borrowers).extracting(BorrowerDto::getId)
                .containsExactly("B-10001", "B-10002", "B-10003", "B-10004", "B-10005");
        assertThat(borrowers).extracting(BorrowerDto::getFullName)
                .contains("James R. Mitchell", "Robert Williams");
        assertThat(borrowers).allSatisfy(borrower -> assertThat(borrower.getLoans()).isNull());
    }

    @Test
    void retrievesABorrowerWithTheirLoans() {
        BorrowerDto borrower = loanService.getBorrowerById("B-10004");

        assertThat(borrower.getFullName()).isEqualTo("Emily M. Johnson");
        assertThat(borrower.getCreditScore()).isEqualTo(810);
        assertThat(borrower.getLoans()).extracting(LoanSummaryDto::getLoanAccountNumber)
                .containsExactly("LN-2021-00567");
    }

    @Test
    void missingBorrowerFails() {
        assertThatThrownBy(() -> loanService.getBorrowerById("B-99999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Borrower not found: B-99999");
    }

    @Test
    void returnsPaymentHistoryNewestFirstForTheRequestedLoan() {
        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2018-00089");

        assertThat(payments).extracting(PaymentDto::getPaymentId)
                .containsExactly("PMT-2025120003", "PMT-2025110003");
        assertThat(payments).allSatisfy(payment -> {
            assertThat(payment.getLoanAccountNumber()).isEqualTo("LN-2018-00089");
            assertThat(payment.getType()).isEqualTo("Regular");
            assertThat(payment.getStatus()).isEqualTo("Posted");
        });
        assertThat(payments.get(0).getPaymentDate()).isEqualTo("12/01/2025");
        assertThat(payments.get(1).getLateFee()).isEqualByComparingTo("47.50");
    }
}
