package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service layer backed by the modern normalized schema.
 *
 * <p>Entities already carry proper types, so the only translation left is
 * rendering dates and status codes in the formats the public API contract uses
 * (see DATA_SOURCE_MIGRATION_NOTES.md).</p>
 */
@Service
public class LoanService {

    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("MM/dd/uuuu");

    private static final Map<String, String> LOAN_STATUS_LABELS = Map.of(
            "ACTIVE", "Active",
            "CLOSED", "Closed",
            "DEFAULT", "Default",
            "FORBEARANCE", "Forbearance");

    private static final Map<String, String> PAYMENT_TYPE_LABELS = Map.of(
            "REGULAR", "Regular",
            "EXTRA", "Extra",
            "PARTIAL", "Partial",
            "PREPAYMENT", "Prepayment");

    private static final Map<String, String> PAYMENT_STATUS_LABELS = Map.of(
            "POSTED", "Posted",
            "REVERSED", "Reversed",
            "NSF", "Non-Sufficient Funds",
            "PENDING", "Pending");

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
        return loanAccountRepository.findAllByOrderByIdAsc().stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LoanAccount account = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return toLoanSummary(account);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAllByOrderByIdAsc().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findByExternalId(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);
        dto.setLoans(loanAccountRepository.findByBorrowerOrderByIdAsc(borrower).stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList()));
        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDesc(loanAccountNumber).stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    private LoanSummaryDto toLoanSummary(LoanAccount account) {
        LoanProduct product = account.getProduct();
        Borrower borrower = account.getBorrower();

        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(account.getAccountNumber());
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(product != null && product.getName() != null
                ? product.getName()
                : (product != null ? product.getCode() : null));
        dto.setOriginalAmount(account.getOriginalAmount());
        dto.setCurrentBalance(account.getCurrentBalance());
        dto.setInterestRate(account.getInterestRate());
        dto.setMonthlyPayment(account.getMonthlyPayment());
        dto.setStatus(label(LOAN_STATUS_LABELS, account.getStatus()));
        dto.setOriginationDate(formatDate(account.getOriginationDate()));
        dto.setPropertyAddress(account.getPropertyAddress() + ", " + account.getPropertyCity()
                + ", " + account.getPropertyState() + " " + account.getPropertyZip());
        dto.setPropertyType(account.getPropertyType() != null ? account.getPropertyType() : "Unknown");
        return dto;
    }

    private BorrowerDto toBorrowerDto(Borrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getExternalId());
        String middle = borrower.getMiddleInitial() != null ? " " + borrower.getMiddleInitial() + "." : "";
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
        dto.setTotalAmount(payment.getTotalAmount());
        dto.setPrincipalAmount(payment.getPrincipalAmount());
        dto.setInterestAmount(payment.getInterestAmount());
        dto.setEscrowAmount(payment.getEscrowAmount());
        dto.setLateFee(payment.getLateFee());
        dto.setType(label(PAYMENT_TYPE_LABELS, payment.getType()));
        dto.setStatus(label(PAYMENT_STATUS_LABELS, payment.getStatus()));
        return dto;
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : date.format(API_DATE);
    }

    private String label(Map<String, String> labels, String value) {
        if (value == null) {
            return "Unknown";
        }
        return labels.getOrDefault(value, value);
    }
}
