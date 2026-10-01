package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads loan data from the modern normalized schema and maps entities to API DTOs.
 *
 * <p>All type conversion and code expansion happen in the data itself (see {@code
 * data-modern.sql}); this layer only assembles DTOs.
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

  private final BorrowerRepository borrowerRepository;
  private final LoanAccountRepository loanAccountRepository;
  private final PaymentRepository paymentRepository;

  /** Creates the service backed by the modern repositories. */
  public LoanService(
      BorrowerRepository borrowerRepository,
      LoanAccountRepository loanAccountRepository,
      PaymentRepository paymentRepository) {
    this.borrowerRepository = borrowerRepository;
    this.loanAccountRepository = loanAccountRepository;
    this.paymentRepository = paymentRepository;
  }

  /** Returns all loans in account-creation order. */
  public List<LoanSummaryDto> getAllLoans() {
    return loanAccountRepository.findAllByOrderByIdAsc().stream()
        .map(LoanService::toLoanSummary)
        .toList();
  }

  /** Returns the loan with the given account number. */
  public LoanSummaryDto getLoanById(String accountNumber) {
    return loanAccountRepository
        .findByAccountNumber(accountNumber)
        .map(LoanService::toLoanSummary)
        .orElseThrow(() -> new RuntimeException("Loan not found: " + accountNumber));
  }

  /** Returns all borrowers without their loans. */
  public List<BorrowerDto> getAllBorrowers() {
    return borrowerRepository.findAll(Sort.by("id")).stream()
        .map(LoanService::toBorrowerDto)
        .toList();
  }

  /** Returns the borrower with the given external ID, including their loans. */
  public BorrowerDto getBorrowerById(String externalId) {
    Borrower borrower =
        borrowerRepository
            .findByExternalId(externalId)
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + externalId));
    BorrowerDto dto = toBorrowerDto(borrower);
    dto.setLoans(
        loanAccountRepository.findByBorrowerExternalIdOrderByIdAsc(externalId).stream()
            .map(LoanService::toLoanSummary)
            .toList());
    return dto;
  }

  /** Returns payments for a loan, most recent first. */
  public List<PaymentDto> getPaymentsByLoan(String accountNumber) {
    return paymentRepository
        .findByLoanAccountAccountNumberOrderByPaymentDateDesc(accountNumber)
        .stream()
        .map(LoanService::toPaymentDto)
        .toList();
  }

  private static LoanSummaryDto toLoanSummary(LoanAccount acct) {
    Borrower borrower = acct.getBorrower();
    LoanSummaryDto dto = new LoanSummaryDto();
    dto.setLoanAccountNumber(acct.getAccountNumber());
    dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
    dto.setProductDescription(acct.getProduct().getName());
    dto.setOriginalAmount(acct.getOriginalAmount());
    dto.setCurrentBalance(acct.getCurrentBalance());
    dto.setInterestRate(acct.getInterestRate());
    dto.setMonthlyPayment(acct.getMonthlyPayment());
    dto.setStatus(acct.getStatus());
    dto.setOriginationDate(isoDate(acct.getOriginationDate()));
    dto.setPropertyAddress(
        acct.getPropertyAddress()
            + ", "
            + acct.getPropertyCity()
            + ", "
            + acct.getPropertyState()
            + " "
            + acct.getPropertyZip());
    dto.setPropertyType(acct.getPropertyType());
    return dto;
  }

  private static BorrowerDto toBorrowerDto(Borrower borrower) {
    BorrowerDto dto = new BorrowerDto();
    dto.setId(borrower.getExternalId());
    String middle =
        borrower.getMiddleInitial() != null ? " " + borrower.getMiddleInitial() + "." : "";
    dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
    dto.setEmail(borrower.getEmail());
    dto.setPhone(borrower.getPhone());
    dto.setCity(borrower.getCity());
    dto.setState(borrower.getState());
    dto.setCreditScore(borrower.getCreditScore());
    dto.setEmploymentStatus(borrower.getEmploymentStatus());
    return dto;
  }

  private static PaymentDto toPaymentDto(Payment pmt) {
    PaymentDto dto = new PaymentDto();
    dto.setPaymentId(pmt.getLegacyPaymentId());
    dto.setLoanAccountNumber(pmt.getLoanAccount().getAccountNumber());
    dto.setPaymentDate(isoDate(pmt.getPaymentDate()));
    dto.setTotalAmount(pmt.getTotalAmount());
    dto.setPrincipalAmount(pmt.getPrincipalAmount());
    dto.setInterestAmount(pmt.getInterestAmount());
    dto.setEscrowAmount(pmt.getEscrowAmount());
    dto.setLateFee(pmt.getLateFee());
    dto.setType(pmt.getType());
    dto.setStatus(pmt.getStatus());
    return dto;
  }

  private static String isoDate(LocalDate date) {
    return date != null ? date.toString() : null;
  }
}
