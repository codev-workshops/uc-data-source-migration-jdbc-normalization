package com.workshop.loanservice.service;

import com.workshop.loanservice.config.DualServiceTestConfig;
import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Import(DualServiceTestConfig.class)
@TestPropertySource(properties = {
    "app.migration.enabled=true",
    "app.service.mode=legacy"
})
class LoanServiceParityTest {

    @Autowired
    @Qualifier("legacyService")
    private LoanServiceInterface legacyService;

    @Autowired
    @Qualifier("modernService")
    private LoanServiceInterface modernService;

    @Test
    void testGetAllLoansIdentical() {
        List<LoanSummaryDto> legacyLoans = legacyService.getAllLoans();
        List<LoanSummaryDto> modernLoans = modernService.getAllLoans();

        assertEquals(legacyLoans.size(), modernLoans.size(), "Loan count mismatch");

        legacyLoans.sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));
        modernLoans.sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));

        for (int i = 0; i < legacyLoans.size(); i++) {
            assertLoanSummaryEquals(legacyLoans.get(i), modernLoans.get(i));
        }
    }

    @Test
    void testGetLoanByIdIdentical() {
        String[] loanIds = {"LN-2019-00142", "LN-2020-00398", "LN-2018-00089", "LN-2021-00567", "LN-2017-00034"};
        for (String loanId : loanIds) {
            LoanSummaryDto legacy = legacyService.getLoanById(loanId);
            LoanSummaryDto modern = modernService.getLoanById(loanId);
            assertLoanSummaryEquals(legacy, modern);
        }
    }

    @Test
    void testGetAllBorrowersIdentical() {
        List<BorrowerDto> legacyBorrowers = legacyService.getAllBorrowers();
        List<BorrowerDto> modernBorrowers = modernService.getAllBorrowers();

        assertEquals(legacyBorrowers.size(), modernBorrowers.size(), "Borrower count mismatch");

        legacyBorrowers.sort(Comparator.comparing(BorrowerDto::getId));
        modernBorrowers.sort(Comparator.comparing(BorrowerDto::getId));

        for (int i = 0; i < legacyBorrowers.size(); i++) {
            assertBorrowerEquals(legacyBorrowers.get(i), modernBorrowers.get(i));
        }
    }

    @Test
    void testGetBorrowerByIdIdentical() {
        String[] borrowerIds = {"B-10001", "B-10002", "B-10003", "B-10004", "B-10005"};
        for (String borrowerId : borrowerIds) {
            BorrowerDto legacy = legacyService.getBorrowerById(borrowerId);
            BorrowerDto modern = modernService.getBorrowerById(borrowerId);
            assertBorrowerEquals(legacy, modern);

            assertNotNull(legacy.getLoans(), "Legacy loans should not be null for " + borrowerId);
            assertNotNull(modern.getLoans(), "Modern loans should not be null for " + borrowerId);
            assertEquals(legacy.getLoans().size(), modern.getLoans().size(),
                "Loan count mismatch for borrower " + borrowerId);

            if (legacy.getLoans() != null && modern.getLoans() != null) {
                legacy.getLoans().sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));
                modern.getLoans().sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));
                for (int j = 0; j < legacy.getLoans().size(); j++) {
                    assertLoanSummaryEquals(legacy.getLoans().get(j), modern.getLoans().get(j));
                }
            }
        }
    }

    @Test
    void testGetPaymentsByLoanIdentical() {
        String[] loanIds = {"LN-2019-00142", "LN-2020-00398", "LN-2018-00089", "LN-2021-00567", "LN-2017-00034"};
        for (String loanId : loanIds) {
            List<PaymentDto> legacyPayments = legacyService.getPaymentsByLoan(loanId);
            List<PaymentDto> modernPayments = modernService.getPaymentsByLoan(loanId);

            assertEquals(legacyPayments.size(), modernPayments.size(),
                "Payment count mismatch for loan " + loanId);

            for (int i = 0; i < legacyPayments.size(); i++) {
                assertPaymentEquals(legacyPayments.get(i), modernPayments.get(i));
            }
        }
    }

    private void assertLoanSummaryEquals(LoanSummaryDto expected, LoanSummaryDto actual) {
        String ctx = "Loan " + expected.getLoanAccountNumber();
        assertEquals(expected.getLoanAccountNumber(), actual.getLoanAccountNumber(), ctx + " - loanAccountNumber");
        assertEquals(expected.getBorrowerName(), actual.getBorrowerName(), ctx + " - borrowerName");
        assertEquals(expected.getProductDescription(), actual.getProductDescription(), ctx + " - productDescription");
        assertEquals(0, expected.getOriginalAmount().compareTo(actual.getOriginalAmount()), ctx + " - originalAmount");
        assertEquals(0, expected.getCurrentBalance().compareTo(actual.getCurrentBalance()), ctx + " - currentBalance");
        assertEquals(0, expected.getInterestRate().compareTo(actual.getInterestRate()), ctx + " - interestRate");
        assertEquals(0, expected.getMonthlyPayment().compareTo(actual.getMonthlyPayment()), ctx + " - monthlyPayment");
        assertEquals(expected.getStatus(), actual.getStatus(), ctx + " - status");
        assertEquals(expected.getOriginationDate(), actual.getOriginationDate(), ctx + " - originationDate");
        assertEquals(expected.getPropertyAddress(), actual.getPropertyAddress(), ctx + " - propertyAddress");
        assertEquals(expected.getPropertyType(), actual.getPropertyType(), ctx + " - propertyType");
    }

    private void assertBorrowerEquals(BorrowerDto expected, BorrowerDto actual) {
        String ctx = "Borrower " + expected.getId();
        assertEquals(expected.getId(), actual.getId(), ctx + " - id");
        assertEquals(expected.getFullName(), actual.getFullName(), ctx + " - fullName");
        assertEquals(expected.getEmail(), actual.getEmail(), ctx + " - email");
        assertEquals(expected.getPhone(), actual.getPhone(), ctx + " - phone");
        assertEquals(expected.getCity(), actual.getCity(), ctx + " - city");
        assertEquals(expected.getState(), actual.getState(), ctx + " - state");
        assertEquals(expected.getCreditScore(), actual.getCreditScore(), ctx + " - creditScore");
        assertEquals(expected.getEmploymentStatus(), actual.getEmploymentStatus(), ctx + " - employmentStatus");
    }

    private void assertPaymentEquals(PaymentDto expected, PaymentDto actual) {
        String ctx = "Payment " + expected.getPaymentId();
        assertEquals(expected.getPaymentId(), actual.getPaymentId(), ctx + " - paymentId");
        assertEquals(expected.getLoanAccountNumber(), actual.getLoanAccountNumber(), ctx + " - loanAccountNumber");
        assertEquals(expected.getPaymentDate(), actual.getPaymentDate(), ctx + " - paymentDate");
        assertEquals(0, expected.getTotalAmount().compareTo(actual.getTotalAmount()), ctx + " - totalAmount");
        assertEquals(0, expected.getPrincipalAmount().compareTo(actual.getPrincipalAmount()), ctx + " - principalAmount");
        assertEquals(0, expected.getInterestAmount().compareTo(actual.getInterestAmount()), ctx + " - interestAmount");
        assertEquals(0, expected.getEscrowAmount().compareTo(actual.getEscrowAmount()), ctx + " - escrowAmount");
        assertEquals(0, expected.getLateFee().compareTo(actual.getLateFee()), ctx + " - lateFee");
        assertEquals(expected.getType(), actual.getType(), ctx + " - type");
        assertEquals(expected.getStatus(), actual.getStatus(), ctx + " - status");
    }
}
