package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.ModernLoanAccountRepository;
import com.workshop.loanservice.repository.ModernLoanProductRepository;
import com.workshop.loanservice.repository.ModernPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Modern service that reads from normalized modern repositories using JPA relationships.
 * No legacy string parsing needed — all amounts and numbers are already proper types.
 * No manual joins — all cross-table lookups use JPA @ManyToOne relationships.
 *
 * Display formatting methods convert normalized DB values (e.g., "ACTIVE") to
 * display-friendly strings (e.g., "Active") to match the legacy API contract.
 *
 * Note: PaymentDto.paymentId uses auto-generated Long ID instead of legacy sequence
 * number (e.g., "1" instead of "PMT-2025120001"). This is an accepted API difference.
 */
@Service
public class ModernLoanService {

    private static final Logger logger = LoggerFactory.getLogger(ModernLoanService.class);
    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final BorrowerRepository borrowerRepository;
    private final ModernLoanProductRepository modernLoanProductRepository;
    private final ModernLoanAccountRepository modernLoanAccountRepository;
    private final ModernPaymentRepository modernPaymentRepository;

    public ModernLoanService(BorrowerRepository borrowerRepository,
                             ModernLoanProductRepository modernLoanProductRepository,
                             ModernLoanAccountRepository modernLoanAccountRepository,
                             ModernPaymentRepository modernPaymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.modernLoanProductRepository = modernLoanProductRepository;
        this.modernLoanAccountRepository = modernLoanAccountRepository;
        this.modernPaymentRepository = modernPaymentRepository;
    }

    // =========================================================================
    // PUBLIC METHODS — same 5 methods as LoanService, identical API contract
    // =========================================================================

    public List<LoanSummaryDto> getAllLoans() {
        return modernLoanAccountRepository.findAll().stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LoanAccount account = modernLoanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return toLoanSummary(account);
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

        List<LoanSummaryDto> loans = modernLoanAccountRepository.findByBorrowerId(borrower.getId())
                .stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        LoanAccount account = modernLoanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return modernPaymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(account.getId())
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // PRIVATE MAPPING METHODS
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount account) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(account.getAccountNumber());
        dto.setBorrowerName(account.getBorrower().getFirstName() + " " + account.getBorrower().getLastName());
        dto.setProductDescription(account.getProduct() != null ? account.getProduct().getName() : account.getAccountNumber());
        dto.setOriginalAmount(account.getOriginalAmount());
        dto.setCurrentBalance(account.getCurrentBalance());
        dto.setInterestRate(account.getInterestRate());
        dto.setMonthlyPayment(account.getMonthlyPayment());
        dto.setStatus(formatLoanStatus(account.getStatus()));
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
        dto.setPaymentId(String.valueOf(payment.getId()));
        dto.setLoanAccountNumber(payment.getLoanAccount().getAccountNumber());
        dto.setPaymentDate(formatDate(payment.getPaymentDate()));
        dto.setTotalAmount(payment.getTotalAmount());
        dto.setPrincipalAmount(payment.getPrincipalAmount());
        dto.setInterestAmount(payment.getInterestAmount());
        dto.setEscrowAmount(payment.getEscrowAmount());
        dto.setLateFee(payment.getLateFee());
        dto.setType(formatPaymentType(payment.getType()));
        dto.setStatus(formatPaymentStatus(payment.getStatus()));
        return dto;
    }

    // =========================================================================
    // DISPLAY FORMATTING HELPERS
    // Convert normalized DB values to display-friendly strings matching legacy API
    // =========================================================================

    private String formatLoanStatus(String status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case "ACTIVE" -> "Active";
            case "CLOSED" -> "Closed";
            case "DEFAULT" -> "Default";
            case "FORBEARANCE" -> "Forbearance";
            default -> status;
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

    private String formatDate(LocalDate date) {
        if (date == null) return null;
        return date.format(LEGACY_DATE_FORMAT);
    }
}
