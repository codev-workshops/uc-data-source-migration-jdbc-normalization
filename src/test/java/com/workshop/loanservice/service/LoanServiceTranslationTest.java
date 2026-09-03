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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterisation tests for the private legacy translation methods in {@link LoanService}
 * ({@code parseLegacyAmount}, {@code parseLegacyDecimal}, {@code parseLegacyInteger}, the four
 * {@code expand*} mappers and the name / address concatenation).
 *
 * <p>These tests pin the <b>current</b> behaviour — including known bugs documented in
 * {@code docs/MIGRATION_ANALYSIS.md} section 4 — so that the modern read path (Phase 5/6) can
 * prove parity or explicitly document intentional differences. A failing test here means the
 * legacy semantics changed, not necessarily that something is wrong.
 *
 * <p>The translation methods are private, so every test goes through a public service method
 * with crafted entity fixtures and Mockito-mocked repositories.
 */
class LoanServiceTranslationTest {

    private static final String LOAN = "LN-TEST-00001";
    private static final String BORROWER = "B-TEST";

    private LegacyBorrowerRepository borrowerRepository;
    private LegacyLoanAccountRepository loanAccountRepository;
    private LegacyLoanProductRepository loanProductRepository;
    private LegacyPaymentRepository paymentRepository;
    private LoanService service;

    @BeforeEach
    void setUp() {
        borrowerRepository = mock(LegacyBorrowerRepository.class);
        loanAccountRepository = mock(LegacyLoanAccountRepository.class);
        loanProductRepository = mock(LegacyLoanProductRepository.class);
        paymentRepository = mock(LegacyPaymentRepository.class);
        service = new LoanService(borrowerRepository, loanAccountRepository,
                loanProductRepository, paymentRepository);
    }

    // ---------------------------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------------------------

    private static LegacyLoanAccount loanAccount() {
        LegacyLoanAccount acct = new LegacyLoanAccount();
        acct.setLoanAccountNumber(LOAN);
        acct.setBorrowerId(BORROWER);
        acct.setBorrowerFirstName("James");
        acct.setBorrowerLastName("Mitchell");
        acct.setProductCode("FXD30");
        acct.setOriginalAmount("285,000");
        acct.setCurrentBalance("271,432.56");
        acct.setInterestRate("4.750");
        acct.setMonthlyPayment("1,487.02");
        acct.setStatusCode("ACT");
        acct.setOriginationDate("02/15/2019");
        acct.setPropertyAddress("742 Elm Street");
        acct.setPropertyCity("Springfield");
        acct.setPropertyState("IL");
        acct.setPropertyZip("62701");
        acct.setPropertyType("SFR");
        return acct;
    }

    private static LegacyBorrower borrower() {
        LegacyBorrower b = new LegacyBorrower();
        b.setBorrowerId(BORROWER);
        b.setFirstName("James");
        b.setMiddleInitial("R");
        b.setLastName("Mitchell");
        b.setEmail("j.mitchell@email.com");
        b.setPhoneNumber("217-555-0142");
        b.setCity("Springfield");
        b.setStateCode("IL");
        b.setCreditScore("745");
        b.setEmploymentStatus("EMPLOYED");
        return b;
    }

    private static LegacyPayment payment() {
        LegacyPayment p = new LegacyPayment();
        p.setPaymentSequenceNumber("PMT-TEST-1");
        p.setLoanAccountNumber(LOAN);
        p.setPaymentDate("12/15/2025");
        p.setTotalAmount("1,487.02");
        p.setPrincipalAmount("456.78");
        p.setInterestAmount("1,074.69");
        p.setEscrowAmount("355.55");
        p.setLateFee("0.00");
        p.setTypeCode("REG");
        p.setStatusCode("PST");
        return p;
    }

    /** Runs {@code getLoanById} for the fixture with no matching product. */
    private LoanSummaryDto translate(LegacyLoanAccount acct) {
        when(loanAccountRepository.findById(LOAN)).thenReturn(Optional.of(acct));
        when(loanProductRepository.findById(anyString())).thenReturn(Optional.empty());
        return service.getLoanById(LOAN);
    }

    private BorrowerDto translate(LegacyBorrower b) {
        when(borrowerRepository.findById(BORROWER)).thenReturn(Optional.of(b));
        when(loanProductRepository.findAll()).thenReturn(List.of());
        when(loanAccountRepository.findByBorrowerId(BORROWER)).thenReturn(List.of());
        return service.getBorrowerById(BORROWER);
    }

    private PaymentDto translate(LegacyPayment p) {
        when(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc(LOAN)).thenReturn(List.of(p));
        return service.getPaymentsByLoan(LOAN).get(0);
    }

    // ---------------------------------------------------------------------------------------
    // parseLegacyAmount (via LoanSummaryDto.originalAmount / PaymentDto.totalAmount)
    // ---------------------------------------------------------------------------------------

    @Nested
    class ParseLegacyAmount {

        @Test
        void stripsThousandsSeparatorFromWholeNumber() {
            LegacyLoanAccount acct = loanAccount();
            acct.setOriginalAmount("285,000");
            assertThat(translate(acct).getOriginalAmount()).isEqualTo(new BigDecimal("285000"));
        }

        @Test
        void stripsThousandsSeparatorAndKeepsScale() {
            LegacyLoanAccount acct = loanAccount();
            acct.setCurrentBalance("271,432.56");
            assertThat(translate(acct).getCurrentBalance()).isEqualTo(new BigDecimal("271432.56"));
        }

        @Test
        void zeroWithScaleIsPreserved() {
            LegacyPayment p = payment();
            p.setLateFee("0.00");
            assertThat(translate(p).getLateFee()).isEqualTo(new BigDecimal("0.00"));
        }

        @Test
        void amountWithoutSeparatorIsParsedAsIs() {
            LegacyPayment p = payment();
            p.setPrincipalAmount("456.78");
            assertThat(translate(p).getPrincipalAmount()).isEqualTo(new BigDecimal("456.78"));
        }

        /**
         * KNOWN BUG (MIGRATION_ANALYSIS.md §4): a blank or null balance is silently reported as
         * zero rather than as missing. This test documents the current behaviour; it is not an
         * assertion that the behaviour is correct.
         */
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void blankAmountCurrentlyBecomesZero(String blank) {
            LegacyLoanAccount acct = loanAccount();
            acct.setCurrentBalance(blank);
            assertThat(translate(acct).getCurrentBalance()).isEqualTo(BigDecimal.ZERO);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void blankPaymentAmountCurrentlyBecomesZero(String blank) {
            LegacyPayment p = payment();
            p.setEscrowAmount(blank);
            assertThat(translate(p).getEscrowAmount()).isEqualTo(BigDecimal.ZERO);
        }

        /**
         * KNOWN BUG (MIGRATION_ANALYSIS.md §4): a non-numeric amount is not handled and the
         * exception escapes the service (surfacing as an HTTP 500 for the whole listing).
         */
        @Test
        void nonNumericAmountCurrentlyThrowsNumberFormatException() {
            LegacyLoanAccount acct = loanAccount();
            acct.setOriginalAmount("abc");
            assertThatThrownBy(() -> translate(acct)).isInstanceOf(NumberFormatException.class);
        }

        @Test
        void currencySymbolIsNotStrippedAndCurrentlyThrows() {
            LegacyLoanAccount acct = loanAccount();
            acct.setMonthlyPayment("$1,487.02");
            assertThatThrownBy(() -> translate(acct)).isInstanceOf(NumberFormatException.class);
        }

        @Test
        void surroundingWhitespaceIsNotTrimmedAndCurrentlyThrows() {
            LegacyLoanAccount acct = loanAccount();
            acct.setOriginalAmount(" 285,000 ");
            assertThatThrownBy(() -> translate(acct)).isInstanceOf(NumberFormatException.class);
        }

        @Test
        void appliesToEveryPaymentAmountField() {
            LegacyPayment p = payment();
            p.setTotalAmount("1,487.02");
            p.setPrincipalAmount("456.78");
            p.setInterestAmount("1,074.69");
            p.setEscrowAmount("355.55");
            p.setLateFee("47.50");
            PaymentDto dto = translate(p);
            assertThat(dto.getTotalAmount()).isEqualTo(new BigDecimal("1487.02"));
            assertThat(dto.getPrincipalAmount()).isEqualTo(new BigDecimal("456.78"));
            assertThat(dto.getInterestAmount()).isEqualTo(new BigDecimal("1074.69"));
            assertThat(dto.getEscrowAmount()).isEqualTo(new BigDecimal("355.55"));
            assertThat(dto.getLateFee()).isEqualTo(new BigDecimal("47.50"));
        }
    }

    // ---------------------------------------------------------------------------------------
    // parseLegacyDecimal (via LoanSummaryDto.interestRate)
    // ---------------------------------------------------------------------------------------

    @Nested
    class ParseLegacyDecimal {

        @Test
        void parsesRateKeepingScale() {
            LegacyLoanAccount acct = loanAccount();
            acct.setInterestRate("4.750");
            assertThat(translate(acct).getInterestRate()).isEqualTo(new BigDecimal("4.750"));
        }

        @Test
        void trimsSurroundingWhitespace() {
            LegacyLoanAccount acct = loanAccount();
            acct.setInterestRate("  3.125\t");
            assertThat(translate(acct).getInterestRate()).isEqualTo(new BigDecimal("3.125"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void blankDecimalCurrentlyBecomesZero(String blank) {
            LegacyLoanAccount acct = loanAccount();
            acct.setInterestRate(blank);
            assertThat(translate(acct).getInterestRate()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void thousandsSeparatorIsNotStrippedAndCurrentlyThrows() {
            LegacyLoanAccount acct = loanAccount();
            acct.setInterestRate("1,000.5");
            assertThatThrownBy(() -> translate(acct)).isInstanceOf(NumberFormatException.class);
        }

        @Test
        void nonNumericDecimalCurrentlyThrowsNumberFormatException() {
            LegacyLoanAccount acct = loanAccount();
            acct.setInterestRate("N/A");
            assertThatThrownBy(() -> translate(acct)).isInstanceOf(NumberFormatException.class);
        }
    }

    // ---------------------------------------------------------------------------------------
    // parseLegacyInteger (via BorrowerDto.creditScore)
    // ---------------------------------------------------------------------------------------

    @Nested
    class ParseLegacyInteger {

        @Test
        void parsesInteger() {
            LegacyBorrower b = borrower();
            b.setCreditScore("745");
            assertThat(translate(b).getCreditScore()).isEqualTo(745);
        }

        @Test
        void trimsSurroundingWhitespace() {
            LegacyBorrower b = borrower();
            b.setCreditScore(" 780 ");
            assertThat(translate(b).getCreditScore()).isEqualTo(780);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void blankIntegerBecomesNullNotZero(String blank) {
            LegacyBorrower b = borrower();
            b.setCreditScore(blank);
            assertThat(translate(b).getCreditScore()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "745.0", "7,45"})
        void nonNumericIntegerCurrentlyThrowsNumberFormatException(String bad) {
            LegacyBorrower b = borrower();
            b.setCreditScore(bad);
            assertThatThrownBy(() -> translate(b)).isInstanceOf(NumberFormatException.class);
        }

        @Test
        void noRangeValidationIsApplied() {
            LegacyBorrower b = borrower();
            b.setCreditScore("9999");
            assertThat(translate(b).getCreditScore()).isEqualTo(9999);
        }
    }

    // ---------------------------------------------------------------------------------------
    // expandStatusCode (via LoanSummaryDto.status)
    // ---------------------------------------------------------------------------------------

    @Nested
    class ExpandStatusCode {

        @ParameterizedTest
        @CsvSource({
                "ACT, Active",
                "CLO, Closed",
                "DFT, Default",
                "FRB, Forbearance"})
        void mapsEveryKnownCode(String code, String expected) {
            LegacyLoanAccount acct = loanAccount();
            acct.setStatusCode(code);
            assertThat(translate(acct).getStatus()).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"XYZ", "act", "", " "})
        void unmappedCodeFallsBackToRawCode(String code) {
            LegacyLoanAccount acct = loanAccount();
            acct.setStatusCode(code);
            assertThat(translate(acct).getStatus()).isEqualTo(code);
        }

        @Test
        void nullCodeBecomesUnknown() {
            LegacyLoanAccount acct = loanAccount();
            acct.setStatusCode(null);
            assertThat(translate(acct).getStatus()).isEqualTo("Unknown");
        }
    }

    // ---------------------------------------------------------------------------------------
    // expandPropertyType (via LoanSummaryDto.propertyType)
    // ---------------------------------------------------------------------------------------

    @Nested
    class ExpandPropertyType {

        @ParameterizedTest
        @CsvSource({
                "SFR, Single Family Residence",
                "CND, Condominium",
                "MFR, Multi-Family Residence",
                "TWN, Townhouse"})
        void mapsEveryKnownCode(String code, String expected) {
            LegacyLoanAccount acct = loanAccount();
            acct.setPropertyType(code);
            assertThat(translate(acct).getPropertyType()).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"XYZ", "sfr", ""})
        void unmappedCodeFallsBackToRawCode(String code) {
            LegacyLoanAccount acct = loanAccount();
            acct.setPropertyType(code);
            assertThat(translate(acct).getPropertyType()).isEqualTo(code);
        }

        @Test
        void nullCodeBecomesUnknown() {
            LegacyLoanAccount acct = loanAccount();
            acct.setPropertyType(null);
            assertThat(translate(acct).getPropertyType()).isEqualTo("Unknown");
        }
    }

    // ---------------------------------------------------------------------------------------
    // expandPaymentType (via PaymentDto.type)
    // ---------------------------------------------------------------------------------------

    @Nested
    class ExpandPaymentType {

        @ParameterizedTest
        @CsvSource({
                "REG, Regular",
                "EXT, Extra",
                "PRT, Partial",
                "PRE, Prepayment"})
        void mapsEveryKnownCode(String code, String expected) {
            LegacyPayment p = payment();
            p.setTypeCode(code);
            assertThat(translate(p).getType()).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"XYZ", "reg", ""})
        void unmappedCodeFallsBackToRawCode(String code) {
            LegacyPayment p = payment();
            p.setTypeCode(code);
            assertThat(translate(p).getType()).isEqualTo(code);
        }

        @Test
        void nullCodeBecomesUnknown() {
            LegacyPayment p = payment();
            p.setTypeCode(null);
            assertThat(translate(p).getType()).isEqualTo("Unknown");
        }
    }

    // ---------------------------------------------------------------------------------------
    // expandPaymentStatus (via PaymentDto.status)
    // ---------------------------------------------------------------------------------------

    @Nested
    class ExpandPaymentStatus {

        @ParameterizedTest
        @CsvSource({
                "PST, Posted",
                "REV, Reversed",
                "NSF, Non-Sufficient Funds",
                "PND, Pending"})
        void mapsEveryKnownCode(String code, String expected) {
            LegacyPayment p = payment();
            p.setStatusCode(code);
            assertThat(translate(p).getStatus()).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"XYZ", "pst", ""})
        void unmappedCodeFallsBackToRawCode(String code) {
            LegacyPayment p = payment();
            p.setStatusCode(code);
            assertThat(translate(p).getStatus()).isEqualTo(code);
        }

        @Test
        void nullCodeBecomesUnknown() {
            LegacyPayment p = payment();
            p.setStatusCode(null);
            assertThat(translate(p).getStatus()).isEqualTo("Unknown");
        }
    }

    // ---------------------------------------------------------------------------------------
    // name / address concatenation
    // ---------------------------------------------------------------------------------------

    @Nested
    class Concatenation {

        @Test
        void loanBorrowerNameIsFirstSpaceLast() {
            assertThat(translate(loanAccount()).getBorrowerName()).isEqualTo("James Mitchell");
        }

        @Test
        void loanBorrowerNameWithNullPartsCurrentlyRendersLiteralNull() {
            LegacyLoanAccount acct = loanAccount();
            acct.setBorrowerFirstName(null);
            acct.setBorrowerLastName(null);
            assertThat(translate(acct).getBorrowerName()).isEqualTo("null null");
        }

        @Test
        void borrowerFullNameIncludesMiddleInitialWithPeriod() {
            assertThat(translate(borrower()).getFullName()).isEqualTo("James R. Mitchell");
        }

        /** Mirrors seed borrower B-10005 (Robert Williams, no middle initial). */
        @Test
        void borrowerFullNameOmitsMiddleInitialWhenNull() {
            LegacyBorrower b = borrower();
            b.setFirstName("Robert");
            b.setMiddleInitial(null);
            b.setLastName("Williams");
            assertThat(translate(b).getFullName()).isEqualTo("Robert Williams");
        }

        @Test
        void borrowerFullNameWithEmptyMiddleInitialCurrentlyRendersDanglingPeriod() {
            LegacyBorrower b = borrower();
            b.setMiddleInitial("");
            assertThat(translate(b).getFullName()).isEqualTo("James . Mitchell");
        }

        @Test
        void borrowerFullNameWithNullNamesCurrentlyRendersLiteralNull() {
            LegacyBorrower b = borrower();
            b.setFirstName(null);
            b.setMiddleInitial(null);
            b.setLastName(null);
            assertThat(translate(b).getFullName()).isEqualTo("null null");
        }

        @Test
        void propertyAddressIsStreetCommaCityCommaStateSpaceZip() {
            assertThat(translate(loanAccount()).getPropertyAddress())
                    .isEqualTo("742 Elm Street, Springfield, IL 62701");
        }

        @Test
        void propertyAddressWithNullPartsCurrentlyRendersLiteralNull() {
            LegacyLoanAccount acct = loanAccount();
            acct.setPropertyAddress(null);
            acct.setPropertyCity(null);
            acct.setPropertyState(null);
            acct.setPropertyZip(null);
            assertThat(translate(acct).getPropertyAddress()).isEqualTo("null, null, null null");
        }

        @Test
        void productDescriptionUsesProductWhenPresent() {
            LegacyLoanProduct product = new LegacyLoanProduct();
            product.setProductCode("FXD30");
            product.setDescription("30-Year Fixed Rate Mortgage");
            when(loanAccountRepository.findById(LOAN)).thenReturn(Optional.of(loanAccount()));
            when(loanProductRepository.findById("FXD30")).thenReturn(Optional.of(product));
            assertThat(service.getLoanById(LOAN).getProductDescription())
                    .isEqualTo("30-Year Fixed Rate Mortgage");
        }

        @Test
        void productDescriptionFallsBackToRawProductCodeWhenProductMissing() {
            assertThat(translate(loanAccount()).getProductDescription()).isEqualTo("FXD30");
        }
    }

    // ---------------------------------------------------------------------------------------
    // dates are passed through unparsed
    // ---------------------------------------------------------------------------------------

    @Nested
    class DatePassThrough {

        @ParameterizedTest
        @ValueSource(strings = {"02/15/2019", "13/40/2025", "not-a-date", ""})
        void originationDateIsCopiedVerbatim(String raw) {
            LegacyLoanAccount acct = loanAccount();
            acct.setOriginationDate(raw);
            assertThat(translate(acct).getOriginationDate()).isEqualTo(raw);
        }

        @Test
        void nullOriginationDateStaysNull() {
            LegacyLoanAccount acct = loanAccount();
            acct.setOriginationDate(null);
            assertThat(translate(acct).getOriginationDate()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"12/15/2025", "99/99/9999", "yesterday", ""})
        void paymentDateIsCopiedVerbatim(String raw) {
            LegacyPayment p = payment();
            p.setPaymentDate(raw);
            assertThat(translate(p).getPaymentDate()).isEqualTo(raw);
        }

        @Test
        void nullPaymentDateStaysNull() {
            LegacyPayment p = payment();
            p.setPaymentDate(null);
            assertThat(translate(p).getPaymentDate()).isNull();
        }
    }

    // ---------------------------------------------------------------------------------------
    // remaining straight copies
    // ---------------------------------------------------------------------------------------

    @Nested
    class StraightCopies {

        @Test
        void borrowerScalarFieldsAreCopiedVerbatim() {
            BorrowerDto dto = translate(borrower());
            assertThat(dto.getId()).isEqualTo(BORROWER);
            assertThat(dto.getEmail()).isEqualTo("j.mitchell@email.com");
            assertThat(dto.getPhone()).isEqualTo("217-555-0142");
            assertThat(dto.getCity()).isEqualTo("Springfield");
            assertThat(dto.getState()).isEqualTo("IL");
            assertThat(dto.getEmploymentStatus()).isEqualTo("EMPLOYED");
        }

        @Test
        void paymentIdentifiersAreCopiedVerbatim() {
            PaymentDto dto = translate(payment());
            assertThat(dto.getPaymentId()).isEqualTo("PMT-TEST-1");
            assertThat(dto.getLoanAccountNumber()).isEqualTo(LOAN);
        }
    }
}
