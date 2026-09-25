package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.FieldViolationDto;
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
import com.workshop.loanservice.service.validation.AmountTransformer;
import com.workshop.loanservice.service.validation.CodeExpansionTransformer;
import com.workshop.loanservice.service.validation.DecimalTransformer;
import com.workshop.loanservice.service.validation.FieldTransformer;
import com.workshop.loanservice.service.validation.IntegerTransformer;
import com.workshop.loanservice.service.validation.LegacyCodeSet;
import com.workshop.loanservice.service.validation.TransformResult;
import com.workshop.loanservice.service.validation.ValidationViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Service layer that reads from legacy tables and translates cryptic legacy fields into clean DTOs.
 *
 * <p>MIGRATION TASK: This service contains all the translation logic between legacy string-typed
 * fields and proper Java types. When switching data sources, this layer needs to be updated (or
 * replaced) to read from the modern schema.
 */
@Service
public class LoanService {

  private final LegacyBorrowerRepository borrowerRepository;
  private final LegacyLoanAccountRepository loanAccountRepository;
  private final LegacyLoanProductRepository loanProductRepository;
  private final LegacyPaymentRepository paymentRepository;

  public LoanService(
      LegacyBorrowerRepository borrowerRepository,
      LegacyLoanAccountRepository loanAccountRepository,
      LegacyLoanProductRepository loanProductRepository,
      LegacyPaymentRepository paymentRepository) {
    this.borrowerRepository = borrowerRepository;
    this.loanAccountRepository = loanAccountRepository;
    this.loanProductRepository = loanProductRepository;
    this.paymentRepository = paymentRepository;
  }

  public List<LoanSummaryDto> getAllLoans() {
    Map<String, LegacyLoanProduct> products =
        loanProductRepository.findAll().stream()
            .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));

    return loanAccountRepository.findAll().stream()
        .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
        .collect(Collectors.toList());
  }

  public LoanSummaryDto getLoanById(String loanAccountNumber) {
    LegacyLoanAccount acct =
        loanAccountRepository
            .findById(loanAccountNumber)
            .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
    LegacyLoanProduct product = loanProductRepository.findById(acct.getProductCode()).orElse(null);
    return toLoanSummary(acct, product);
  }

  public List<BorrowerDto> getAllBorrowers() {
    return borrowerRepository.findAll().stream()
        .map(this::toBorrowerDto)
        .collect(Collectors.toList());
  }

  public BorrowerDto getBorrowerById(String borrowerId) {
    LegacyBorrower borrower =
        borrowerRepository
            .findById(borrowerId)
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
    BorrowerDto dto = toBorrowerDto(borrower);

    // Attach loans for this borrower
    Map<String, LegacyLoanProduct> products =
        loanProductRepository.findAll().stream()
            .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));
    List<LoanSummaryDto> loans =
        loanAccountRepository.findByBorrowerId(borrowerId).stream()
            .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
            .collect(Collectors.toList());
    dto.setLoans(loans);

    return dto;
  }

  public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
    return paymentRepository
        .findByLoanAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
        .stream()
        .map(this::toPaymentDto)
        .collect(Collectors.toList());
  }

  // =========================================================================
  // LEGACY TRANSLATION METHODS
  // Every legacy string field is routed through a FieldTransformer. Invalid
  // input yields a null DTO field plus an entry in dataQualityViolations;
  // nothing is silently defaulted to zero or passed through as a raw code.
  // =========================================================================

  private static final AmountTransformer AMOUNT = new AmountTransformer();
  private static final DecimalTransformer DECIMAL = new DecimalTransformer();
  private static final IntegerTransformer CREDIT_SCORE = IntegerTransformer.creditScore();
  private static final CodeExpansionTransformer LOAN_STATUS =
      new CodeExpansionTransformer(LegacyCodeSet.LN_STAT_CD);
  private static final CodeExpansionTransformer PROPERTY_TYPE =
      new CodeExpansionTransformer(LegacyCodeSet.PROP_TYP_CD);
  private static final CodeExpansionTransformer PAYMENT_TYPE =
      new CodeExpansionTransformer(LegacyCodeSet.PMT_TYP_CD);
  private static final CodeExpansionTransformer PAYMENT_STATUS =
      new CodeExpansionTransformer(LegacyCodeSet.PMT_STAT_CD);

  private LoanSummaryDto toLoanSummary(LegacyLoanAccount acct, LegacyLoanProduct product) {
    List<ValidationViolation> violations = new ArrayList<>();
    LoanSummaryDto dto = new LoanSummaryDto();
    dto.setLoanAccountNumber(acct.getLoanAccountNumber());
    dto.setBorrowerName(acct.getBorrowerFirstName() + " " + acct.getBorrowerLastName());
    dto.setProductDescription(product != null ? product.getDescription() : acct.getProductCode());
    dto.setOriginalAmount(apply(AMOUNT, acct.getOriginalAmount(), "LN_ORIG_AMT", violations));
    dto.setCurrentBalance(apply(AMOUNT, acct.getCurrentBalance(), "LN_CURR_BAL", violations));
    dto.setInterestRate(apply(DECIMAL, acct.getInterestRate(), "LN_INT_RT", violations));
    dto.setMonthlyPayment(apply(AMOUNT, acct.getMonthlyPayment(), "LN_PMT_AMT", violations));
    dto.setStatus(apply(LOAN_STATUS, acct.getStatusCode(), "LN_STAT_CD", violations));
    dto.setOriginationDate(acct.getOriginationDate());
    dto.setPropertyAddress(
        acct.getPropertyAddress()
            + ", "
            + acct.getPropertyCity()
            + ", "
            + acct.getPropertyState()
            + " "
            + acct.getPropertyZip());
    dto.setPropertyType(apply(PROPERTY_TYPE, acct.getPropertyType(), "PROP_TYP_CD", violations));
    dto.setDataQualityViolations(toDtos(violations));
    return dto;
  }

  private BorrowerDto toBorrowerDto(LegacyBorrower borrower) {
    List<ValidationViolation> violations = new ArrayList<>();
    BorrowerDto dto = new BorrowerDto();
    dto.setId(borrower.getBorrowerId());
    String middle =
        borrower.getMiddleInitial() != null ? " " + borrower.getMiddleInitial() + "." : "";
    dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
    dto.setEmail(borrower.getEmail());
    dto.setPhone(borrower.getPhoneNumber());
    dto.setCity(borrower.getCity());
    dto.setState(borrower.getStateCode());
    dto.setCreditScore(apply(CREDIT_SCORE, borrower.getCreditScore(), "BORR_CRDT_SCR", violations));
    dto.setEmploymentStatus(borrower.getEmploymentStatus());
    dto.setLoans(new ArrayList<>());
    dto.setDataQualityViolations(toDtos(violations));
    return dto;
  }

  private PaymentDto toPaymentDto(LegacyPayment pmt) {
    List<ValidationViolation> violations = new ArrayList<>();
    PaymentDto dto = new PaymentDto();
    dto.setPaymentId(pmt.getPaymentSequenceNumber());
    dto.setLoanAccountNumber(pmt.getLoanAccountNumber());
    dto.setPaymentDate(pmt.getPaymentDate());
    dto.setTotalAmount(apply(AMOUNT, pmt.getTotalAmount(), "PMT_AMT", violations));
    dto.setPrincipalAmount(apply(AMOUNT, pmt.getPrincipalAmount(), "PMT_PRIN_AMT", violations));
    dto.setInterestAmount(apply(AMOUNT, pmt.getInterestAmount(), "PMT_INT_AMT", violations));
    dto.setEscrowAmount(apply(AMOUNT, pmt.getEscrowAmount(), "PMT_ESCROW_AMT", violations));
    dto.setLateFee(apply(AMOUNT, pmt.getLateFee(), "PMT_LATE_FEE", violations));
    dto.setType(apply(PAYMENT_TYPE, pmt.getTypeCode(), "PMT_TYP_CD", violations));
    dto.setStatus(apply(PAYMENT_STATUS, pmt.getStatusCode(), "PMT_STAT_CD", violations));
    dto.setDataQualityViolations(toDtos(violations));
    return dto;
  }

  private static <T> T apply(
      FieldTransformer<T> transformer, String raw, String field, List<ValidationViolation> sink) {
    TransformResult<T> result = transformer.transform(raw, field);
    sink.addAll(result.violations());
    return result.value();
  }

  private static List<FieldViolationDto> toDtos(List<ValidationViolation> violations) {
    return violations.stream().map(FieldViolationDto::from).collect(Collectors.toList());
  }
}
