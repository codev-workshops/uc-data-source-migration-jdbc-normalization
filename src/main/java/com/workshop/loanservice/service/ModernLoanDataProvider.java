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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads from the modern normalized tables. Because the modern schema is properly
 * typed, there is no string-to-type parsing here (contrast
 * {@link LegacyLoanDataProvider}); the only work is display formatting so the
 * REST API contract stays byte-for-byte identical to the legacy output:
 * {@link LocalDate} → {@code MM/DD/YYYY}, enum-like codes → title case, and the
 * denormalized name/address strings the DTOs expose.
 */
@Component
@Transactional(readOnly = true, transactionManager = "modernTransactionManager")
public class ModernLoanDataProvider implements LoanDataProvider {

    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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
    public DataSourceSelector.DataSource dataSource() {
        return DataSourceSelector.DataSource.MODERN;
    }

    @Override
    public List<LoanSummaryDto> getAllLoans() {
        return loanAccountRepository.findAll().stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    @Override
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LoanAccount acct = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return toLoanSummary(acct);
    }

    @Override
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    @Override
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
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // MODERN TRANSLATION (display formatting only — no type parsing)
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount acct) {
        Borrower borrower = acct.getBorrower();
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(acct.getProduct().getName());
        dto.setOriginalAmount(acct.getOriginalAmount());
        dto.setCurrentBalance(acct.getCurrentBalance());
        dto.setInterestRate(acct.getInterestRate());
        dto.setMonthlyPayment(acct.getMonthlyPayment());
        dto.setStatus(displayLoanStatus(acct.getStatus()));
        dto.setOriginationDate(formatDate(acct.getOriginationDate()));
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(acct.getPropertyType());
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
        dto.setPaymentId(pmt.getLegacySequenceNumber());
        dto.setLoanAccountNumber(pmt.getLoanAccount().getAccountNumber());
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

    private static String formatDate(LocalDate date) {
        return date == null ? null : date.format(API_DATE);
    }

    private static String displayLoanStatus(String status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case "ACTIVE" -> "Active";
            case "CLOSED" -> "Closed";
            case "DEFAULT" -> "Default";
            case "FORBEARANCE" -> "Forbearance";
            default -> status;
        };
    }

    private static String displayPaymentType(String type) {
        if (type == null) return "Unknown";
        return switch (type) {
            case "REGULAR" -> "Regular";
            case "EXTRA" -> "Extra";
            case "PARTIAL" -> "Partial";
            case "PREPAYMENT" -> "Prepayment";
            default -> type;
        };
    }

    private static String displayPaymentStatus(String status) {
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
