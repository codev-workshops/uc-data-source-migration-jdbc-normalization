package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.normalized.Borrower;
import com.workshop.loanservice.entity.normalized.LoanAccount;
import com.workshop.loanservice.entity.normalized.LoanProduct;
import com.workshop.loanservice.entity.normalized.Payment;
import com.workshop.loanservice.repository.normalized.BorrowerRepository;
import com.workshop.loanservice.repository.normalized.LoanAccountRepository;
import com.workshop.loanservice.repository.normalized.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Reads the normalized schema and produces the same DTOs as the legacy path: typed columns replace
 * the legacy string parsing, while code expansions and the formatting of names, addresses and dates
 * are unchanged.
 *
 * <p>Active when {@code application.data-mode} is {@code normalized}.
 */
@Service
@ConditionalOnProperty(name = "application.data-mode", havingValue = "normalized")
public class NormalizedLoanService implements LoanQueryService {

  private static final Logger log = LoggerFactory.getLogger(NormalizedLoanService.class);

  private static final DateTimeFormatter LEGACY_DATE_FORMAT =
      DateTimeFormatter.ofPattern("MM/dd/yyyy");

  private final BorrowerRepository borrowerRepository;
  private final LoanAccountRepository loanAccountRepository;
  private final PaymentRepository paymentRepository;

  public NormalizedLoanService(
      BorrowerRepository borrowerRepository,
      LoanAccountRepository loanAccountRepository,
      PaymentRepository paymentRepository) {
    this.borrowerRepository = borrowerRepository;
    this.loanAccountRepository = loanAccountRepository;
    this.paymentRepository = paymentRepository;
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
            .findById(loanAccountNumber)
            .orElseThrow(
                () -> {
                  log.warn("loan not found id={}", loanAccountNumber);
                  return new RuntimeException("Loan not found: " + loanAccountNumber);
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
            .findById(borrowerId)
            .orElseThrow(
                () -> {
                  log.warn("borrower not found id={}", borrowerId);
                  return new RuntimeException("Borrower not found: " + borrowerId);
                });
    BorrowerDto dto = toBorrowerDto(borrower);
    dto.setLoans(
        loanAccountRepository.findByBorrowerBorrowerId(borrowerId).stream()
            .map(this::toLoanSummary)
            .collect(Collectors.toList()));
    return dto;
  }

  @Override
  public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
    log.info("fetching payments for loan id={} (normalized)", loanAccountNumber);
    return paymentRepository
        .findByLoanAccountLoanAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
        .stream()
        .map(this::toPaymentDto)
        .collect(Collectors.toList());
  }

  private LoanSummaryDto toLoanSummary(LoanAccount account) {
    LoanProduct product = account.getLoanProduct();
    Borrower borrower = account.getBorrower();
    if (product == null) {
      log.warn(
          "product not found code={} for loan id={}, falling back to raw code",
          account.getProductCode(),
          account.getLoanAccountNumber());
    }

    LoanSummaryDto dto = new LoanSummaryDto();
    dto.setLoanAccountNumber(account.getLoanAccountNumber());
    dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
    dto.setProductDescription(
        product != null ? product.getDescription() : account.getProductCode());
    dto.setOriginalAmount(orZero(account.getOriginalAmount()));
    dto.setCurrentBalance(orZero(account.getCurrentBalance()));
    dto.setInterestRate(orZero(account.getInterestRate()));
    dto.setMonthlyPayment(orZero(account.getMonthlyPayment()));
    dto.setStatus(expandStatusCode(account.getStatusCode()));
    dto.setOriginationDate(formatDate(account.getOriginationDate()));
    dto.setPropertyAddress(
        account.getPropertyAddressLine1()
            + ", "
            + account.getPropertyCity()
            + ", "
            + account.getPropertyState()
            + " "
            + account.getPropertyZip());
    dto.setPropertyType(expandPropertyType(account.getPropertyType()));
    return dto;
  }

  private BorrowerDto toBorrowerDto(Borrower borrower) {
    BorrowerDto dto = new BorrowerDto();
    dto.setId(borrower.getBorrowerId());
    String middle =
        borrower.getMiddleInitial() != null ? " " + borrower.getMiddleInitial() + "." : "";
    dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
    dto.setEmail(borrower.getEmail());
    dto.setPhone(borrower.getPhoneNumber());
    dto.setCity(borrower.getCity());
    dto.setState(borrower.getStateCode());
    dto.setCreditScore(borrower.getCreditScore());
    dto.setEmploymentStatus(borrower.getEmploymentStatus());
    return dto;
  }

  private PaymentDto toPaymentDto(Payment payment) {
    PaymentDto dto = new PaymentDto();
    dto.setPaymentId(payment.getPaymentId());
    dto.setLoanAccountNumber(payment.getLoanAccount().getLoanAccountNumber());
    dto.setPaymentDate(formatDate(payment.getPaymentDate()));
    dto.setTotalAmount(orZero(payment.getTotalAmount()));
    dto.setPrincipalAmount(orZero(payment.getPrincipalAmount()));
    dto.setInterestAmount(orZero(payment.getInterestAmount()));
    dto.setEscrowAmount(orZero(payment.getEscrowAmount()));
    dto.setLateFee(orZero(payment.getLateFee()));
    dto.setType(expandPaymentType(payment.getTypeCode()));
    dto.setStatus(expandPaymentStatus(payment.getStatusCode()));
    return dto;
  }

  /** Renders dates the way the legacy path returned them verbatim from its string columns. */
  private String formatDate(LocalDate date) {
    return date != null ? date.format(LEGACY_DATE_FORMAT) : null;
  }

  private BigDecimal orZero(BigDecimal amount) {
    return amount != null ? amount : BigDecimal.ZERO;
  }

  private String expandStatusCode(String code) {
    if (code == null) {
      return "Unknown";
    }
    return switch (code) {
      case "ACT" -> "Active";
      case "CLO" -> "Closed";
      case "DFT" -> "Default";
      case "FRB" -> "Forbearance";
      default -> code;
    };
  }

  private String expandPropertyType(String code) {
    if (code == null) {
      return "Unknown";
    }
    return switch (code) {
      case "SFR" -> "Single Family Residence";
      case "CND" -> "Condominium";
      case "MFR" -> "Multi-Family Residence";
      case "TWN" -> "Townhouse";
      default -> code;
    };
  }

  private String expandPaymentType(String code) {
    if (code == null) {
      return "Unknown";
    }
    return switch (code) {
      case "REG" -> "Regular";
      case "EXT" -> "Extra";
      case "PRT" -> "Partial";
      case "PRE" -> "Prepayment";
      default -> code;
    };
  }

  private String expandPaymentStatus(String code) {
    if (code == null) {
      return "Unknown";
    }
    return switch (code) {
      case "PST" -> "Posted";
      case "REV" -> "Reversed";
      case "NSF" -> "Non-Sufficient Funds";
      case "PND" -> "Pending";
      default -> code;
    };
  }
}
