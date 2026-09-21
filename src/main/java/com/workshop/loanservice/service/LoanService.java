package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer that reads from the modern normalized schema and renders the same
 * DTOs the legacy-backed implementation produced.
 *
 * <p>The modern schema stores UPPERCASE canonical codes and real DATE / DECIMAL types,
 * while the public API contract (see src/test/resources/golden) uses title-case display
 * strings, MM/dd/yyyy dates and the numeric scale of the legacy source. All of that
 * translation lives here so controllers and DTOs stay unchanged.
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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
        LoanAccount acct = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return toLoanSummary(acct);
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

        List<LoanSummaryDto> loans = loanAccountRepository.findByBorrowerExternalIdOrderByIdAsc(borrowerId)
                .stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDescIdAsc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // MODERN -> API TRANSLATION
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount acct) {
        Borrower borrower = acct.getBorrower();
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(acct.getProduct().getName());
        dto.setOriginalAmount(legacyAmount(acct.getOriginalAmount()));
        dto.setCurrentBalance(legacyAmount(acct.getCurrentBalance()));
        dto.setInterestRate(legacyDecimal(acct.getInterestRate()));
        dto.setMonthlyPayment(legacyAmount(acct.getMonthlyPayment()));
        dto.setStatus(displayLoanStatus(acct.getStatus()));
        dto.setOriginationDate(legacyDate(acct.getOriginationDate()));
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
        dto.setPaymentId(pmt.getLegacyPaymentId() != null
                ? pmt.getLegacyPaymentId() : String.valueOf(pmt.getId()));
        dto.setLoanAccountNumber(pmt.getLoanAccount().getAccountNumber());
        dto.setPaymentDate(legacyDate(pmt.getPaymentDate()));
        dto.setTotalAmount(legacyAmount(pmt.getTotalAmount()));
        dto.setPrincipalAmount(legacyAmount(pmt.getPrincipalAmount()));
        dto.setInterestAmount(legacyAmount(pmt.getInterestAmount()));
        dto.setEscrowAmount(legacyAmount(pmt.getEscrowAmount()));
        dto.setLateFee(legacyAmount(pmt.getLateFee()));
        dto.setType(displayPaymentType(pmt.getType()));
        dto.setStatus(displayPaymentStatus(pmt.getStatus()));
        return dto;
    }

    private static String legacyDate(LocalDate date) {
        return date == null ? null : LEGACY_DATE.format(date);
    }

    /**
     * Legacy amounts were free-text: whole amounts were stored without a fractional part
     * ("285,000"), everything else with two decimals ("1,487.02", "0.00"). DECIMAL(x,2)
     * columns always come back at scale 2, so whole non-zero amounts are rescaled to 0
     * to keep the JSON number tokens identical (285000 rather than 285000.00).
     */
    private static BigDecimal legacyAmount(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        if (amount.signum() != 0 && amount.stripTrailingZeros().scale() <= 0) {
            return amount.setScale(0);
        }
        return amount;
    }

    private static BigDecimal legacyDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String displayLoanStatus(String canonical) {
        if (canonical == null) return "Unknown";
        return switch (canonical) {
            case "ACTIVE" -> "Active";
            case "CLOSED" -> "Closed";
            case "DEFAULT" -> "Default";
            case "FORBEARANCE" -> "Forbearance";
            default -> canonical;
        };
    }

    private static String displayPropertyType(String canonical) {
        if (canonical == null) return "Unknown";
        return switch (canonical) {
            case "SINGLE_FAMILY" -> "Single Family Residence";
            case "CONDOMINIUM" -> "Condominium";
            case "MULTI_FAMILY" -> "Multi-Family Residence";
            case "TOWNHOUSE" -> "Townhouse";
            default -> canonical;
        };
    }

    private static String displayPaymentType(String canonical) {
        if (canonical == null) return "Unknown";
        return switch (canonical) {
            case "REGULAR" -> "Regular";
            case "EXTRA" -> "Extra";
            case "PARTIAL" -> "Partial";
            case "PREPAYMENT" -> "Prepayment";
            default -> canonical;
        };
    }

    private static String displayPaymentStatus(String canonical) {
        if (canonical == null) return "Unknown";
        return switch (canonical) {
            case "POSTED" -> "Posted";
            case "REVERSED" -> "Reversed";
            case "NSF" -> "Non-Sufficient Funds";
            case "PENDING" -> "Pending";
            default -> canonical;
        };
    }
}
