package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoanService}, the layer that translates cryptic legacy
 * string fields into typed DTOs. Repositories are mocked so the tests focus on
 * the translation/business logic rather than persistence.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LegacyBorrowerRepository borrowerRepository;
    @Mock
    private LegacyLoanAccountRepository loanAccountRepository;
    @Mock
    private LegacyLoanProductRepository loanProductRepository;
    @Mock
    private LegacyPaymentRepository paymentRepository;

    @InjectMocks
    private LoanService loanService;

    // ---------------------------------------------------------------------
    // getAllLoans
    // ---------------------------------------------------------------------

    @Test
    void getAllLoans_translatesLegacyFieldsIntoSummaries() {
        when(loanProductRepository.findAll()).thenReturn(List.of(product("FX30", "30-Year Fixed")));
        when(loanAccountRepository.findAll()).thenReturn(List.of(loanAccount()));

        List<LoanSummaryDto> result = loanService.getAllLoans();

        assertThat(result).hasSize(1);
        LoanSummaryDto dto = result.get(0);
        assertThat(dto.getLoanAccountNumber()).isEqualTo("LN1001");
        assertThat(dto.getBorrowerName()).isEqualTo("Jane Doe");
        assertThat(dto.getProductDescription()).isEqualTo("30-Year Fixed");
        assertThat(dto.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(dto.getCurrentBalance()).isEqualByComparingTo("250000.50");
        assertThat(dto.getInterestRate()).isEqualByComparingTo("5.25");
        assertThat(dto.getMonthlyPayment()).isEqualByComparingTo("1487.02");
        assertThat(dto.getStatus()).isEqualTo("Active");
        assertThat(dto.getOriginationDate()).isEqualTo("01/15/2020");
        assertThat(dto.getPropertyAddress()).isEqualTo("123 Main St, Springfield, IL 62704");
        assertThat(dto.getPropertyType()).isEqualTo("Single Family Residence");
    }

    @Test
    void getAllLoans_fallsBackToProductCodeWhenProductMissing() {
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(loanAccount()));

        List<LoanSummaryDto> result = loanService.getAllLoans();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductDescription()).isEqualTo("FX30");
    }

    @Test
    void getAllLoans_returnsEmptyListWhenNoAccounts() {
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of());

        assertThat(loanService.getAllLoans()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // getLoanById
    // ---------------------------------------------------------------------

    @Test
    void getLoanById_returnsTranslatedSummary() {
        LegacyLoanAccount acct = loanAccount();
        when(loanAccountRepository.findById("LN1001")).thenReturn(Optional.of(acct));
        when(loanProductRepository.findById("FX30")).thenReturn(Optional.of(product("FX30", "30-Year Fixed")));

        LoanSummaryDto dto = loanService.getLoanById("LN1001");

        assertThat(dto.getLoanAccountNumber()).isEqualTo("LN1001");
        assertThat(dto.getProductDescription()).isEqualTo("30-Year Fixed");
    }

    @Test
    void getLoanById_usesProductCodeWhenProductNotFound() {
        when(loanAccountRepository.findById("LN1001")).thenReturn(Optional.of(loanAccount()));
        when(loanProductRepository.findById("FX30")).thenReturn(Optional.empty());

        LoanSummaryDto dto = loanService.getLoanById("LN1001");

        assertThat(dto.getProductDescription()).isEqualTo("FX30");
    }

    @Test
    void getLoanById_throwsWhenLoanMissing() {
        when(loanAccountRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.getLoanById("MISSING"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Loan not found: MISSING");
    }

    // ---------------------------------------------------------------------
    // getAllBorrowers / getBorrowerById
    // ---------------------------------------------------------------------

    @Test
    void getAllBorrowers_translatesBorrowerFields() {
        when(borrowerRepository.findAll()).thenReturn(List.of(borrower()));

        List<BorrowerDto> result = loanService.getAllBorrowers();

        assertThat(result).hasSize(1);
        BorrowerDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo("B1");
        assertThat(dto.getFullName()).isEqualTo("Jane Q. Doe");
        assertThat(dto.getEmail()).isEqualTo("jane@example.com");
        assertThat(dto.getPhone()).isEqualTo("555-1234");
        assertThat(dto.getCity()).isEqualTo("Springfield");
        assertThat(dto.getState()).isEqualTo("IL");
        assertThat(dto.getCreditScore()).isEqualTo(720);
        assertThat(dto.getEmploymentStatus()).isEqualTo("EMPLOYED");
    }

    @Test
    void getAllBorrowers_buildsFullNameWithoutMiddleInitial() {
        LegacyBorrower borrower = borrower();
        borrower.setMiddleInitial(null);
        when(borrowerRepository.findAll()).thenReturn(List.of(borrower));

        assertThat(loanService.getAllBorrowers().get(0).getFullName()).isEqualTo("Jane Doe");
    }

    @Test
    void getBorrowerById_attachesLoansForBorrower() {
        when(borrowerRepository.findById("B1")).thenReturn(Optional.of(borrower()));
        when(loanProductRepository.findAll()).thenReturn(List.of(product("FX30", "30-Year Fixed")));
        when(loanAccountRepository.findByBorrowerId("B1")).thenReturn(List.of(loanAccount()));

        BorrowerDto dto = loanService.getBorrowerById("B1");

        assertThat(dto.getId()).isEqualTo("B1");
        assertThat(dto.getLoans()).hasSize(1);
        assertThat(dto.getLoans().get(0).getLoanAccountNumber()).isEqualTo("LN1001");
        assertThat(dto.getLoans().get(0).getProductDescription()).isEqualTo("30-Year Fixed");
    }

    @Test
    void getBorrowerById_returnsEmptyLoansWhenNone() {
        when(borrowerRepository.findById("B1")).thenReturn(Optional.of(borrower()));
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findByBorrowerId("B1")).thenReturn(List.of());

        assertThat(loanService.getBorrowerById("B1").getLoans()).isEmpty();
    }

    @Test
    void getBorrowerById_throwsWhenBorrowerMissing() {
        when(borrowerRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.getBorrowerById("MISSING"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Borrower not found: MISSING");
    }

    // ---------------------------------------------------------------------
    // getPaymentsByLoan
    // ---------------------------------------------------------------------

    @Test
    void getPaymentsByLoan_translatesPaymentFields() {
        when(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc("LN1001"))
                .thenReturn(List.of(payment()));

        List<PaymentDto> result = loanService.getPaymentsByLoan("LN1001");

        assertThat(result).hasSize(1);
        PaymentDto dto = result.get(0);
        assertThat(dto.getPaymentId()).isEqualTo("P1");
        assertThat(dto.getLoanAccountNumber()).isEqualTo("LN1001");
        assertThat(dto.getPaymentDate()).isEqualTo("02/01/2020");
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("1487.02");
        assertThat(dto.getPrincipalAmount()).isEqualByComparingTo("300.00");
        assertThat(dto.getInterestAmount()).isEqualByComparingTo("1187.02");
        assertThat(dto.getEscrowAmount()).isEqualByComparingTo("0");
        assertThat(dto.getLateFee()).isEqualByComparingTo("0");
        assertThat(dto.getType()).isEqualTo("Regular");
        assertThat(dto.getStatus()).isEqualTo("Posted");
    }

    @Test
    void getPaymentsByLoan_returnsEmptyWhenNoPayments() {
        when(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc("LN1001"))
                .thenReturn(List.of());

        assertThat(loanService.getPaymentsByLoan("LN1001")).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Amount / numeric parsing edge cases (exercised via getAllLoans)
    // ---------------------------------------------------------------------

    @Test
    void parseLegacyAmount_handlesNullAndBlankAsZero() {
        LegacyLoanAccount acct = loanAccount();
        acct.setOriginalAmount(null);
        acct.setCurrentBalance("");
        acct.setMonthlyPayment("   ");
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(acct));

        LoanSummaryDto dto = loanService.getAllLoans().get(0);

        assertThat(dto.getOriginalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getMonthlyPayment()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseLegacyAmount_stripsThousandsSeparators() {
        LegacyLoanAccount acct = loanAccount();
        acct.setOriginalAmount("1,250,000.75");
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(acct));

        assertThat(loanService.getAllLoans().get(0).getOriginalAmount())
                .isEqualByComparingTo("1250000.75");
    }

    @Test
    void parseLegacyDecimal_blankRateBecomesZero() {
        LegacyLoanAccount acct = loanAccount();
        acct.setInterestRate("  ");
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(acct));

        assertThat(loanService.getAllLoans().get(0).getInterestRate())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseLegacyInteger_nullCreditScoreBecomesNull() {
        LegacyBorrower borrower = borrower();
        borrower.setCreditScore(null);
        when(borrowerRepository.findAll()).thenReturn(List.of(borrower));

        assertThat(loanService.getAllBorrowers().get(0).getCreditScore()).isNull();
    }

    @Test
    void parseLegacyInteger_trimsWhitespace() {
        LegacyBorrower borrower = borrower();
        borrower.setCreditScore("  680 ");
        when(borrowerRepository.findAll()).thenReturn(List.of(borrower));

        assertThat(loanService.getAllBorrowers().get(0).getCreditScore()).isEqualTo(680);
    }

    // ---------------------------------------------------------------------
    // Status / type code expansion (exercised via getAllLoans / payments)
    // ---------------------------------------------------------------------

    @Test
    void expandStatusCode_mapsKnownCodes() {
        assertThat(statusFor("ACT")).isEqualTo("Active");
        assertThat(statusFor("CLO")).isEqualTo("Closed");
        assertThat(statusFor("DFT")).isEqualTo("Default");
        assertThat(statusFor("FRB")).isEqualTo("Forbearance");
    }

    @Test
    void expandStatusCode_returnsRawCodeForUnknown() {
        assertThat(statusFor("ZZZ")).isEqualTo("ZZZ");
    }

    @Test
    void expandStatusCode_nullBecomesUnknown() {
        assertThat(statusFor(null)).isEqualTo("Unknown");
    }

    @Test
    void expandPropertyType_mapsKnownCodes() {
        assertThat(propertyTypeFor("SFR")).isEqualTo("Single Family Residence");
        assertThat(propertyTypeFor("CND")).isEqualTo("Condominium");
        assertThat(propertyTypeFor("MFR")).isEqualTo("Multi-Family Residence");
        assertThat(propertyTypeFor("TWN")).isEqualTo("Townhouse");
    }

    @Test
    void expandPropertyType_unknownAndNull() {
        assertThat(propertyTypeFor("XYZ")).isEqualTo("XYZ");
        assertThat(propertyTypeFor(null)).isEqualTo("Unknown");
    }

    @Test
    void expandPaymentType_mapsKnownCodes() {
        assertThat(paymentTypeFor("REG")).isEqualTo("Regular");
        assertThat(paymentTypeFor("EXT")).isEqualTo("Extra");
        assertThat(paymentTypeFor("PRT")).isEqualTo("Partial");
        assertThat(paymentTypeFor("PRE")).isEqualTo("Prepayment");
        assertThat(paymentTypeFor("???")).isEqualTo("???");
        assertThat(paymentTypeFor(null)).isEqualTo("Unknown");
    }

    @Test
    void expandPaymentStatus_mapsKnownCodes() {
        assertThat(paymentStatusFor("PST")).isEqualTo("Posted");
        assertThat(paymentStatusFor("REV")).isEqualTo("Reversed");
        assertThat(paymentStatusFor("NSF")).isEqualTo("Non-Sufficient Funds");
        assertThat(paymentStatusFor("PND")).isEqualTo("Pending");
        assertThat(paymentStatusFor("???")).isEqualTo("???");
        assertThat(paymentStatusFor(null)).isEqualTo("Unknown");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String statusFor(String code) {
        LegacyLoanAccount acct = loanAccount();
        acct.setStatusCode(code);
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(acct));
        return loanService.getAllLoans().get(0).getStatus();
    }

    private String propertyTypeFor(String code) {
        LegacyLoanAccount acct = loanAccount();
        acct.setPropertyType(code);
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(acct));
        return loanService.getAllLoans().get(0).getPropertyType();
    }

    private String paymentTypeFor(String code) {
        LegacyPayment pmt = payment();
        pmt.setTypeCode(code);
        when(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc("LN1001"))
                .thenReturn(List.of(pmt));
        return loanService.getPaymentsByLoan("LN1001").get(0).getType();
    }

    private String paymentStatusFor(String code) {
        LegacyPayment pmt = payment();
        pmt.setStatusCode(code);
        when(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc("LN1001"))
                .thenReturn(List.of(pmt));
        return loanService.getPaymentsByLoan("LN1001").get(0).getStatus();
    }

    private static LegacyLoanProduct product(String code, String description) {
        LegacyLoanProduct product = new LegacyLoanProduct();
        product.setProductCode(code);
        product.setDescription(description);
        return product;
    }

    private static LegacyLoanAccount loanAccount() {
        LegacyLoanAccount acct = new LegacyLoanAccount();
        acct.setLoanAccountNumber("LN1001");
        acct.setBorrowerId("B1");
        acct.setBorrowerFirstName("Jane");
        acct.setBorrowerLastName("Doe");
        acct.setProductCode("FX30");
        acct.setOriginalAmount("285,000");
        acct.setCurrentBalance("250,000.50");
        acct.setInterestRate("5.25");
        acct.setMonthlyPayment("1,487.02");
        acct.setStatusCode("ACT");
        acct.setOriginationDate("01/15/2020");
        acct.setPropertyAddress("123 Main St");
        acct.setPropertyCity("Springfield");
        acct.setPropertyState("IL");
        acct.setPropertyZip("62704");
        acct.setPropertyType("SFR");
        return acct;
    }

    private static LegacyBorrower borrower() {
        LegacyBorrower borrower = new LegacyBorrower();
        borrower.setBorrowerId("B1");
        borrower.setFirstName("Jane");
        borrower.setLastName("Doe");
        borrower.setMiddleInitial("Q");
        borrower.setEmail("jane@example.com");
        borrower.setPhoneNumber("555-1234");
        borrower.setCity("Springfield");
        borrower.setStateCode("IL");
        borrower.setCreditScore("720");
        borrower.setEmploymentStatus("EMPLOYED");
        return borrower;
    }

    private static LegacyPayment payment() {
        LegacyPayment pmt = new LegacyPayment();
        pmt.setPaymentSequenceNumber("P1");
        pmt.setLoanAccountNumber("LN1001");
        pmt.setPaymentDate("02/01/2020");
        pmt.setTotalAmount("1,487.02");
        pmt.setPrincipalAmount("300.00");
        pmt.setInterestAmount("1,187.02");
        pmt.setEscrowAmount(null);
        pmt.setLateFee("");
        pmt.setTypeCode("REG");
        pmt.setStatusCode("PST");
        return pmt;
    }
}
