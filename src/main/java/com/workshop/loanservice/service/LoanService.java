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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer that reads from the modern normalized schema
 * and maps entities directly to DTOs.
 *
 * Rewired from legacy repositories (Task 3). All legacy string-parsing
 * and status-expansion helpers have been removed — the modern entities
 * already store proper Java types and expanded status values.
 *
 * Thin display formatters remain only to preserve the existing API
 * contract (date format, status casing, property-type suffix).
 */
@Service
public class LoanService {

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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
        return loanAccountRepository.findAll().stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LoanAccount acct = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return toLoanSummary(acct);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findByExternalId(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);

        List<LoanSummaryDto> loans = loanAccountRepository.findByBorrowerId(borrower.getId())
                .stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        LoanAccount acct = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElse(null);
        if (acct == null) {
            return Collections.emptyList();
        }
        return paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(acct.getId())
                .stream()
                .map(pmt -> toPaymentDto(pmt, loanAccountNumber))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // DTO MAPPING — reads directly from modern entities, no parsing needed.
    // Only thin display formatters for API contract compatibility.
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount acct) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        Borrower borrower = acct.getBorrower();
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(acct.getProduct() != null ? acct.getProduct().getName() : "Unknown");
        dto.setOriginalAmount(stripZeros(acct.getOriginalAmount()));
        dto.setCurrentBalance(stripZeros(acct.getCurrentBalance()));
        dto.setInterestRate(stripZeros(acct.getInterestRate()));
        dto.setMonthlyPayment(stripZeros(acct.getMonthlyPayment()));
        dto.setStatus(formatStatus(acct.getStatus()));
        dto.setOriginationDate(formatDate(acct.getOriginationDate()));
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(formatPropertyType(acct.getPropertyType()));
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

    private PaymentDto toPaymentDto(Payment pmt, String loanAccountNumber) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(pmt.getExternalId());
        dto.setLoanAccountNumber(loanAccountNumber);
        dto.setPaymentDate(formatDate(pmt.getPaymentDate()));
        dto.setTotalAmount(stripZeros(pmt.getTotalAmount()));
        dto.setPrincipalAmount(stripZeros(pmt.getPrincipalAmount()));
        dto.setInterestAmount(stripZeros(pmt.getInterestAmount()));
        dto.setEscrowAmount(stripZeros(pmt.getEscrowAmount()));
        dto.setLateFee(stripZeros(pmt.getLateFee()));
        dto.setType(formatPaymentType(pmt.getType()));
        dto.setStatus(formatPaymentStatus(pmt.getStatus()));
        return dto;
    }

    // =========================================================================
    // DISPLAY FORMATTERS — convert modern stored values to legacy API format.
    // These are thin mappers, not parsers — the data is already properly typed.
    // =========================================================================

    private BigDecimal stripZeros(BigDecimal value) {
        if (value == null) return null;
        BigDecimal stripped = value.stripTrailingZeros();
        // BigDecimal("0.00").stripTrailingZeros() yields scale <= 0,
        // which Jackson serializes as integer 0. Legacy API returned 0.0,
        // so preserve one decimal place for zero values.
        if (stripped.signum() == 0 && stripped.scale() <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return stripped;
    }

    private String formatDate(LocalDate date) {
        if (date == null) return null;
        return date.format(DISPLAY_DATE_FORMAT);
    }

    private String formatStatus(String status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case "ACTIVE" -> "Active";
            case "CLOSED" -> "Closed";
            case "DEFAULT" -> "Default";
            case "FORBEARANCE" -> "Forbearance";
            default -> status;
        };
    }

    private String formatPropertyType(String propertyType) {
        if (propertyType == null) return "Unknown";
        return switch (propertyType) {
            case "Single Family" -> "Single Family Residence";
            case "Multi-Family" -> "Multi-Family Residence";
            default -> propertyType;
        };
    }

    private String formatPaymentType(String type) {
        if (type == null) return "Unknown";
        return switch (type) {
            case "REGULAR" -> "Regular";
            case "EXTRA" -> "Extra";
            case "PARTIAL" -> "Partial";
            case "PREPAYMENT" -> "Prepayment";
            default -> type;
        };
    }

    private String formatPaymentStatus(String status) {
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
