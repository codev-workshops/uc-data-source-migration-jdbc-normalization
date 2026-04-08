package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.DataMigrationService;
import com.workshop.loanservice.service.LoanService;
import com.workshop.loanservice.service.ModernLoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
class LegacyModernParityTest {

    @Autowired
    private LoanService loanService;

    @Autowired
    private ModernLoanService modernLoanService;

    @Autowired
    private DataMigrationService dataMigrationService;

    @BeforeEach
    void setUp() {
        dataMigrationService.migrate();
    }

    @Test
    void testAllLoans_parity() {
        List<LoanSummaryDto> legacyLoans = loanService.getAllLoans();
        List<LoanSummaryDto> modernLoans = modernLoanService.getAllLoans();

        assertEquals(legacyLoans.size(), modernLoans.size());

        legacyLoans.sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));
        modernLoans.sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));

        for (int i = 0; i < legacyLoans.size(); i++) {
            LoanSummaryDto legacy = legacyLoans.get(i);
            LoanSummaryDto modern = modernLoans.get(i);
            assertEquals(legacy.getLoanAccountNumber(), modern.getLoanAccountNumber());
            assertEquals(legacy.getBorrowerName(), modern.getBorrowerName());
            assertEquals(legacy.getProductDescription(), modern.getProductDescription());
            assertEquals(0, legacy.getOriginalAmount().compareTo(modern.getOriginalAmount()));
            assertEquals(0, legacy.getCurrentBalance().compareTo(modern.getCurrentBalance()));
            assertEquals(0, legacy.getInterestRate().compareTo(modern.getInterestRate()));
            assertEquals(0, legacy.getMonthlyPayment().compareTo(modern.getMonthlyPayment()));
            assertEquals(legacy.getStatus(), modern.getStatus());
            assertEquals(legacy.getOriginationDate(), modern.getOriginationDate());
            assertEquals(legacy.getPropertyAddress(), modern.getPropertyAddress());
            assertEquals(legacy.getPropertyType(), modern.getPropertyType());
        }
    }

    @Test
    void testAllBorrowers_parity() {
        List<BorrowerDto> legacyBorrowers = loanService.getAllBorrowers();
        List<BorrowerDto> modernBorrowers = modernLoanService.getAllBorrowers();

        assertEquals(legacyBorrowers.size(), modernBorrowers.size());

        legacyBorrowers.sort(Comparator.comparing(BorrowerDto::getId));
        modernBorrowers.sort(Comparator.comparing(BorrowerDto::getId));

        for (int i = 0; i < legacyBorrowers.size(); i++) {
            BorrowerDto legacy = legacyBorrowers.get(i);
            BorrowerDto modern = modernBorrowers.get(i);
            assertEquals(legacy.getId(), modern.getId());
            assertEquals(legacy.getFullName(), modern.getFullName());
            assertEquals(legacy.getEmail(), modern.getEmail());
            assertEquals(legacy.getPhone(), modern.getPhone());
            assertEquals(legacy.getCity(), modern.getCity());
            assertEquals(legacy.getState(), modern.getState());
            assertEquals(legacy.getCreditScore(), modern.getCreditScore());
            assertEquals(legacy.getEmploymentStatus(), modern.getEmploymentStatus());
        }
    }

    @Test
    void testBorrowerWithLoans_parity() {
        String[] borrowerIds = {"B-10001", "B-10002", "B-10003", "B-10004", "B-10005"};

        for (String borrowerId : borrowerIds) {
            BorrowerDto legacy = loanService.getBorrowerById(borrowerId);
            BorrowerDto modern = modernLoanService.getBorrowerById(borrowerId);

            assertNotNull(legacy.getLoans(), "Legacy loans null for " + borrowerId);
            assertNotNull(modern.getLoans(), "Modern loans null for " + borrowerId);
            assertEquals(legacy.getLoans().size(), modern.getLoans().size(),
                    "Loan count mismatch for borrower " + borrowerId);

            legacy.getLoans().sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));
            modern.getLoans().sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));

            for (int i = 0; i < legacy.getLoans().size(); i++) {
                LoanSummaryDto legacyLoan = legacy.getLoans().get(i);
                LoanSummaryDto modernLoan = modern.getLoans().get(i);
                assertEquals(legacyLoan.getLoanAccountNumber(), modernLoan.getLoanAccountNumber());
                assertEquals(legacyLoan.getBorrowerName(), modernLoan.getBorrowerName());
                assertEquals(legacyLoan.getProductDescription(), modernLoan.getProductDescription());
                assertEquals(0, legacyLoan.getOriginalAmount().compareTo(modernLoan.getOriginalAmount()));
                assertEquals(0, legacyLoan.getCurrentBalance().compareTo(modernLoan.getCurrentBalance()));
                assertEquals(0, legacyLoan.getInterestRate().compareTo(modernLoan.getInterestRate()));
                assertEquals(0, legacyLoan.getMonthlyPayment().compareTo(modernLoan.getMonthlyPayment()));
                assertEquals(legacyLoan.getStatus(), modernLoan.getStatus());
                assertEquals(legacyLoan.getOriginationDate(), modernLoan.getOriginationDate());
                assertEquals(legacyLoan.getPropertyAddress(), modernLoan.getPropertyAddress());
                assertEquals(legacyLoan.getPropertyType(), modernLoan.getPropertyType());
            }
        }
    }

    @Test
    void testPayments_parity() {
        String[] loanAccountNumbers = {
            "LN-2019-00142", "LN-2020-00398", "LN-2018-00089", "LN-2021-00567", "LN-2017-00034"
        };

        for (String loanAccountNumber : loanAccountNumbers) {
            List<PaymentDto> legacyPayments = loanService.getPaymentsByLoan(loanAccountNumber);
            List<PaymentDto> modernPayments = modernLoanService.getPaymentsByLoan(loanAccountNumber);

            assertEquals(legacyPayments.size(), modernPayments.size(),
                    "Payment count mismatch for loan " + loanAccountNumber);

            legacyPayments.sort(Comparator.comparing(PaymentDto::getPaymentDate));
            modernPayments.sort(Comparator.comparing(PaymentDto::getPaymentDate));

            for (int i = 0; i < legacyPayments.size(); i++) {
                PaymentDto legacy = legacyPayments.get(i);
                PaymentDto modern = modernPayments.get(i);
                // paymentId is known to differ (legacy "PMT-..." vs modern auto-generated Long)
                assertEquals(legacy.getLoanAccountNumber(), modern.getLoanAccountNumber());
                assertEquals(legacy.getPaymentDate(), modern.getPaymentDate());
                assertEquals(0, legacy.getTotalAmount().compareTo(modern.getTotalAmount()));
                assertEquals(0, legacy.getPrincipalAmount().compareTo(modern.getPrincipalAmount()));
                assertEquals(0, legacy.getInterestAmount().compareTo(modern.getInterestAmount()));
                assertEquals(0, legacy.getEscrowAmount().compareTo(modern.getEscrowAmount()));
                assertEquals(0, legacy.getLateFee().compareTo(modern.getLateFee()));
                assertEquals(legacy.getType(), modern.getType());
                assertEquals(legacy.getStatus(), modern.getStatus());
            }
        }
    }
}
