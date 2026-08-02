package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.migration.CodeTranslator;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The modern read path. Produces byte-identical v1 DTOs from the normalized schema.
 *
 * <p>Every list query here is a single statement with explicit fetch joins: the whole point of the
 * new model is that a loan list no longer costs 2 x N lazy loads, and an accidental N+1 would give
 * back the performance the migration is meant to buy.
 */
@Component
@Transactional(transactionManager = "modernTransactionManager", readOnly = true)
public class ModernLoanDataProvider implements LoanDataProvider {

    private final LoanAccountRepository accounts;
    private final BorrowerRepository borrowers;
    private final PaymentRepository payments;
    private final CodeTranslator codes;

    public ModernLoanDataProvider(LoanAccountRepository accounts,
                                  BorrowerRepository borrowers,
                                  PaymentRepository payments,
                                  CodeTranslator codes) {
        this.accounts = accounts;
        this.borrowers = borrowers;
        this.payments = payments;
        this.codes = codes;
    }

    @Override
    public String name() {
        return "modern";
    }

    @Override
    public List<LoanSummaryDto> getAllLoans() {
        return accounts.findAllWithBorrowerAndProduct().stream().map(this::toLoanSummary).toList();
    }

    @Override
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return accounts.findByAccountNumberWithBorrowerAndProduct(loanAccountNumber)
            .map(this::toLoanSummary)
            .orElseThrow(() -> new LoanNotFoundException("loan"));
    }

    @Override
    public List<BorrowerDto> getAllBorrowers() {
        return borrowers.findAll().stream().map(ModernLoanDataProvider::toBorrowerDto).toList();
    }

    @Override
    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowers.findByExternalId(borrowerId)
            .orElseThrow(() -> new LoanNotFoundException("borrower"));
        BorrowerDto dto = toBorrowerDto(borrower);
        dto.setLoans(accounts.findByBorrowerExternalId(borrowerId).stream().map(this::toLoanSummary).toList());
        return dto;
    }

    @Override
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return payments.findByAccountNumberOrderByDateDesc(loanAccountNumber).stream()
            .map(this::toPaymentDto)
            .toList();
    }

    private LoanSummaryDto toLoanSummary(LoanAccount account) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(account.getAccountNumber());
        Borrower borrower = account.getBorrower();
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(account.getProduct().getName());
        dto.setOriginalAmount(V1Format.originationAmount(account.getOriginalAmount()));
        dto.setCurrentBalance(V1Format.money(account.getCurrentBalance()));
        dto.setInterestRate(V1Format.rate(account.getInterestRate()));
        dto.setMonthlyPayment(V1Format.money(account.getMonthlyPayment()));
        dto.setStatus(codes.loanStatusLabel(account.getStatus()));
        dto.setOriginationDate(V1Format.date(account.getOriginationDate()));
        dto.setPropertyAddress(V1Format.propertyAddress(account.getPropertyAddress(), account.getPropertyCity(),
            account.getPropertyState(), account.getPropertyZip()));
        dto.setPropertyType(codes.propertyTypeLabel(account.getPropertyType()));
        return dto;
    }

    private static BorrowerDto toBorrowerDto(Borrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getExternalId());
        dto.setFullName(V1Format.fullName(borrower.getFirstName(), borrower.getMiddleInitial(),
            borrower.getLastName()));
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
        dto.setPaymentId(payment.getLegacyId());
        dto.setLoanAccountNumber(payment.getLoanAccount().getAccountNumber());
        dto.setPaymentDate(V1Format.date(payment.getPaymentDate()));
        dto.setTotalAmount(V1Format.money(payment.getTotalAmount()));
        dto.setPrincipalAmount(V1Format.money(payment.getPrincipalAmount()));
        dto.setInterestAmount(V1Format.money(payment.getInterestAmount()));
        dto.setEscrowAmount(V1Format.money(payment.getEscrowAmount()));
        dto.setLateFee(V1Format.money(payment.getLateFee()));
        dto.setType(codes.paymentTypeLabel(payment.getType()));
        dto.setStatus(codes.paymentStatusLabel(payment.getStatus()));
        return dto;
    }
}
