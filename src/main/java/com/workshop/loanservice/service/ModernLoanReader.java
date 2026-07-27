package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.migration.MigrationIdMap;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reads the modern normalized schema and reproduces exactly the DTOs the legacy path produces.
 *
 * <p>Every public method runs in a {@code modernTransactionManager} transaction so the lazy
 * {@code @ManyToOne} navigations on the modern entities (and the {@code migration_id_map} lookups)
 * resolve while the modern persistence context is open.
 *
 * <p>Unlike the legacy path this performs NO string-to-type parsing: the modern entities are
 * already typed ({@code BigDecimal}, {@code LocalDate}, ...). The only formatting done here is
 * turning stored codes into the historical title-case display strings and rendering dates back to
 * the legacy {@code MM/dd/yyyy} text, so the JSON stays byte-for-byte identical.
 */
@Component
public class ModernLoanReader {

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final BorrowerRepository borrowers;
    private final LoanAccountRepository accounts;
    private final PaymentRepository payments;
    private final MigrationIdMap idMap;

    public ModernLoanReader(BorrowerRepository borrowers,
                            LoanAccountRepository accounts,
                            PaymentRepository payments,
                            MigrationIdMap idMap) {
        this.borrowers = borrowers;
        this.accounts = accounts;
        this.payments = payments;
        this.idMap = idMap;
    }

    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public List<LoanSummaryDto> getAllLoans() {
        return accounts.findAll().stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public Optional<LoanSummaryDto> getLoanById(String loanAccountNumber) {
        return accounts.findByAccountNumber(loanAccountNumber).map(this::toLoanSummary);
    }

    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public List<BorrowerDto> getAllBorrowers() {
        return borrowers.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public Optional<BorrowerDto> getBorrowerById(String borrowerId) {
        return borrowers.findByExternalId(borrowerId).map(borrower -> {
            BorrowerDto dto = toBorrowerDto(borrower);
            List<LoanSummaryDto> loans = borrower.getLoanAccounts().stream()
                    .map(this::toLoanSummary)
                    .collect(Collectors.toList());
            dto.setLoans(loans);
            return dto;
        });
    }

    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return payments.findByLoanAccountAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // MODERN MAPPING METHODS (no string-to-type parsing; entities are typed)
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount acct) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        Borrower borrower = acct.getBorrower();
        dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        dto.setProductDescription(acct.getProduct().getName());
        dto.setOriginalAmount(wholeDollar(acct.getOriginalAmount()));
        dto.setCurrentBalance(acct.getCurrentBalance());
        dto.setInterestRate(acct.getInterestRate());
        dto.setMonthlyPayment(acct.getMonthlyPayment());
        dto.setStatus(displayStatus(acct.getStatus()));
        dto.setOriginationDate(formatDate(acct.getOriginationDate()));
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(displayPropertyType(acct.getPropertyType()));
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

    private PaymentDto toPaymentDto(Payment pmt) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(idMap.findLegacyId(MigrationIdMap.PAYMENT, pmt.getId()).orElse(null));
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

    private String formatDate(LocalDate date) {
        return date == null ? null : date.format(LEGACY_DATE);
    }

    /**
     * The modern {@code original_amount} column is {@code DECIMAL(12,2)}, so a whole-dollar legacy
     * amount such as {@code 285000} (which the legacy path parses from the string {@code "285,000"}
     * at scale 0) reads back as {@code 285000.00}. Loan original amounts in the legacy CDW are
     * whole dollars, so we drop the purely-zero fractional part to reproduce the legacy JSON
     * exactly. This is output formatting only; no other amount is touched (they carry real cents
     * and already round-trip byte-for-byte).
     */
    private BigDecimal wholeDollar(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    // =========================================================================
    // DISPLAY EXPANSION (output formatting; mirrors the legacy expand* methods)
    // Handles both the migrated values and any raw legacy codes migrated as-is.
    // =========================================================================

    private String displayStatus(String value) {
        if (value == null) return "Unknown";
        return switch (value) {
            case "ACTIVE" -> "Active";
            case "CLOSED" -> "Closed";
            case "DEFAULT" -> "Default";
            case "FORBEARANCE" -> "Forbearance";
            default -> value;
        };
    }

    private String displayPropertyType(String value) {
        if (value == null) return "Unknown";
        return switch (value) {
            case "Single Family" -> "Single Family Residence";
            case "Condominium" -> "Condominium";
            case "TWN" -> "Townhouse";
            case "MFR" -> "Multi-Family Residence";
            default -> value;
        };
    }

    private String displayPaymentType(String value) {
        if (value == null) return "Unknown";
        return switch (value) {
            case "REGULAR" -> "Regular";
            case "EXTRA" -> "Extra";
            case "PARTIAL" -> "Partial";
            case "PREPAYMENT" -> "Prepayment";
            default -> value;
        };
    }

    private String displayPaymentStatus(String value) {
        if (value == null) return "Unknown";
        return switch (value) {
            case "POSTED" -> "Posted";
            case "REVERSED" -> "Reversed";
            case "NSF" -> "Non-Sufficient Funds";
            case "PENDING" -> "Pending";
            default -> value;
        };
    }
}
