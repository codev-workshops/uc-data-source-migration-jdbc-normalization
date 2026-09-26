package com.workshop.loanservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.exception.ResourceNotFoundException;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegacyLoanServiceTest {

  private static final String LOAN_ID = "LN-2019-00142";
  private static final String BORROWER_ID = "B-10001";

  @Mock private LegacyBorrowerRepository borrowerRepository;
  @Mock private LegacyLoanAccountRepository loanAccountRepository;
  @Mock private LegacyLoanProductRepository loanProductRepository;
  @Mock private LegacyPaymentRepository paymentRepository;

  @InjectMocks private LegacyLoanService service;

  @Test
  void getAllLoansTranslatesLegacyFields() {
    given(loanProductRepository.findAll()).willReturn(List.of(product()));
    given(loanAccountRepository.findAll()).willReturn(List.of(loanAccount()));

    List<LoanSummaryDto> loans = service.getAllLoans();

    assertThat(loans).hasSize(1);
    LoanSummaryDto dto = loans.get(0);
    assertThat(dto.getLoanAccountNumber()).isEqualTo(LOAN_ID);
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
  void getAllLoansReturnsEmptyListWhenNoAccounts() {
    given(loanProductRepository.findAll()).willReturn(List.of());
    given(loanAccountRepository.findAll()).willReturn(List.of());

    assertThat(service.getAllLoans()).isEmpty();
  }

  @Test
  void getLoanByIdReturnsTranslatedLoan() {
    given(loanAccountRepository.findById(LOAN_ID)).willReturn(Optional.of(loanAccount()));
    given(loanProductRepository.findById("FXD30")).willReturn(Optional.of(product()));

    LoanSummaryDto dto = service.getLoanById(LOAN_ID);

    assertThat(dto.getLoanAccountNumber()).isEqualTo(LOAN_ID);
    assertThat(dto.getProductDescription()).isEqualTo("30-Year Fixed Rate Mortgage");
    assertThat(dto.getStatus()).isEqualTo("Active");
  }

  @Test
  void getLoanByIdFallsBackToRawProductCodeWhenProductMissing() {
    given(loanAccountRepository.findById(LOAN_ID)).willReturn(Optional.of(loanAccount()));
    given(loanProductRepository.findById("FXD30")).willReturn(Optional.empty());

    assertThat(service.getLoanById(LOAN_ID).getProductDescription()).isEqualTo("FXD30");
  }

  @Test
  void getLoanByIdThrowsWhenNotFound() {
    given(loanAccountRepository.findById("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getLoanById("missing"))
        .isExactlyInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Loan not found: missing");
  }

  @Test
  void getLoanByIdThrowsNumberFormatExceptionOnMalformedAmount() {
    LegacyLoanAccount acct = loanAccount();
    acct.setOriginalAmount("not-a-number");
    given(loanAccountRepository.findById(LOAN_ID)).willReturn(Optional.of(acct));
    given(loanProductRepository.findById("FXD30")).willReturn(Optional.of(product()));

    assertThatThrownBy(() -> service.getLoanById(LOAN_ID))
        .isInstanceOf(NumberFormatException.class);
  }

  @Test
  void getLoanByIdThrowsNumberFormatExceptionOnMalformedInterestRate() {
    LegacyLoanAccount acct = loanAccount();
    acct.setInterestRate("4.75%");
    given(loanAccountRepository.findById(LOAN_ID)).willReturn(Optional.of(acct));
    given(loanProductRepository.findById("FXD30")).willReturn(Optional.of(product()));

    assertThatThrownBy(() -> service.getLoanById(LOAN_ID))
        .isInstanceOf(NumberFormatException.class);
  }

  @Test
  void blankAndNullAmountsTranslateToZeroAndUnknownCodesPassThrough() {
    LegacyLoanAccount acct = loanAccount();
    acct.setOriginalAmount(null);
    acct.setCurrentBalance("  ");
    acct.setInterestRate(null);
    acct.setStatusCode("XYZ");
    acct.setPropertyType(null);
    given(loanAccountRepository.findById(LOAN_ID)).willReturn(Optional.of(acct));
    given(loanProductRepository.findById("FXD30")).willReturn(Optional.of(product()));

    LoanSummaryDto dto = service.getLoanById(LOAN_ID);

    assertThat(dto.getOriginalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(dto.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(dto.getInterestRate()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(dto.getStatus()).isEqualTo("XYZ");
    assertThat(dto.getPropertyType()).isEqualTo("Unknown");
  }

  @Test
  void getAllBorrowersTranslatesFullNameAndCreditScore() {
    given(borrowerRepository.findAll()).willReturn(List.of(borrower()));

    List<BorrowerDto> borrowers = service.getAllBorrowers();

    assertThat(borrowers).hasSize(1);
    BorrowerDto dto = borrowers.get(0);
    assertThat(dto.getId()).isEqualTo(BORROWER_ID);
    assertThat(dto.getFullName()).isEqualTo("James R. Mitchell");
    assertThat(dto.getEmail()).isEqualTo("j.mitchell@email.com");
    assertThat(dto.getPhone()).isEqualTo("217-555-0142");
    assertThat(dto.getCity()).isEqualTo("Springfield");
    assertThat(dto.getState()).isEqualTo("IL");
    assertThat(dto.getCreditScore()).isEqualTo(745);
    assertThat(dto.getEmploymentStatus()).isEqualTo("EMPLOYED");
    assertThat(dto.getLoans()).isNull();
  }

  @Test
  void fullNameOmitsMiddleInitialWhenAbsent() {
    LegacyBorrower borrower = borrower();
    borrower.setMiddleInitial(null);
    given(borrowerRepository.findAll()).willReturn(List.of(borrower));

    assertThat(service.getAllBorrowers().get(0).getFullName()).isEqualTo("James Mitchell");
  }

  @Test
  void blankCreditScoreTranslatesToNull() {
    LegacyBorrower borrower = borrower();
    borrower.setCreditScore("");
    given(borrowerRepository.findAll()).willReturn(List.of(borrower));

    assertThat(service.getAllBorrowers().get(0).getCreditScore()).isNull();
  }

  @Test
  void malformedCreditScoreThrowsNumberFormatException() {
    LegacyBorrower borrower = borrower();
    borrower.setCreditScore("seven-forty-five");
    given(borrowerRepository.findAll()).willReturn(List.of(borrower));

    assertThatThrownBy(() -> service.getAllBorrowers()).isInstanceOf(NumberFormatException.class);
  }

  @Test
  void getBorrowerByIdAttachesLoans() {
    given(borrowerRepository.findById(BORROWER_ID)).willReturn(Optional.of(borrower()));
    given(loanProductRepository.findAll()).willReturn(List.of(product()));
    given(loanAccountRepository.findByBorrowerId(BORROWER_ID)).willReturn(List.of(loanAccount()));

    BorrowerDto dto = service.getBorrowerById(BORROWER_ID);

    assertThat(dto.getFullName()).isEqualTo("James R. Mitchell");
    assertThat(dto.getLoans()).hasSize(1);
    assertThat(dto.getLoans().get(0).getLoanAccountNumber()).isEqualTo(LOAN_ID);
    assertThat(dto.getLoans().get(0).getProductDescription())
        .isEqualTo("30-Year Fixed Rate Mortgage");
  }

  @Test
  void getBorrowerByIdReturnsEmptyLoansWhenBorrowerHasNone() {
    given(borrowerRepository.findById(BORROWER_ID)).willReturn(Optional.of(borrower()));
    given(loanProductRepository.findAll()).willReturn(List.of());
    given(loanAccountRepository.findByBorrowerId(BORROWER_ID)).willReturn(List.of());

    assertThat(service.getBorrowerById(BORROWER_ID).getLoans()).isEmpty();
  }

  @Test
  void getBorrowerByIdThrowsWhenNotFound() {
    given(borrowerRepository.findById("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getBorrowerById("missing"))
        .isExactlyInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Borrower not found: missing");
  }

  @Test
  void getPaymentsByLoanTranslatesAmountsAndCodes() {
    given(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc(LOAN_ID))
        .willReturn(List.of(payment()));

    List<PaymentDto> payments = service.getPaymentsByLoan(LOAN_ID);

    assertThat(payments).hasSize(1);
    PaymentDto dto = payments.get(0);
    assertThat(dto.getPaymentId()).isEqualTo("PMT-2025120001");
    assertThat(dto.getLoanAccountNumber()).isEqualTo(LOAN_ID);
    assertThat(dto.getPaymentDate()).isEqualTo("12/15/2025");
    assertThat(dto.getTotalAmount()).isEqualByComparingTo("1487.02");
    assertThat(dto.getPrincipalAmount()).isEqualByComparingTo("456.78");
    assertThat(dto.getInterestAmount()).isEqualByComparingTo("1074.69");
    assertThat(dto.getEscrowAmount()).isEqualByComparingTo("355.55");
    assertThat(dto.getLateFee()).isEqualByComparingTo("0.00");
    assertThat(dto.getType()).isEqualTo("Regular");
    assertThat(dto.getStatus()).isEqualTo("Posted");
  }

  @Test
  void paymentCodeExpansionsCoverAllKnownCodes() {
    LegacyPayment nsf = payment();
    nsf.setTypeCode("PRE");
    nsf.setStatusCode("NSF");
    LegacyPayment unknown = payment();
    unknown.setTypeCode(null);
    unknown.setStatusCode("ZZZ");
    given(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc(LOAN_ID))
        .willReturn(List.of(nsf, unknown));

    List<PaymentDto> payments = service.getPaymentsByLoan(LOAN_ID);

    assertThat(payments.get(0).getType()).isEqualTo("Prepayment");
    assertThat(payments.get(0).getStatus()).isEqualTo("Non-Sufficient Funds");
    assertThat(payments.get(1).getType()).isEqualTo("Unknown");
    assertThat(payments.get(1).getStatus()).isEqualTo("ZZZ");
  }

  @Test
  void malformedPaymentAmountThrowsNumberFormatException() {
    LegacyPayment payment = payment();
    payment.setTotalAmount("1,487.02 USD");
    given(paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc(LOAN_ID))
        .willReturn(List.of(payment));

    assertThatThrownBy(() -> service.getPaymentsByLoan(LOAN_ID))
        .isInstanceOf(NumberFormatException.class);
  }

  private static LegacyLoanProduct product() {
    LegacyLoanProduct product = new LegacyLoanProduct();
    product.setProductCode("FXD30");
    product.setDescription("30-Year Fixed Rate Mortgage");
    return product;
  }

  private static LegacyLoanAccount loanAccount() {
    LegacyLoanAccount acct = new LegacyLoanAccount();
    acct.setLoanAccountNumber(LOAN_ID);
    acct.setBorrowerId(BORROWER_ID);
    acct.setBorrowerFirstName("James");
    acct.setBorrowerLastName("Mitchell");
    acct.setProductCode("FXD30");
    acct.setOriginalAmount("285,000");
    acct.setCurrentBalance("271,432.56");
    acct.setInterestRate("4.750");
    acct.setMonthlyPayment("1,487.02");
    acct.setOriginationDate("02/15/2019");
    acct.setStatusCode("ACT");
    acct.setPropertyAddress("742 Elm Street");
    acct.setPropertyCity("Springfield");
    acct.setPropertyState("IL");
    acct.setPropertyZip("62701");
    acct.setPropertyType("SFR");
    return acct;
  }

  private static LegacyBorrower borrower() {
    LegacyBorrower borrower = new LegacyBorrower();
    borrower.setBorrowerId(BORROWER_ID);
    borrower.setFirstName("James");
    borrower.setLastName("Mitchell");
    borrower.setMiddleInitial("R");
    borrower.setEmail("j.mitchell@email.com");
    borrower.setPhoneNumber("217-555-0142");
    borrower.setCity("Springfield");
    borrower.setStateCode("IL");
    borrower.setCreditScore("745");
    borrower.setEmploymentStatus("EMPLOYED");
    return borrower;
  }

  private static LegacyPayment payment() {
    LegacyPayment pmt = new LegacyPayment();
    pmt.setPaymentSequenceNumber("PMT-2025120001");
    pmt.setLoanAccountNumber(LOAN_ID);
    pmt.setPaymentDate("12/15/2025");
    pmt.setTotalAmount("1,487.02");
    pmt.setPrincipalAmount("456.78");
    pmt.setInterestAmount("1,074.69");
    pmt.setEscrowAmount("355.55");
    pmt.setLateFee("0.00");
    pmt.setTypeCode("REG");
    pmt.setStatusCode("PST");
    return pmt;
  }
}
