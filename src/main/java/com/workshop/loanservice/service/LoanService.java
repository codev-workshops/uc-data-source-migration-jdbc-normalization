package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
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
 * Service layer backed by the modern, normalized schema.
 *
 * <p>The entities are already properly typed, so no string parsing happens here.
 * What remains is presentation logic that keeps the public API byte-identical to the
 * legacy responses: dates are rendered back as {@code MM/DD/YYYY} strings and the
 * uppercase stored codes are mapped to the title-case display strings the legacy
 * service produced.
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

        // Attach loans for this borrower
        List<LoanSummaryDto> loans = loanAccountRepository.findByBorrower_ExternalId(borrowerId)
                .stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // DTO MAPPING
    // Modern entities are typed; only date formatting and code-to-display
    // mapping remain so that the API contract is unchanged.
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount acct) {
        LoanProduct product = acct.getProduct();
        Borrower borrower = acct.getBorrower();
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        dto.setBorrowerName(borrower != null
                ? borrower.getFirstName() + " " + borrower.getLastName()
                : null);
        dto.setProductDescription(product != null ? product.getName() : null);
        dto.setOriginalAmount(principalScale(acct.getOriginalAmount()));
        dto.setCurrentBalance(acct.getCurrentBalance());
        dto.setInterestRate(acct.getInterestRate());
        dto.setMonthlyPayment(acct.getMonthlyPayment());
        dto.setStatus(displayLoanStatus(acct.getStatus()));
        dto.setOriginationDate(formatDate(acct.getOriginationDate()));
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(displayPropertyType(acct.getPropertyType()));
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
        dto.setLoanAccountNumber(pmt.getLoanAccount() != null
                ? pmt.getLoanAccount().getAccountNumber()
                : null);
        dto.setPaymentDate(formatDate(pmt.getPaymentDate()));
        dto.setTotalAmount(pmt.getTotalAmount());
        dto.setPrincipalAmount(pmt.getPrincipalAmount());
        dto.setInterestAmount(pmt.getInterestAmount());
        dto.setEscrowAmount(pmt.getEscrowAmount());
        dto.setLateFee(pmt.getLateFee());
        dto.setType(displayPaymentType(pmt.getType()));
        dto.setStatus(displayPaymentStatus(pmt.getStatus()));
        return dto;
    }

    /** Legacy stored principals without cents ("285,000"); keep whole amounts unscaled. */
    private BigDecimal principalScale(BigDecimal amount) {
        if (amount == null) return null;
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.setScale(0) : amount;
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : date.format(API_DATE);
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
            case "SINGLE_FAMILY_RESIDENCE" -> "Single Family Residence";
            case "CONDOMINIUM" -> "Condominium";
            case "MULTI_FAMILY_RESIDENCE" -> "Multi-Family Residence";
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
