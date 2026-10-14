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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the API DTOs from the normalized schema. Values are already typed, so this only
 * formats them into the existing response shapes.
 */
@Service
@Transactional(readOnly = true)
public class ModernLoanService {

    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final Sort BY_ID = Sort.by("id");

    private final BorrowerRepository borrowerRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    public ModernLoanService(
            BorrowerRepository borrowerRepository,
            LoanAccountRepository loanAccountRepository,
            PaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<LoanSummaryDto> getAllLoans() {
        return loanAccountRepository.findAll(BY_ID).stream().map(this::toLoanSummary).toList();
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .map(this::toLoanSummary)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll(BY_ID).stream().map(this::toBorrowerDto).toList();
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findByExternalId(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);
        dto.setLoans(loanAccountRepository.findByBorrowerExternalId(borrowerId).stream()
                .map(this::toLoanSummary)
                .toList());
        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository
                .findByLoanAccountAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .toList();
    }

    private LoanSummaryDto toLoanSummary(LoanAccount loan) {
        Borrower borrower = loan.getBorrower();
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(loan.getAccountNumber());
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(loan.getProduct().getName());
        dto.setOriginalAmount(loan.getOriginalAmount());
        dto.setCurrentBalance(loan.getCurrentBalance());
        dto.setInterestRate(loan.getInterestRate());
        dto.setMonthlyPayment(loan.getMonthlyPayment());
        dto.setStatus(loan.getStatus().getLabel());
        dto.setOriginationDate(format(loan.getOriginationDate()));
        dto.setPropertyAddress(loan.getPropertyAddress() + ", " + loan.getPropertyCity()
                + ", " + loan.getPropertyState() + " " + loan.getPropertyZip());
        dto.setPropertyType(loan.getPropertyType());
        return dto;
    }

    private BorrowerDto toBorrowerDto(Borrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getExternalId());
        String middle = borrower.getMiddleInitial() != null
                ? " " + borrower.getMiddleInitial() + "."
                : "";
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
        dto.setPaymentId(payment.getLegacyPaymentId());
        dto.setLoanAccountNumber(payment.getLoanAccount().getAccountNumber());
        dto.setPaymentDate(format(payment.getPaymentDate()));
        dto.setTotalAmount(payment.getTotalAmount());
        dto.setPrincipalAmount(payment.getPrincipalAmount());
        dto.setInterestAmount(payment.getInterestAmount());
        dto.setEscrowAmount(payment.getEscrowAmount());
        dto.setLateFee(payment.getLateFee());
        dto.setType(payment.getType().getLabel());
        dto.setStatus(payment.getStatus().getLabel());
        return dto;
    }

    private static String format(LocalDate date) {
        return date != null ? API_DATE.format(date) : null;
    }
}
