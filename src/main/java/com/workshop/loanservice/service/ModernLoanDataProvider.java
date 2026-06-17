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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@link LoanDataProvider} backed by the modern normalized schema.
 *
 * Produces DTOs that are output-identical to {@link LegacyLoanDataProvider}: the
 * modern store keeps typed/canonical values, and this layer re-applies the legacy
 * presentation rules — dates formatted back to {@code MM/DD/YYYY}, canonical
 * status/type values mapped to their title-case display strings, and the property
 * type expanded to the legacy display string.
 *
 * Read methods run in a read-only modern transaction so lazy foreign-key
 * associations are initialized while building the DTOs.
 */
@Component
public class ModernLoanDataProvider implements LoanDataProvider {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);

    private final BorrowerRepository borrowerRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    public ModernLoanDataProvider(BorrowerRepository borrowerRepository,
                                  LoanAccountRepository loanAccountRepository,
                                  PaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public List<LoanSummaryDto> getAllLoans() {
        return loanAccountRepository.findAll().stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LoanAccount account = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return toLoanSummary(account);
    }

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
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

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // MODERN → DTO MAPPING (re-applies the legacy presentation rules)
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount account) {
        Borrower borrower = account.getBorrower();
        LoanProduct product = account.getProduct();

        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(account.getAccountNumber());
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(product != null && product.getName() != null
                ? product.getName()
                : account.getAccountNumber());
        dto.setOriginalAmount(account.getOriginalAmount());
        dto.setCurrentBalance(account.getCurrentBalance());
        dto.setInterestRate(account.getInterestRate());
        dto.setMonthlyPayment(account.getMonthlyPayment());
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
        return date == null ? null : date.format(DISPLAY_DATE);
    }

    /** Canonical loan status → legacy display string (ACTIVE → "Active"). */
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

    /** Canonical property type → legacy display string. */
    private String displayPropertyType(String type) {
        if (type == null) return "Unknown";
        return switch (type) {
            case "Single Family" -> "Single Family Residence";
            case "Multi-Family" -> "Multi-Family Residence";
            case "Condominium" -> "Condominium";
            case "Townhouse" -> "Townhouse";
            default -> type;
        };
    }

    /** Canonical payment type → legacy display string (REGULAR → "Regular"). */
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

    /** Canonical payment status → legacy display string (PST source → "Posted"). */
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
