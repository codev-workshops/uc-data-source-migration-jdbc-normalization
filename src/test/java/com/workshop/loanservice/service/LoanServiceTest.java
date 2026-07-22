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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private LegacyLoanProduct product(String code, String desc) {
        LegacyLoanProduct p = new LegacyLoanProduct();
        p.setProductCode(code);
        p.setDescription(desc);
        return p;
    }

    private LegacyLoanAccount account(String number, String productCode) {
        LegacyLoanAccount a = new LegacyLoanAccount();
        a.setLoanAccountNumber(number);
        a.setBorrowerId("B-1");
        a.setBorrowerFirstName("James");
        a.setBorrowerLastName("Mitchell");
        a.setProductCode(productCode);
        a.setOriginalAmount("285,000");
        a.setCurrentBalance("271,432.56");
        a.setInterestRate("4.750");
        a.setMonthlyPayment("1,487.02");
        a.setStatusCode("ACT");
        a.setOriginationDate("02/15/2019");
        a.setPropertyAddress("742 Elm Street");
        a.setPropertyCity("Springfield");
        a.setPropertyState("IL");
        a.setPropertyZip("62701");
        a.setPropertyType("SFR");
        return a;
    }

    private LegacyBorrower borrower(String id, String middle) {
        LegacyBorrower b = new LegacyBorrower();
        b.setBorrowerId(id);
        b.setFirstName("James");
        b.setLastName("Mitchell");
        b.setMiddleInitial(middle);
        b.setEmail("j.mitchell@email.com");
        b.setPhoneNumber("217-555-0142");
        b.setCity("Springfield");
        b.setStateCode("IL");
        b.setCreditScore("745");
        b.setEmploymentStatus("EMPLOYED");
        return b;
    }

    private LegacyPayment payment(String seq, String type, String status) {
        LegacyPayment p = new LegacyPayment();
        p.setPaymentSequenceNumber(seq);
        p.setLoanAccountNumber("LN-1");
        p.setPaymentDate("12/15/2025");
        p.setTotalAmount("1,487.02");
        p.setPrincipalAmount("456.78");
        p.setInterestAmount("1,074.69");
        p.setEscrowAmount("355.55");
        p.setLateFee("0.00");
        p.setTypeCode(type);
        p.setStatusCode(status);
        return p;
    }

    // ------------------------------------------------------------------
    // getAllLoans
    // ------------------------------------------------------------------

    @Test
    void getAllLoans_mapsAccountsAndProducts() {
        when(loanProductRepository.findAll()).thenReturn(List.of(product("FXD30", "30-Year Fixed Rate Mortgage")));
        when(loanAccountRepository.findAll()).thenReturn(List.of(account("LN-1", "FXD30")));

        List<LoanSummaryDto> result = loanService.getAllLoans();

        assertThat(result).hasSize(1);
        LoanSummaryDto dto = result.get(0);
        assertThat(dto.getLoanAccountNumber()).isEqualTo("LN-1");
        assertThat(dto.getBorrowerName()).isEqualTo("James Mitchell");
        assertThat(dto.getProductDescription()).isEqualTo("30-Year Fixed Rate Mortgage");
        assertThat(dto.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(dto.getCurrentBalance()).isEqualByComparingTo("271432.56");
        assertThat(dto.getInterestRate()).isEqualByComparingTo("4.750");
        assertThat(dto.getMonthlyPayment()).isEqualByComparingTo("1487.02");
        assertThat(dto.getStatus()).isEqualTo("Active");
        assertThat(dto.getOriginationDate()).isEqualTo("02/15/2019");
        assertThat(dto.getPropertyAddress()).isEqualTo("742 Elm Street, Springfield, IL 62701");
        assertThat(dto.getPropertyType()).isEqualTo("Single Family Residence");
    }

    @Test
    void getAllLoans_whenProductMissing_fallsBackToProductCode() {
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(account("LN-1", "UNKNOWN")));

        List<LoanSummaryDto> result = loanService.getAllLoans();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductDescription()).isEqualTo("UNKNOWN");
    }

    // ------------------------------------------------------------------
    // getLoanById
    // ------------------------------------------------------------------

    @Test
    void getLoanById_found_withProduct() {
        when(loanAccountRepository.findById("LN-1")).thenReturn(Optional.of(account("LN-1", "FXD30")));
        when(loanProductRepository.findById("FXD30"))
                .thenReturn(Optional.of(product("FXD30", "30-Year Fixed Rate Mortgage")));

        LoanSummaryDto dto = loanService.getLoanById("LN-1");

        assertThat(dto.getLoanAccountNumber()).isEqualTo("LN-1");
        assertThat(dto.getProductDescription()).isEqualTo("30-Year Fixed Rate Mortgage");
    }

    @Test
    void getLoanById_found_productNull_fallsBackToProductCode() {
        when(loanAccountRepository.findById("LN-1")).thenReturn(Optional.of(account("LN-1", "FXD30")));
        when(loanProductRepository.findById("FXD30")).thenReturn(Optional.empty());

        LoanSummaryDto dto = loanService.getLoanById("LN-1");

        assertThat(dto.getProductDescription()).isEqualTo("FXD30");
    }

    @Test
    void getLoanById_notFound_throws() {
        when(loanAccountRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.getLoanById("MISSING"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Loan not found: MISSING");
    }

    // ------------------------------------------------------------------
    // getAllBorrowers
    // ------------------------------------------------------------------

    @Test
    void getAllBorrowers_mapsBorrowers() {
        when(borrowerRepository.findAll()).thenReturn(List.of(borrower("B-1", "R")));

        List<BorrowerDto> result = loanService.getAllBorrowers();

        assertThat(result).hasSize(1);
        BorrowerDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo("B-1");
        assertThat(dto.getFullName()).isEqualTo("James R. Mitchell");
        assertThat(dto.getEmail()).isEqualTo("j.mitchell@email.com");
        assertThat(dto.getPhone()).isEqualTo("217-555-0142");
        assertThat(dto.getCity()).isEqualTo("Springfield");
        assertThat(dto.getState()).isEqualTo("IL");
        assertThat(dto.getCreditScore()).isEqualTo(745);
        assertThat(dto.getEmploymentStatus()).isEqualTo("EMPLOYED");
    }

    // ------------------------------------------------------------------
    // getBorrowerById
    // ------------------------------------------------------------------

    @Test
    void getBorrowerById_found_attachesLoans() {
        when(borrowerRepository.findById("B-1")).thenReturn(Optional.of(borrower("B-1", "R")));
        when(loanProductRepository.findAll()).thenReturn(List.of(product("FXD30", "30-Year Fixed Rate Mortgage")));
        when(loanAccountRepository.findByBorrowerId("B-1")).thenReturn(List.of(account("LN-1", "FXD30")));

        BorrowerDto dto = loanService.getBorrowerById("B-1");

        assertThat(dto.getId()).isEqualTo("B-1");
        assertThat(dto.getFullName()).isEqualTo("James R. Mitchell");
        assertThat(dto.getLoans()).hasSize(1);
        assertThat(dto.getLoans().get(0).getLoanAccountNumber()).isEqualTo("LN-1");
        assertThat(dto.getLoans().get(0).getProductDescription()).isEqualTo("30-Year Fixed Rate Mortgage");
    }

    @Test
    void getBorrowerById_withoutMiddleInitial_composesName() {
        when(borrowerRepository.findById("B-1")).thenReturn(Optional.of(borrower("B-1", null)));
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findByBorrowerId("B-1")).thenReturn(List.of());

        BorrowerDto dto = loanService.getBorrowerById("B-1");

        assertThat(dto.getFullName()).isEqualTo("James Mitchell");
        assertThat(dto.getLoans()).isEmpty();
    }

    @Test
    void getBorrowerById_notFound_throws() {
        when(borrowerRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.getBorrowerById("MISSING"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Borrower not found: MISSING");
    }

    // ------------------------------------------------------------------
    // getPaymentsByLoan
    // ------------------------------------------------------------------

    @Test
    void getPaymentsByLoan_mapsPayments() {
        when(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc("LN-1"))
                .thenReturn(List.of(payment("PMT-1", "REG", "PST")));

        List<PaymentDto> result = loanService.getPaymentsByLoan("LN-1");

        assertThat(result).hasSize(1);
        PaymentDto dto = result.get(0);
        assertThat(dto.getPaymentId()).isEqualTo("PMT-1");
        assertThat(dto.getLoanAccountNumber()).isEqualTo("LN-1");
        assertThat(dto.getPaymentDate()).isEqualTo("12/15/2025");
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("1487.02");
        assertThat(dto.getPrincipalAmount()).isEqualByComparingTo("456.78");
        assertThat(dto.getInterestAmount()).isEqualByComparingTo("1074.69");
        assertThat(dto.getEscrowAmount()).isEqualByComparingTo("355.55");
        assertThat(dto.getLateFee()).isEqualByComparingTo("0.00");
        assertThat(dto.getType()).isEqualTo("Regular");
        assertThat(dto.getStatus()).isEqualTo("Posted");
    }

    // ------------------------------------------------------------------
    // parseLegacyAmount (via getAllLoans)
    // ------------------------------------------------------------------

    @Test
    void parseLegacyAmount_handlesCommasNullAndBlank() {
        LegacyLoanAccount a = account("LN-1", "FXD30");
        a.setOriginalAmount("285,000");     // comma-formatted integer
        a.setCurrentBalance("1,487.02");    // comma-formatted decimal
        a.setMonthlyPayment(null);          // null -> ZERO
        a.setInterestRate("   ");           // blank decimal -> ZERO
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(a));

        LoanSummaryDto dto = loanService.getAllLoans().get(0);

        assertThat(dto.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(dto.getCurrentBalance()).isEqualByComparingTo("1487.02");
        assertThat(dto.getMonthlyPayment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getInterestRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseLegacyAmount_blankAmount_returnsZero() {
        LegacyLoanAccount a = account("LN-1", "FXD30");
        a.setOriginalAmount("");
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(a));

        assertThat(loanService.getAllLoans().get(0).getOriginalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // parseLegacyDecimal (via interest rate)
    // ------------------------------------------------------------------

    @Test
    void parseLegacyDecimal_valid() {
        LegacyLoanAccount a = account("LN-1", "FXD30");
        a.setInterestRate("5.250");
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(a));

        assertThat(loanService.getAllLoans().get(0).getInterestRate()).isEqualByComparingTo("5.250");
    }

    // ------------------------------------------------------------------
    // parseLegacyInteger (via credit score)
    // ------------------------------------------------------------------

    @Test
    void parseLegacyInteger_null_returnsNull() {
        LegacyBorrower b = borrower("B-1", "R");
        b.setCreditScore(null);
        when(borrowerRepository.findAll()).thenReturn(List.of(b));

        assertThat(loanService.getAllBorrowers().get(0).getCreditScore()).isNull();
    }

    @Test
    void parseLegacyInteger_blank_returnsNull() {
        LegacyBorrower b = borrower("B-1", "R");
        b.setCreditScore("  ");
        when(borrowerRepository.findAll()).thenReturn(List.of(b));

        assertThat(loanService.getAllBorrowers().get(0).getCreditScore()).isNull();
    }

    // ------------------------------------------------------------------
    // expandStatusCode
    // ------------------------------------------------------------------

    private String statusFor(String code) {
        LegacyLoanAccount a = account("LN-1", "FXD30");
        a.setStatusCode(code);
        lenient().when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(a));
        return loanService.getAllLoans().get(0).getStatus();
    }

    @Test
    void expandStatusCode_allKnownCodes() {
        assertThat(statusFor("ACT")).isEqualTo("Active");
        assertThat(statusFor("CLO")).isEqualTo("Closed");
        assertThat(statusFor("DFT")).isEqualTo("Default");
        assertThat(statusFor("FRB")).isEqualTo("Forbearance");
    }

    @Test
    void expandStatusCode_nullAndUnknown() {
        assertThat(statusFor(null)).isEqualTo("Unknown");
        assertThat(statusFor("ZZZ")).isEqualTo("ZZZ");
    }

    // ------------------------------------------------------------------
    // expandPropertyType
    // ------------------------------------------------------------------

    private String propertyTypeFor(String code) {
        LegacyLoanAccount a = account("LN-1", "FXD30");
        a.setPropertyType(code);
        lenient().when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findAll()).thenReturn(List.of(a));
        return loanService.getAllLoans().get(0).getPropertyType();
    }

    @Test
    void expandPropertyType_allKnownCodes() {
        assertThat(propertyTypeFor("SFR")).isEqualTo("Single Family Residence");
        assertThat(propertyTypeFor("CND")).isEqualTo("Condominium");
        assertThat(propertyTypeFor("MFR")).isEqualTo("Multi-Family Residence");
        assertThat(propertyTypeFor("TWN")).isEqualTo("Townhouse");
    }

    @Test
    void expandPropertyType_nullAndUnknown() {
        assertThat(propertyTypeFor(null)).isEqualTo("Unknown");
        assertThat(propertyTypeFor("ZZZ")).isEqualTo("ZZZ");
    }

    // ------------------------------------------------------------------
    // expandPaymentType / expandPaymentStatus
    // ------------------------------------------------------------------

    private PaymentDto paymentDtoFor(String type, String status) {
        when(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc("LN-1"))
                .thenReturn(List.of(payment("PMT-1", type, status)));
        return loanService.getPaymentsByLoan("LN-1").get(0);
    }

    @Test
    void expandPaymentType_allKnownCodes() {
        assertThat(paymentDtoFor("REG", "PST").getType()).isEqualTo("Regular");
        assertThat(paymentDtoFor("EXT", "PST").getType()).isEqualTo("Extra");
        assertThat(paymentDtoFor("PRT", "PST").getType()).isEqualTo("Partial");
        assertThat(paymentDtoFor("PRE", "PST").getType()).isEqualTo("Prepayment");
    }

    @Test
    void expandPaymentType_nullAndUnknown() {
        assertThat(paymentDtoFor(null, "PST").getType()).isEqualTo("Unknown");
        assertThat(paymentDtoFor("ZZZ", "PST").getType()).isEqualTo("ZZZ");
    }

    @Test
    void expandPaymentStatus_allKnownCodes() {
        assertThat(paymentDtoFor("REG", "PST").getStatus()).isEqualTo("Posted");
        assertThat(paymentDtoFor("REG", "REV").getStatus()).isEqualTo("Reversed");
        assertThat(paymentDtoFor("REG", "NSF").getStatus()).isEqualTo("Non-Sufficient Funds");
        assertThat(paymentDtoFor("REG", "PND").getStatus()).isEqualTo("Pending");
    }

    @Test
    void expandPaymentStatus_nullAndUnknown() {
        assertThat(paymentDtoFor("REG", null).getStatus()).isEqualTo("Unknown");
        assertThat(paymentDtoFor("REG", "ZZZ").getStatus()).isEqualTo("ZZZ");
    }
}
