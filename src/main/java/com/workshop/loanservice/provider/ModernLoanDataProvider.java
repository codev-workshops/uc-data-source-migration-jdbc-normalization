package com.workshop.loanservice.provider;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads the normalized modern schema. No string parsing and no code expansion:
 * the entities are already typed, so the only work left is presentation.
 */
@Component
public class ModernLoanDataProvider implements LoanDataProvider {

    public static final String NAME = "modern";

    private final BorrowerRepository borrowerRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;
    private final PresentationFormat format;

    public ModernLoanDataProvider(BorrowerRepository borrowerRepository,
                                  LoanAccountRepository loanAccountRepository,
                                  PaymentRepository paymentRepository,
                                  PresentationFormat format) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
        this.format = format;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<LoanSummaryDto> getAllLoans() {
        return loanAccountRepository.findAllByOrderByIdAsc().stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    @Override
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .map(this::toLoanSummary)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
    }

    @Override
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAllByOrderByIdAsc().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    @Override
    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findByExternalId(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);
        dto.setLoans(loanAccountRepository.findByBorrowerExternalIdOrderByIdAsc(borrowerId).stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList()));
        return dto;
    }

    @Override
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository
                .findByLoanAccountAccountNumberOrderByPaymentDateDescExternalIdDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    private LoanSummaryDto toLoanSummary(LoanAccount acct) {
        Borrower borrower = acct.getBorrower();
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(acct.getProduct().getName());
        dto.setOriginalAmount(format.wholeDollars(acct.getOriginalAmount()));
        dto.setCurrentBalance(format.money(acct.getCurrentBalance()));
        dto.setInterestRate(format.rate(acct.getInterestRate()));
        dto.setMonthlyPayment(format.money(acct.getMonthlyPayment()));
        dto.setStatus(format.loanStatus(acct.getStatus()));
        dto.setOriginationDate(format.date(acct.getOriginationDate()));
        dto.setPropertyAddress(format.fullAddress(acct.getPropertyAddress(), acct.getPropertyCity(),
                acct.getPropertyState(), acct.getPropertyZip()));
        dto.setPropertyType(format.propertyType(acct.getPropertyType()));
        return dto;
    }

    private BorrowerDto toBorrowerDto(Borrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getExternalId());
        dto.setFullName(format.borrowerFullName(borrower.getFirstName(), borrower.getMiddleInitial(),
                borrower.getLastName()));
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
        dto.setPaymentDate(format.date(pmt.getPaymentDate()));
        dto.setTotalAmount(format.money(pmt.getTotalAmount()));
        dto.setPrincipalAmount(format.money(pmt.getPrincipalAmount()));
        dto.setInterestAmount(format.money(pmt.getInterestAmount()));
        dto.setEscrowAmount(format.money(pmt.getEscrowAmount()));
        dto.setLateFee(format.money(pmt.getLateFee()));
        dto.setType(format.paymentType(pmt.getType()));
        dto.setStatus(format.paymentStatus(pmt.getStatus()));
        return dto;
    }
}
