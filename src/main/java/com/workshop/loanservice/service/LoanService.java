package com.workshop.loanservice.service;

import com.workshop.loanservice.domain.CodedValue;
import com.workshop.loanservice.domain.LoanStatus;
import com.workshop.loanservice.domain.PaymentStatus;
import com.workshop.loanservice.domain.PaymentType;
import com.workshop.loanservice.domain.PropertyType;
import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads loan data from the modern normalized schema and builds the API DTOs.
 *
 * <p>Public identifiers are unchanged: loans are addressed by account number and borrowers by
 * their external (legacy) id; both are resolved to the modern surrogate keys here.
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

  /** Date format of the existing API contract. */
  private static final DateTimeFormatter API_DATE_FORMAT =
      DateTimeFormatter.ofPattern("MM/dd/yyyy");
  private static final Sort BY_ID = Sort.by("id");

  private final BorrowerRepository borrowerRepository;
  private final LoanAccountRepository loanAccountRepository;
  private final PaymentRepository paymentRepository;

  public LoanService(BorrowerRepository borrowerRepository,
      LoanAccountRepository loanAccountRepository,
      PaymentRepository paymentRepository) {
    this.borrowerRepository = borrowerRepository;
    this.loanAccountRepository = loanAccountRepository;
    this.paymentRepository = paymentRepository;
  }

  public List<LoanSummaryDto> getAllLoans() {
    return loanAccountRepository.findAll(BY_ID).stream()
        .map(this::toLoanSummary)
        .toList();
  }

  public LoanSummaryDto getLoanById(String loanAccountNumber) {
    return loanAccountRepository.findByAccountNumber(loanAccountNumber)
        .map(this::toLoanSummary)
        .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
  }

  public List<BorrowerDto> getAllBorrowers() {
    return borrowerRepository.findAll(BY_ID).stream()
        .map(this::toBorrowerDto)
        .toList();
  }

  public BorrowerDto getBorrowerById(String borrowerId) {
    Borrower borrower = borrowerRepository.findByExternalId(borrowerId)
        .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
    BorrowerDto dto = toBorrowerDto(borrower);
    dto.setLoans(loanAccountRepository.findByBorrowerIdOrderByIdAsc(borrower.getId()).stream()
        .map(this::toLoanSummary)
        .toList());
    return dto;
  }

  public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
    return loanAccountRepository.findByAccountNumber(loanAccountNumber)
        .map(account -> paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(
            account.getId()))
        .orElseGet(List::of)
        .stream()
        .map(this::toPaymentDto)
        .toList();
  }

  private LoanSummaryDto toLoanSummary(LoanAccount account) {
    Borrower borrower = account.getBorrower();
    LoanSummaryDto dto = new LoanSummaryDto();
    dto.setLoanAccountNumber(account.getAccountNumber());
    dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
    dto.setProductDescription(account.getProduct().getName());
    dto.setOriginalAmount(orZero(account.getOriginalAmount()));
    dto.setCurrentBalance(orZero(account.getCurrentBalance()));
    dto.setInterestRate(orZero(account.getInterestRate()));
    dto.setMonthlyPayment(orZero(account.getMonthlyPayment()));
    dto.setStatus(CodedValue.labelFor(LoanStatus.class, account.getStatus()));
    dto.setOriginationDate(formatDate(account.getOriginationDate()));
    dto.setPropertyAddress(account.getPropertyAddress() + ", " + account.getPropertyCity()
        + ", " + account.getPropertyState() + " " + account.getPropertyZip());
    dto.setPropertyType(CodedValue.labelFor(PropertyType.class, account.getPropertyType()));
    return dto;
  }

  private BorrowerDto toBorrowerDto(Borrower borrower) {
    BorrowerDto dto = new BorrowerDto();
    dto.setId(borrower.getExternalId());
    String middle = borrower.getMiddleInitial() != null
        ? " " + borrower.getMiddleInitial() + "."
        : "";
    dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
    dto.setEmail(borrower.getEmail());
    dto.setPhone(borrower.getPhone());
    dto.setCity(borrower.getCity());
    dto.setState(borrower.getState());
    dto.setCreditScore(borrower.getCreditScore());
    dto.setEmploymentStatus(borrower.getEmploymentStatus());
    return dto;
  }

  private PaymentDto toPaymentDto(Payment payment) {
    PaymentDto dto = new PaymentDto();
    dto.setPaymentId(payment.getExternalId());
    dto.setLoanAccountNumber(payment.getLoanAccount().getAccountNumber());
    dto.setPaymentDate(formatDate(payment.getPaymentDate()));
    dto.setTotalAmount(orZero(payment.getTotalAmount()));
    dto.setPrincipalAmount(orZero(payment.getPrincipalAmount()));
    dto.setInterestAmount(orZero(payment.getInterestAmount()));
    dto.setEscrowAmount(orZero(payment.getEscrowAmount()));
    dto.setLateFee(orZero(payment.getLateFee()));
    dto.setType(CodedValue.labelFor(PaymentType.class, payment.getType()));
    dto.setStatus(CodedValue.labelFor(PaymentStatus.class, payment.getStatus()));
    return dto;
  }

  private static String formatDate(LocalDate date) {
    return date == null ? null : API_DATE_FORMAT.format(date);
  }

  /** The API has always reported missing amounts as zero. */
  private static BigDecimal orZero(BigDecimal amount) {
    return amount == null ? BigDecimal.ZERO : amount;
  }
}
