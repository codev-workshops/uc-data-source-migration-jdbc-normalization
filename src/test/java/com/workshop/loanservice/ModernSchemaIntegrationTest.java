package com.workshop.loanservice;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests against the real H2 modern schema (schema-modern.sql /
 * data-modern.sql), exercising the repositories, JPA relationships and the
 * service mapping end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ModernSchemaIntegrationTest {

    @Autowired private LoanService loanService;
    @Autowired private BorrowerRepository borrowerRepository;
    @Autowired private LoanProductRepository loanProductRepository;
    @Autowired private LoanAccountRepository loanAccountRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void modernTablesAreSeededAndLegacyTablesAreAbsent() {
        assertEquals(5, borrowerRepository.count());
        assertEquals(5, loanProductRepository.count());
        assertEquals(5, loanAccountRepository.count());
        assertEquals(10, paymentRepository.count());

        Integer legacyTables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'CDW%'", Integer.class);
        assertEquals(0, legacyTables);
    }

    @Test
    void listLoansReturnsAllAccountsInSeedOrder() {
        List<LoanSummaryDto> loans = loanService.getAllLoans();
        assertEquals(List.of("LN-2019-00142", "LN-2020-00398", "LN-2018-00089", "LN-2021-00567", "LN-2017-00034"),
                loans.stream().map(LoanSummaryDto::getLoanAccountNumber).toList());
    }

    @Test
    void singleLoanLookupMapsTypedColumnsBackToApiRepresentation() {
        LoanSummaryDto loan = loanService.getLoanById("LN-2020-00398");
        assertEquals("Sarah Chen", loan.getBorrowerName());
        assertEquals("15-Year Fixed Rate Mortgage", loan.getProductDescription());
        assertEquals(new BigDecimal("420000"), loan.getOriginalAmount());
        assertEquals(new BigDecimal("312876.43"), loan.getCurrentBalance());
        assertEquals(new BigDecimal("3.125"), loan.getInterestRate());
        assertEquals("Active", loan.getStatus());
        assertEquals("04/01/2020", loan.getOriginationDate());
        assertEquals("Condominium", loan.getPropertyType());
        assertEquals("1100 Oak Avenue, Portland, OR 97201", loan.getPropertyAddress());
    }

    @Test
    void unknownLoanThrowsRuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> loanService.getLoanById("LN-0000-00000"));
        assertEquals("Loan not found: LN-0000-00000", ex.getMessage());
    }

    @Test
    void borrowerListingUsesExternalIdsAndOmitsLoans() {
        List<BorrowerDto> borrowers = loanService.getAllBorrowers();
        assertEquals(List.of("B-10001", "B-10002", "B-10003", "B-10004", "B-10005"),
                borrowers.stream().map(BorrowerDto::getId).toList());
        assertEquals("Robert Williams", borrowers.get(4).getFullName());
        assertEquals("James R. Mitchell", borrowers.get(0).getFullName());
        assertEquals(745, borrowers.get(0).getCreditScore());
        borrowers.forEach(b -> assertNull(b.getLoans()));
    }

    @Test
    void borrowerLookupAttachesLoansThroughForeignKey() {
        BorrowerDto borrower = loanService.getBorrowerById("B-10003");
        assertEquals("Michael A. Torres", borrower.getFullName());
        assertEquals(1, borrower.getLoans().size());
        assertEquals("LN-2018-00089", borrower.getLoans().get(0).getLoanAccountNumber());
        assertEquals("Michael Torres", borrower.getLoans().get(0).getBorrowerName());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> loanService.getBorrowerById("B-00000"));
        assertEquals("Borrower not found: B-00000", ex.getMessage());
    }

    @Test
    void loanAccountResolvesBorrowerAndProductViaForeignKeys() {
        LoanAccount acct = loanAccountRepository.findByAccountNumber("LN-2017-00034").orElseThrow();
        assertEquals("B-10005", acct.getBorrower().getExternalId());
        assertEquals("Robert", acct.getBorrower().getFirstName());
        assertEquals("FHA30", acct.getProduct().getCode());
        assertEquals("FHA 30-Year Fixed", acct.getProduct().getName());
        assertEquals(360, acct.getProduct().getTermMonths());
        assertEquals(LocalDate.of(2017, 3, 1), acct.getOriginationDate());
        assertEquals("SINGLE_FAMILY", acct.getPropertyType());

        List<LoanAccount> byBorrower = loanAccountRepository.findByBorrower_ExternalIdOrderByIdAsc("B-10005");
        assertEquals(1, byBorrower.size());
        assertEquals(acct.getId(), byBorrower.get(0).getId());
    }

    @Test
    void borrowerOneToManyExposesLoanAccounts() {
        var borrower = borrowerRepository.findByExternalId("B-10001").orElseThrow();
        assertEquals(1, borrower.getLoanAccounts().size());
        assertEquals("LN-2019-00142", borrower.getLoanAccounts().get(0).getAccountNumber());
    }

    @Test
    void paymentHistoryIsOrderedByPaymentDateDescending() {
        List<Payment> payments = paymentRepository
                .findByLoanAccount_AccountNumberOrderByPaymentDateDesc("LN-2018-00089");
        assertEquals(2, payments.size());
        assertTrue(payments.get(0).getPaymentDate().isAfter(payments.get(1).getPaymentDate()));
        assertEquals("PMT-2025120003", payments.get(0).getExternalId());
        assertEquals("PMT-2025110003", payments.get(1).getExternalId());
        assertEquals(new BigDecimal("47.50"), payments.get(1).getLateFee());

        List<PaymentDto> dtos = loanService.getPaymentsByLoan("LN-2018-00089");
        assertEquals(List.of("12/01/2025", "11/01/2025"),
                dtos.stream().map(PaymentDto::getPaymentDate).toList());
        assertEquals("PMT-2025120003", dtos.get(0).getPaymentId());
        assertEquals("LN-2018-00089", dtos.get(0).getLoanAccountNumber());
        assertEquals("Regular", dtos.get(0).getType());
        assertEquals("Posted", dtos.get(0).getStatus());

        assertTrue(loanService.getPaymentsByLoan("LN-0000-00000").isEmpty());
    }
}
