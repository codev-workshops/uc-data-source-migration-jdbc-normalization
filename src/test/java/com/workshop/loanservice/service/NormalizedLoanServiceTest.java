package com.workshop.loanservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.normalized.Borrower;
import com.workshop.loanservice.entity.normalized.LoanAccount;
import com.workshop.loanservice.entity.normalized.LoanProduct;
import com.workshop.loanservice.entity.normalized.Payment;
import com.workshop.loanservice.exception.ResourceNotFoundException;
import com.workshop.loanservice.repository.normalized.BorrowerRepository;
import com.workshop.loanservice.repository.normalized.LoanAccountRepository;
import com.workshop.loanservice.repository.normalized.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NormalizedLoanServiceTest {

  private static final String LOAN_ID = "LN-2019-00142";
  private static final String BORROWER_ID = "B-10001";

  @Mock private BorrowerRepository borrowerRepository;
  @Mock private LoanAccountRepository loanAccountRepository;
  @Mock private PaymentRepository paymentRepository;

  @InjectMocks private NormalizedLoanService service;

  @Test
  void getAllLoansProducesSameDtoAsLegacyPath() {
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
  void getLoanByIdResolvesBorrowerNameThroughJoin() {
    LoanAccount account = loanAccount();
    account.getBorrower().setFirstName("Sarah");
    account.getBorrower().setLastName("Chen");
    given(loanAccountRepository.findById(LOAN_ID)).willReturn(Optional.of(account));

    assertThat(service.getLoanById(LOAN_ID).getBorrowerName()).isEqualTo("Sarah Chen");
  }

  @Test
  void getLoanByIdFormatsDateAsLegacyString() {
    LoanAccount account = loanAccount();
    account.setOriginationDate(LocalDate.of(2021, 10, 1));
    given(loanAccountRepository.findById(LOAN_ID)).willReturn(Optional.of(account));

    assertThat(service.getLoanById(LOAN_ID).getOriginationDate()).isEqualTo("10/01/2021");
  }

  @Test
  void getLoanByIdReturnsNullDateAndZeroAmountsForNullColumns() {
    LoanAccount account = loanAccount();
    account.setOriginationDate(null);
    account.setOriginalAmount(null);
    account.setInterestRate(null);
    given(loanAccountRepository.findById(LOAN_ID)).willReturn(Optional.of(account));

    LoanSummaryDto dto = service.getLoanById(LOAN_ID);

    assertThat(dto.getOriginationDate()).isNull();
    assertThat(dto.getOriginalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(dto.getInterestRate()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void getLoanByIdFallsBackToRawProductCodeWhenProductMissing() {
    LoanAccount account = loanAccount();
    account.setLoanProduct(null);
    given(loanAccountRepository.findById(LOAN_ID)).willReturn(Optional.of(account));

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
  void getAllBorrowersProducesSameDtoAsLegacyPath() {
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
    Borrower borrower = borrower();
    borrower.setMiddleInitial(null);
    given(borrowerRepository.findAll()).willReturn(List.of(borrower));

    assertThat(service.getAllBorrowers().get(0).getFullName()).isEqualTo("James Mitchell");
  }

  @Test
  void getBorrowerByIdAttachesLoans() {
    given(borrowerRepository.findById(BORROWER_ID)).willReturn(Optional.of(borrower()));
    given(loanAccountRepository.findByBorrowerBorrowerId(BORROWER_ID))
        .willReturn(List.of(loanAccount()));

    BorrowerDto dto = service.getBorrowerById(BORROWER_ID);

    assertThat(dto.getFullName()).isEqualTo("James R. Mitchell");
    assertThat(dto.getLoans()).hasSize(1);
    assertThat(dto.getLoans().get(0).getLoanAccountNumber()).isEqualTo(LOAN_ID);
  }

  @Test
  void getBorrowerByIdThrowsWhenNotFound() {
    given(borrowerRepository.findById("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getBorrowerById("missing"))
        .isExactlyInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Borrower not found: missing");
  }

  @Test
  void getPaymentsByLoanProducesSameDtoAsLegacyPath() {
    given(paymentRepository.findByLoanAccountLoanAccountNumberOrderByPaymentDateDesc(LOAN_ID))
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
  void paymentWithNullAmountsAndUnknownCodes() {
    Payment payment = payment();
    payment.setLateFee(null);
    payment.setPaymentDate(null);
    payment.setTypeCode(null);
    payment.setStatusCode("ZZZ");
    given(paymentRepository.findByLoanAccountLoanAccountNumberOrderByPaymentDateDesc(LOAN_ID))
        .willReturn(List.of(payment));

    PaymentDto dto = service.getPaymentsByLoan(LOAN_ID).get(0);

    assertThat(dto.getLateFee()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(dto.getPaymentDate()).isNull();
    assertThat(dto.getType()).isEqualTo("Unknown");
    assertThat(dto.getStatus()).isEqualTo("ZZZ");
  }

  private static LoanProduct product() {
    LoanProduct product = new LoanProduct();
    product.setProductCode("FXD30");
    product.setDescription("30-Year Fixed Rate Mortgage");
    return product;
  }

  private static Borrower borrower() {
    Borrower borrower = new Borrower();
    borrower.setBorrowerId(BORROWER_ID);
    borrower.setFirstName("James");
    borrower.setLastName("Mitchell");
    borrower.setMiddleInitial("R");
    borrower.setEmail("j.mitchell@email.com");
    borrower.setPhoneNumber("217-555-0142");
    borrower.setCity("Springfield");
    borrower.setStateCode("IL");
    borrower.setCreditScore(745);
    borrower.setEmploymentStatus("EMPLOYED");
    return borrower;
  }

  private static LoanAccount loanAccount() {
    LoanAccount acct = new LoanAccount();
    acct.setLoanAccountNumber(LOAN_ID);
    acct.setBorrower(borrower());
    acct.setLoanProduct(product());
    ReflectionTestUtils.setField(acct, "productCode", "FXD30");
    acct.setOriginalAmount(new BigDecimal("285000.00"));
    acct.setCurrentBalance(new BigDecimal("271432.56"));
    acct.setInterestRate(new BigDecimal("4.750"));
    acct.setMonthlyPayment(new BigDecimal("1487.02"));
    acct.setOriginationDate(LocalDate.of(2019, 2, 15));
    acct.setStatusCode("ACT");
    acct.setPropertyAddressLine1("742 Elm Street");
    acct.setPropertyCity("Springfield");
    acct.setPropertyState("IL");
    acct.setPropertyZip("62701");
    acct.setPropertyType("SFR");
    return acct;
  }

  private static Payment payment() {
    Payment pmt = new Payment();
    pmt.setPaymentId("PMT-2025120001");
    pmt.setLoanAccount(loanAccount());
    pmt.setPaymentDate(LocalDate.of(2025, 12, 15));
    pmt.setTotalAmount(new BigDecimal("1487.02"));
    pmt.setPrincipalAmount(new BigDecimal("456.78"));
    pmt.setInterestAmount(new BigDecimal("1074.69"));
    pmt.setEscrowAmount(new BigDecimal("355.55"));
    pmt.setLateFee(new BigDecimal("0.00"));
    pmt.setTypeCode("REG");
    pmt.setStatusCode("PST");
    return pmt;
  }
}
