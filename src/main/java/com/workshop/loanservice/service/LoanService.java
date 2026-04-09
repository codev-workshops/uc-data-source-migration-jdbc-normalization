package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.LoanProduct;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service layer that reads from the modern DynamoDB tables and maps
 * entities to DTOs for the API layer.
 *
 * <p>Replaces the legacy service that read from CDW tables and performed
 * manual string parsing. Since DynamoDB stores proper types and expanded
 * status values, the mapping logic is significantly simpler.
 *
 * <p>API contract (endpoints and response shapes) is fully backward compatible
 * with the legacy implementation.
 */
@Service
public class LoanService {

    private final BorrowerRepository borrowerRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final LoanProductRepository loanProductRepository;
    private final PaymentRepository paymentRepository;

    public LoanService(BorrowerRepository borrowerRepository,
                       LoanAccountRepository loanAccountRepository,
                       LoanProductRepository loanProductRepository,
                       PaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.loanProductRepository = loanProductRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<LoanSummaryDto> getAllLoans() {
        Map<String, LoanProduct> products = loanProductRepository.findAll()
                .stream()
                .collect(Collectors.toMap(LoanProduct::getProductCode, p -> p));

        Map<String, Borrower> borrowers = borrowerRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Borrower::getBorrowerId, b -> b));

        return loanAccountRepository.findAll().stream()
                .map(acct -> toLoanSummary(acct,
                        products.get(acct.getProductCode()),
                        borrowers.get(acct.getBorrowerId())))
                .collect(Collectors.toList());
    }

    public LoanSummaryDto getLoanById(String accountNumber) {
        LoanAccount acct = loanAccountRepository.findById(accountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + accountNumber));
        LoanProduct product = loanProductRepository.findById(acct.getProductCode())
                .orElse(null);
        Borrower borrower = borrowerRepository.findById(acct.getBorrowerId())
                .orElse(null);
        return toLoanSummary(acct, product, borrower);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);

        Map<String, LoanProduct> products = loanProductRepository.findAll()
                .stream()
                .collect(Collectors.toMap(LoanProduct::getProductCode, p -> p));

        List<LoanSummaryDto> loans = loanAccountRepository.findByBorrowerId(borrowerId)
                .stream()
                .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode()), borrower))
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountId) {
        return paymentRepository.findByLoanAccountIdOrderByDateDesc(loanAccountId)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // DTO MAPPING
    // Much simpler than the legacy version — no string parsing or code
    // expansion needed. DynamoDB stores proper types and expanded values.
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount acct, LoanProduct product, Borrower borrower) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());

        if (borrower != null) {
            dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        } else {
            dto.setBorrowerName("Unknown");
        }

        dto.setProductDescription(product != null ? product.getName() : acct.getProductCode());
        dto.setOriginalAmount(nullSafe(acct.getOriginalAmount()));
        dto.setCurrentBalance(nullSafe(acct.getCurrentBalance()));
        dto.setInterestRate(nullSafe(acct.getInterestRate()));
        dto.setMonthlyPayment(nullSafe(acct.getMonthlyPayment()));
        dto.setStatus(toTitleCase(acct.getStatus()));
        dto.setOriginationDate(acct.getOriginationDate());
        dto.setPropertyAddress(buildPropertyAddress(
                acct.getPropertyAddress(), acct.getPropertyCity(),
                acct.getPropertyState(), acct.getPropertyZip()));
        dto.setPropertyType(acct.getPropertyType());
        return dto;
    }

    private BorrowerDto toBorrowerDto(Borrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getBorrowerId());
        String middle = borrower.getMiddleInitial() != null
                ? " " + borrower.getMiddleInitial() + "." : "";
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
        dto.setPaymentId(pmt.getPaymentId());
        dto.setLoanAccountNumber(pmt.getLoanAccountId());
        dto.setPaymentDate(pmt.getPaymentDate());
        dto.setTotalAmount(nullSafe(pmt.getTotalAmount()));
        dto.setPrincipalAmount(nullSafe(pmt.getPrincipalAmount()));
        dto.setInterestAmount(nullSafe(pmt.getInterestAmount()));
        dto.setEscrowAmount(nullSafe(pmt.getEscrowAmount()));
        dto.setLateFee(nullSafe(pmt.getLateFee()));
        dto.setType(toTitleCase(pmt.getType()));
        dto.setStatus(formatPaymentStatus(pmt.getStatus()));
        return dto;
    }

    // =========================================================================
    // FORMATTING HELPERS
    // These ensure the API responses match the legacy format exactly.
    // DynamoDB stores UPPERCASE values; the legacy API returned Title Case.
    // =========================================================================

    /**
     * Convert an UPPERCASE status string to Title Case for backward compatibility.
     * e.g. "ACTIVE" -> "Active", "FORBEARANCE" -> "Forbearance"
     */
    private String toTitleCase(String value) {
        if (value == null || value.isEmpty()) {
            return "Unknown";
        }
        return value.substring(0, 1).toUpperCase()
                + value.substring(1).toLowerCase();
    }

    /**
     * Payment statuses need special handling for backward compatibility.
     * Legacy API returned "Non-Sufficient Funds" for NSF.
     */
    private String formatPaymentStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "Unknown";
        }
        return switch (status) {
            case "NSF" -> "Non-Sufficient Funds";
            case "POSTED" -> "Posted";
            case "REVERSED" -> "Reversed";
            case "PENDING" -> "Pending";
            default -> toTitleCase(status);
        };
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Build a formatted property address string, handling null fields gracefully.
     */
    private String buildPropertyAddress(String address, String city, String state, String zip) {
        StringBuilder sb = new StringBuilder();
        sb.append(address != null ? address : "");
        sb.append(", ");
        sb.append(city != null ? city : "");
        sb.append(", ");
        sb.append(state != null ? state : "");
        sb.append(" ");
        sb.append(zip != null ? zip : "");
        return sb.toString();
    }
}
