package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LoanService {

    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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

        List<LoanSummaryDto> loans = loanAccountRepository.findByBorrowerId(borrower.getId())
                .stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        LoanAccount account = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(account.getId())
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    private LoanSummaryDto toLoanSummary(LoanAccount account) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(account.getAccountNumber());
        dto.setBorrowerName(account.getBorrower().getFirstName() + " " + account.getBorrower().getLastName());
        dto.setProductDescription(account.getProduct().getName());
        dto.setOriginalAmount(normalizeScale(account.getOriginalAmount()));
        dto.setCurrentBalance(normalizeScale(account.getCurrentBalance()));
        dto.setInterestRate(normalizeScale(account.getInterestRate()));
        dto.setMonthlyPayment(normalizeScale(account.getMonthlyPayment()));
        dto.setStatus(account.getStatus());
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
        dto.setPaymentId(String.valueOf(payment.getId()));
        dto.setLoanAccountNumber(payment.getLoanAccount().getAccountNumber());
        dto.setPaymentDate(formatDate(payment.getPaymentDate()));
        dto.setTotalAmount(normalizeScale(payment.getTotalAmount()));
        dto.setPrincipalAmount(normalizeScale(payment.getPrincipalAmount()));
        dto.setInterestAmount(normalizeScale(payment.getInterestAmount()));
        dto.setEscrowAmount(normalizeScale(payment.getEscrowAmount()));
        dto.setLateFee(normalizeScale(payment.getLateFee()));
        dto.setType(payment.getType());
        dto.setStatus(payment.getStatus());
        return dto;
    }

    private String formatDate(LocalDate date) {
        if (date == null) return null;
        return date.format(LEGACY_DATE_FORMAT);
    }

    private BigDecimal normalizeScale(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        if (value.signum() == 0) return value;
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
        }
        return value;
    }
}
