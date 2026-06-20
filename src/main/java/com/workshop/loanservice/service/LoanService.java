package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer that reads from the modern, normalized schema and maps it to the
 * existing DTOs.
 *
 * The migration moved all type parsing into the data layer (proper LocalDate /
 * BigDecimal / Integer columns). What remains here is purely presentation: the
 * API contract still exposes dates as MM/DD/YYYY strings, human-readable status
 * values, and a composite property address / borrower name. Those formatting
 * rules are preserved exactly so the public contract is unchanged.
 */
@Service
public class LoanService {

    /** uuuu == proleptic year; renders identically to the legacy MM/DD/YYYY strings. */
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("MM/dd/uuuu");

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
        List<LoanSummaryDto> loans = loanAccountRepository
                .findByBorrower_ExternalIdOrderByIdAsc(borrowerId).stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
        dto.setLoans(loans);
        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository
                .findByLoanAccount_AccountNumberOrderByPaymentDateDesc(loanAccountNumber).stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // DTO MAPPING (presentation only; the modern schema already holds real types)
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount account) {
        Borrower borrower = account.getBorrower();
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(account.getAccountNumber());
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(account.getProduct().getName());
        dto.setOriginalAmount(wholeDollar(account.getOriginalAmount()));
        dto.setCurrentBalance(account.getCurrentBalance());
        dto.setInterestRate(account.getInterestRate());
        dto.setMonthlyPayment(account.getMonthlyPayment());
        dto.setStatus(displayLoanStatus(account.getStatus()));
        dto.setOriginationDate(formatDate(account.getOriginationDate()));
        dto.setPropertyAddress(account.getPropertyAddress() + ", " + account.getPropertyCity()
                + ", " + account.getPropertyState() + " " + account.getPropertyZip());
        dto.setPropertyType(account.getPropertyType());
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
        dto.setType(displayPaymentType(payment.getType()));
        dto.setStatus(displayPaymentStatus(payment.getStatus()));
        return dto;
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : date.format(API_DATE);
    }

    /**
     * The contract renders whole-dollar original amounts without decimals
     * (e.g. 285000, not 285000.00). All source original amounts are whole; this
     * strips the scale a DECIMAL(12,2) column adds back while leaving genuinely
     * fractional values untouched.
     */
    private BigDecimal wholeDollar(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private String displayLoanStatus(String status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case "ACTIVE" -> "Active";
            case "CLOSED" -> "Closed";
            case "DEFAULT" -> "Default";
            case "FORBEARANCE" -> "Forbearance";
            default -> status;
        };
    }

    private String displayPaymentType(String type) {
        if (type == null) return "Unknown";
        return switch (type) {
            case "REGULAR" -> "Regular";
            case "EXTRA" -> "Extra";
            case "PARTIAL" -> "Partial";
            case "PREPAYMENT" -> "Prepayment";
            default -> type;
        };
    }

    private String displayPaymentStatus(String status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case "POSTED" -> "Posted";
            case "REVERSED" -> "Reversed";
            case "NSF" -> "Non-Sufficient Funds";
            case "PENDING" -> "Pending";
            default -> status;
        };
    }
}
