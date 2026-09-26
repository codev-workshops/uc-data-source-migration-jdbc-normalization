package com.workshop.loanservice.service;

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
import com.workshop.loanservice.routing.LoanServiceRegistry;
import com.workshop.loanservice.routing.ServiceImplementation;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads the normalized schema and produces the DTOs exposed by the REST layer. Callers address
 * loans and borrowers by their external identifiers (account number, borrower external id); the
 * BIGINT surrogate ids never leave the persistence layer. Status, type and property-type values are
 * already expanded in the database (by the V4 migration) and are passed through unchanged.
 *
 * <p>Registered as {@link ServiceImplementation#NORMALIZED}, the default per-request selection.
 */
@Service
public class NormalizedLoanService implements LoanQueryService {

  private static final Logger log = LoggerFactory.getLogger(NormalizedLoanService.class);

  private static final DateTimeFormatter DTO_DATE_FORMAT =
      DateTimeFormatter.ofPattern("MM/dd/yyyy");

  private final BorrowerRepository borrowerRepository;
  private final LoanAccountRepository loanAccountRepository;
  private final PaymentRepository paymentRepository;
  private final LoanServiceRegistry registry;

  public NormalizedLoanService(
      BorrowerRepository borrowerRepository,
      LoanAccountRepository loanAccountRepository,
      PaymentRepository paymentRepository,
      LoanServiceRegistry registry) {
    this.borrowerRepository = borrowerRepository;
    this.loanAccountRepository = loanAccountRepository;
    this.paymentRepository = paymentRepository;
    this.registry = registry;
  }

  @PostConstruct
  void register() {
    registry.register(ServiceImplementation.NORMALIZED, this);
  }

  @Override
  public List<LoanSummaryDto> getAllLoans() {
    log.info("fetching all loans (normalized)");
    return loanAccountRepository.findAll().stream()
        .map(this::toLoanSummary)
        .collect(Collectors.toList());
  }

  @Override
  public LoanSummaryDto getLoanById(String loanAccountNumber) {
    log.info("fetching loan id={} (normalized)", loanAccountNumber);
    LoanAccount account =
        loanAccountRepository
            .findByAccountNumber(loanAccountNumber)
            .orElseThrow(
                () -> {
                  log.warn("loan not found id={}", loanAccountNumber);
                  return new ResourceNotFoundException("Loan", loanAccountNumber);
                });
    return toLoanSummary(account);
  }

  @Override
  public List<BorrowerDto> getAllBorrowers() {
    log.info("fetching all borrowers (normalized)");
    return borrowerRepository.findAll().stream()
        .map(this::toBorrowerDto)
        .collect(Collectors.toList());
  }

  @Override
  public BorrowerDto getBorrowerById(String borrowerId) {
    log.info("fetching borrower id={} (normalized)", borrowerId);
    Borrower borrower =
        borrowerRepository
            .findByExternalId(borrowerId)
            .orElseThrow(
                () -> {
                  log.warn("borrower not found id={}", borrowerId);
                  return new ResourceNotFoundException("Borrower", borrowerId);
                });
    BorrowerDto dto = toBorrowerDto(borrower);
    dto.setLoans(
        loanAccountRepository.findByBorrowerExternalId(borrowerId).stream()
            .map(this::toLoanSummary)
            .collect(Collectors.toList()));
    return dto;
  }

  @Override
  public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
    log.info("fetching payments for loan id={} (normalized)", loanAccountNumber);
    return paymentRepository
        .findByLoanAccountAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
        .stream()
        .map(this::toPaymentDto)
        .collect(Collectors.toList());
  }

  private LoanSummaryDto toLoanSummary(LoanAccount account) {
    LoanProduct product = account.getProduct();
    Borrower borrower = account.getBorrower();

    LoanSummaryDto dto = new LoanSummaryDto();
    dto.setLoanAccountNumber(account.getAccountNumber());
    dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
    dto.setProductDescription(resolveProductDescription(product, account.getAccountNumber()));
    dto.setOriginalAmount(orZero(account.getOriginalAmount()));
    dto.setCurrentBalance(orZero(account.getCurrentBalance()));
    dto.setInterestRate(orZero(account.getInterestRate()));
    dto.setMonthlyPayment(orZero(account.getMonthlyPayment()));
    dto.setStatus(account.getStatus());
    dto.setOriginationDate(formatDate(account.getOriginationDate()));
    dto.setPropertyAddress(
        account.getPropertyAddress()
            + ", "
            + account.getPropertyCity()
            + ", "
            + account.getPropertyState()
            + " "
            + account.getPropertyZip());
    dto.setPropertyType(account.getPropertyType());
    return dto;
  }

  private String resolveProductDescription(LoanProduct product, String accountNumber) {
    if (product == null) {
      log.warn("product missing for loan id={}", accountNumber);
      return null;
    }
    if (product.getName() == null) {
      log.warn("product name missing code={}, falling back to code", product.getCode());
      return product.getCode();
    }
    return product.getName();
  }

  private BorrowerDto toBorrowerDto(Borrower borrower) {
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
    dto.setType(payment.getType());
    dto.setStatus(payment.getStatus());
    return dto;
  }

  private String formatDate(LocalDate date) {
    return date != null ? date.format(DTO_DATE_FORMAT) : null;
  }

  private BigDecimal orZero(BigDecimal amount) {
    return amount != null ? amount : BigDecimal.ZERO;
  }
}
