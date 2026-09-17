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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer that reads the modern normalized tables and maps them to the
 * API DTOs.
 *
 * <p>The DTO contract predates the migration: dates are exposed as
 * {@code MM/DD/YYYY} strings, ids are the legacy natural keys, and enum-like
 * column values are expanded into display strings.
 */
@Service
public class LoanService {

    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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

    @Transactional(readOnly = true)
    public List<LoanSummaryDto> getAllLoans() {
        return loanAccountRepository.findAll().stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LoanAccount account = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return toLoanSummary(account);
    }

    @Transactional(readOnly = true)
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findByExternalId(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);

        List<LoanSummaryDto> loans = loanAccountRepository.findByBorrower_ExternalId(borrowerId)
                .stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // DTO MAPPING
    // The modern entities are strongly typed; the API contract still uses
    // formatted date strings, concatenated names and expanded display codes.
    // =========================================================================

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
        dto.setStatus(displayLoanStatus(account.getStatus()));
        dto.setOriginationDate(formatDate(account.getOriginationDate()));
        dto.setPropertyAddress(account.getPropertyAddress() + ", " + account.getPropertyCity()
                + ", " + account.getPropertyState() + " " + account.getPropertyZip());
        dto.setPropertyType(displayPropertyType(account.getPropertyType()));
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
        dto.setPaymentId(payment.getLegacyId());
        dto.setLoanAccountNumber(payment.getLoanAccount().getAccountNumber());
        dto.setPaymentDate(formatDate(payment.getPaymentDate()));
        dto.setTotalAmount(orZero(payment.getTotalAmount()));
        dto.setPrincipalAmount(orZero(payment.getPrincipalAmount()));
        dto.setInterestAmount(orZero(payment.getInterestAmount()));
        dto.setEscrowAmount(orZero(payment.getEscrowAmount()));
        dto.setLateFee(orZero(payment.getLateFee()));
        dto.setType(displayPaymentType(payment.getType()));
        dto.setStatus(displayPaymentStatus(payment.getStatus()));
        return dto;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(API_DATE) : null;
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
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

    private String displayPropertyType(String propertyType) {
        if (propertyType == null) return "Unknown";
        return switch (propertyType) {
            case "SINGLE_FAMILY" -> "Single Family Residence";
            case "CONDOMINIUM" -> "Condominium";
            case "MULTI_FAMILY" -> "Multi-Family Residence";
            case "TOWNHOUSE" -> "Townhouse";
            default -> propertyType;
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
