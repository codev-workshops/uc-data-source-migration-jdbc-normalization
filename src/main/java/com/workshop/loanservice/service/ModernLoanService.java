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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serves the API from the normalized modern schema. Entities are already typed,
 * so this layer only shapes them into DTOs.
 */
@Service
@ConditionalOnProperty(name = "loan.datasource.mode", havingValue = "modern", matchIfMissing = true)
@Transactional(readOnly = true)
public class ModernLoanService implements LoanService {

    private static final Logger log = LoggerFactory.getLogger(ModernLoanService.class);

    private final BorrowerRepository borrowerRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    public ModernLoanService(BorrowerRepository borrowerRepository,
                             LoanAccountRepository loanAccountRepository,
                             PaymentRepository paymentRepository) {
        log.info("Loan API serves data from the modern schema (loan.datasource.mode=modern)");
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public List<LoanSummaryDto> getAllLoans() {
        return loanAccountRepository.findAllByOrderByIdAsc().stream()
                .map(this::toLoanSummary)
                .toList();
    }

    @Override
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .map(this::toLoanSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", loanAccountNumber));
    }

    @Override
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAllByOrderByIdAsc().stream()
                .map(this::toBorrowerDto)
                .toList();
    }

    @Override
    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findByExternalId(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException("Borrower", borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);
        dto.setLoans(loanAccountRepository.findByBorrowerExternalIdOrderByIdAsc(borrowerId).stream()
                .map(this::toLoanSummary)
                .toList());
        return dto;
    }

    @Override
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        if (!loanAccountRepository.existsByAccountNumber(loanAccountNumber)) {
            throw new ResourceNotFoundException("Loan", loanAccountNumber);
        }
        return paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDescIdAsc(loanAccountNumber).stream()
                .map(this::toPaymentDto)
                .toList();
    }

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
        dto.setStatus(acct.getStatus() != null ? acct.getStatus().getLabel() : "Unknown");
        dto.setOriginationDate(acct.getOriginationDate());
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(acct.getPropertyType() != null ? acct.getPropertyType() : "Unknown");
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
        dto.setPaymentDate(pmt.getPaymentDate());
        dto.setTotalAmount(pmt.getTotalAmount());
        dto.setPrincipalAmount(pmt.getPrincipalAmount());
        dto.setInterestAmount(pmt.getInterestAmount());
        dto.setEscrowAmount(pmt.getEscrowAmount());
        dto.setLateFee(pmt.getLateFee());
        dto.setType(pmt.getType().getLabel());
        dto.setStatus(pmt.getStatus().getLabel());
        return dto;
    }
}
