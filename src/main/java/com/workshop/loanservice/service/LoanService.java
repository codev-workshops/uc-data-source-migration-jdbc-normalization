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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer reading from the modern normalized schema.
 * Entities already carry proper types, so the only remaining translation is
 * presentation formatting (display dates and human readable status labels)
 * required to keep the REST contract unchanged.
 */
@Service
public class LoanService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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
        LoanAccount account = loanAccountRepository.findByAccountNumber(loanAccountNumber)
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
        dto.setLoans(loanAccountRepository.findByBorrowerExternalId(borrowerId).stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList()));
        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository
                .findByLoanAccountAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    private LoanSummaryDto toLoanSummary(LoanAccount account) {
        Borrower borrower = account.getBorrower();
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(account.getAccountNumber());
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(account.getProduct().getName());
        dto.setOriginalAmount(account.getOriginalAmount());
        dto.setCurrentBalance(account.getCurrentBalance());
        dto.setInterestRate(account.getInterestRate());
        dto.setMonthlyPayment(account.getMonthlyPayment());
        dto.setStatus(toDisplayLabel(account.getStatus()));
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
        dto.setType(toDisplayLabel(payment.getType()));
        dto.setStatus(toDisplayLabel(payment.getStatus()));
        return dto;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DISPLAY_DATE) : null;
    }

    /**
     * Turns a stored enum-like value such as {@code FORBEARANCE} into the
     * display label {@code Forbearance} the API has always returned.
     */
    private String toDisplayLabel(String value) {
        if (value == null) return "Unknown";
        if ("NSF".equals(value)) return "Non-Sufficient Funds";
        return value.charAt(0) + value.substring(1).toLowerCase();
    }
}
