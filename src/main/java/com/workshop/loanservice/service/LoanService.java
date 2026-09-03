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

/**
 * Service layer that reads the normalized schema and maps entities onto the
 * public DTOs. The DTO shapes and values are frozen by the golden-master API
 * tests, so dates, amounts and status labels are rendered in the exact form the
 * legacy data source produced.
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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
                .toList();
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LoanAccount acct = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return toLoanSummary(acct);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAllByOrderByIdAsc().stream()
                .map(this::toBorrowerDto)
                .toList();
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findByExternalId(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);
        dto.setLoans(loanAccountRepository.findByBorrower_ExternalIdOrderByIdAsc(borrowerId).stream()
                .map(this::toLoanSummary)
                .toList());
        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc(loanAccountNumber).stream()
                .map(this::toPaymentDto)
                .toList();
    }

    // =========================================================================
    // ENTITY -> DTO MAPPING
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount acct) {
        Borrower borrower = acct.getBorrower();
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(acct.getProduct().getName());
        dto.setOriginalAmount(wholeDollarsWhenNoCents(acct.getOriginalAmount()));
        dto.setCurrentBalance(acct.getCurrentBalance());
        dto.setInterestRate(acct.getInterestRate());
        dto.setMonthlyPayment(acct.getMonthlyPayment());
        dto.setStatus(loanStatusLabel(acct.getStatus()));
        dto.setOriginationDate(formatApiDate(acct.getOriginationDate()));
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(propertyTypeLabel(acct.getPropertyType()));
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

    private PaymentDto toPaymentDto(Payment pmt) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(pmt.getExternalId());
        dto.setLoanAccountNumber(pmt.getLoanAccount().getAccountNumber());
        dto.setPaymentDate(formatApiDate(pmt.getPaymentDate()));
        dto.setTotalAmount(pmt.getTotalAmount());
        dto.setPrincipalAmount(pmt.getPrincipalAmount());
        dto.setInterestAmount(pmt.getInterestAmount());
        dto.setEscrowAmount(pmt.getEscrowAmount());
        dto.setLateFee(pmt.getLateFee());
        dto.setType(paymentTypeLabel(pmt.getType()));
        dto.setStatus(paymentStatusLabel(pmt.getStatus()));
        return dto;
    }

    // =========================================================================
    // API PRESENTATION RULES
    // The public API keeps the legacy presentation: dates as MM/dd/yyyy strings,
    // original amounts in whole dollars when there are no cents, and
    // human-readable status labels.
    // =========================================================================

    private static String formatApiDate(LocalDate date) {
        return date != null ? date.format(API_DATE_FORMAT) : null;
    }

    /**
     * Original loan amounts are presented without a fractional part when the
     * amount is a whole number of dollars (285000 rather than 285000.00).
     */
    private static BigDecimal wholeDollarsWhenNoCents(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.setScale(0) : amount;
    }

    private static String loanStatusLabel(String status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case "ACTIVE" -> "Active";
            case "CLOSED" -> "Closed";
            case "DEFAULT" -> "Default";
            case "FORBEARANCE" -> "Forbearance";
            default -> status;
        };
    }

    private static String propertyTypeLabel(String propertyType) {
        if (propertyType == null) return "Unknown";
        return switch (propertyType) {
            case "SINGLE_FAMILY" -> "Single Family Residence";
            case "CONDOMINIUM" -> "Condominium";
            case "MULTI_FAMILY" -> "Multi-Family Residence";
            case "TOWNHOUSE" -> "Townhouse";
            default -> propertyType;
        };
    }

    private static String paymentTypeLabel(String type) {
        if (type == null) return "Unknown";
        return switch (type) {
            case "REGULAR" -> "Regular";
            case "EXTRA" -> "Extra";
            case "PARTIAL" -> "Partial";
            case "PREPAYMENT" -> "Prepayment";
            default -> type;
        };
    }

    private static String paymentStatusLabel(String status) {
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
