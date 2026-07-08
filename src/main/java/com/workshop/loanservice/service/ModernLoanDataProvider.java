package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.ModernBorrowerRepository;
import com.workshop.loanservice.modern.repository.ModernLoanAccountRepository;
import com.workshop.loanservice.modern.repository.ModernPaymentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads from the modern normalized schema. Because the modern tables store
 * proper types (DATE, DECIMAL, INTEGER), no string-to-type parsing happens
 * here; only presentation formatting so responses stay identical to the
 * legacy-backed API (golden-file guarded): dates rendered as MM/dd/yyyy,
 * canonical status codes expanded to display labels, and whole-dollar
 * amounts rendered without a fractional part, as the legacy source did.
 */
@Component
@ConditionalOnProperty(name = "datasource.mode", havingValue = "modern", matchIfMissing = true)
public class ModernLoanDataProvider implements LoanDataProvider {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private static final Map<String, String> LOAN_STATUS_LABELS = Map.of(
            "ACTIVE", "Active",
            "CLOSED", "Closed",
            "DEFAULT", "Default",
            "FORBEARANCE", "Forbearance");

    private static final Map<String, String> PROPERTY_TYPE_LABELS = Map.of(
            "Single Family", "Single Family Residence",
            "Multi Family", "Multi-Family Residence",
            "Condominium", "Condominium",
            "Townhouse", "Townhouse");

    private static final Map<String, String> PAYMENT_TYPE_LABELS = Map.of(
            "REGULAR", "Regular",
            "EXTRA", "Extra",
            "PARTIAL", "Partial",
            "PREPAYMENT", "Prepayment");

    private static final Map<String, String> PAYMENT_STATUS_LABELS = Map.of(
            "POSTED", "Posted",
            "REVERSED", "Reversed",
            "NSF", "Non-Sufficient Funds",
            "PENDING", "Pending");

    private final ModernBorrowerRepository borrowerRepository;
    private final ModernLoanAccountRepository loanAccountRepository;
    private final ModernPaymentRepository paymentRepository;

    public ModernLoanDataProvider(ModernBorrowerRepository borrowerRepository,
                                  ModernLoanAccountRepository loanAccountRepository,
                                  ModernPaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public List<LoanSummaryDto> getAllLoans() {
        return loanAccountRepository.findAll(Sort.by("id")).stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LoanAccount acct = loanAccountRepository.findByAccountNumber(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        return toLoanSummary(acct);
    }

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll(Sort.by("id")).stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findByExternalId(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);
        dto.setLoans(loanAccountRepository.findByBorrowerExternalIdOrderByIdAsc(borrowerId)
                .stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList()));
        return dto;
    }

    @Override
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository
                .findByLoanAccountAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    private LoanSummaryDto toLoanSummary(LoanAccount acct) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        dto.setBorrowerName(acct.getBorrower().getFirstName() + " " + acct.getBorrower().getLastName());
        dto.setProductDescription(acct.getProduct().getName());
        dto.setOriginalAmount(wholeDollarsWithoutCents(acct.getOriginalAmount()));
        dto.setCurrentBalance(acct.getCurrentBalance());
        dto.setInterestRate(acct.getInterestRate());
        dto.setMonthlyPayment(acct.getMonthlyPayment());
        dto.setStatus(label(LOAN_STATUS_LABELS, acct.getStatus()));
        dto.setOriginationDate(formatDate(acct.getOriginationDate()));
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(label(PROPERTY_TYPE_LABELS, acct.getPropertyType()));
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
        dto.setPaymentId(pmt.getPaymentNumber());
        dto.setLoanAccountNumber(pmt.getLoanAccount().getAccountNumber());
        dto.setPaymentDate(formatDate(pmt.getPaymentDate()));
        dto.setTotalAmount(pmt.getTotalAmount());
        dto.setPrincipalAmount(pmt.getPrincipalAmount());
        dto.setInterestAmount(pmt.getInterestAmount());
        dto.setEscrowAmount(pmt.getEscrowAmount());
        dto.setLateFee(pmt.getLateFee());
        dto.setType(label(PAYMENT_TYPE_LABELS, pmt.getType()));
        dto.setStatus(label(PAYMENT_STATUS_LABELS, pmt.getStatus()));
        return dto;
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : DISPLAY_DATE.format(date);
    }

    private String label(Map<String, String> labels, String value) {
        if (value == null) return "Unknown";
        return labels.getOrDefault(value, value);
    }

    /**
     * The legacy source records original loan amounts in whole dollars
     * (e.g. "285,000"), so integral amounts are rendered without a
     * fractional part even though the modern column carries two decimals.
     */
    private BigDecimal wholeDollarsWithoutCents(BigDecimal amount) {
        if (amount == null) return null;
        return amount.stripTrailingZeros().scale() <= 0
                ? amount.setScale(0)
                : amount;
    }
}
