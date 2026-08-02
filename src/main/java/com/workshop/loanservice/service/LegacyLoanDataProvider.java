package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The original read path, unchanged, extracted from {@code LoanService} so the modern path can be
 * compared against it row by row during the reconciliation window and rolled back to instantly.
 *
 * <p>The parsing and code-expansion logic below is deliberately a verbatim copy of the pre-migration
 * behaviour, quirks included: it is the reference implementation the golden contract tests and the
 * shadow-read comparison are measured against.
 */
@Component
public class LegacyLoanDataProvider implements LoanDataProvider {

    private final LegacyBorrowerRepository borrowerRepository;
    private final LegacyLoanAccountRepository loanAccountRepository;
    private final LegacyLoanProductRepository loanProductRepository;
    private final LegacyPaymentRepository paymentRepository;

    public LegacyLoanDataProvider(LegacyBorrowerRepository borrowerRepository,
                                  LegacyLoanAccountRepository loanAccountRepository,
                                  LegacyLoanProductRepository loanProductRepository,
                                  LegacyPaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.loanProductRepository = loanProductRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public String name() {
        return "legacy";
    }

    @Override
    public List<LoanSummaryDto> getAllLoans() {
        Map<String, LegacyLoanProduct> products = loanProductRepository.findAll()
            .stream()
            .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));

        return loanAccountRepository.findAll().stream()
            .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
            .collect(Collectors.toList());
    }

    @Override
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LegacyLoanAccount acct = loanAccountRepository.findById(loanAccountNumber)
            .orElseThrow(() -> new LoanNotFoundException("loan"));
        LegacyLoanProduct product = loanProductRepository.findById(acct.getProductCode()).orElse(null);
        return toLoanSummary(acct, product);
    }

    @Override
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
            .map(LegacyLoanDataProvider::toBorrowerDto)
            .collect(Collectors.toList());
    }

    @Override
    public BorrowerDto getBorrowerById(String borrowerId) {
        LegacyBorrower borrower = borrowerRepository.findById(borrowerId)
            .orElseThrow(() -> new LoanNotFoundException("borrower"));
        BorrowerDto dto = toBorrowerDto(borrower);

        Map<String, LegacyLoanProduct> products = loanProductRepository.findAll()
            .stream()
            .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));
        dto.setLoans(loanAccountRepository.findByBorrowerId(borrowerId).stream()
            .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
            .collect(Collectors.toList()));
        return dto;
    }

    @Override
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
            .stream()
            .map(LegacyLoanDataProvider::toPaymentDto)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // Legacy translation - preserved verbatim
    // =========================================================================

    private static LoanSummaryDto toLoanSummary(LegacyLoanAccount acct, LegacyLoanProduct product) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getLoanAccountNumber());
        dto.setBorrowerName(acct.getBorrowerFirstName() + " " + acct.getBorrowerLastName());
        dto.setProductDescription(product != null ? product.getDescription() : acct.getProductCode());
        dto.setOriginalAmount(parseLegacyAmount(acct.getOriginalAmount()));
        dto.setCurrentBalance(parseLegacyAmount(acct.getCurrentBalance()));
        dto.setInterestRate(parseLegacyDecimal(acct.getInterestRate()));
        dto.setMonthlyPayment(parseLegacyAmount(acct.getMonthlyPayment()));
        dto.setStatus(expandStatusCode(acct.getStatusCode()));
        dto.setOriginationDate(acct.getOriginationDate());
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
            + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(expandPropertyType(acct.getPropertyType()));
        return dto;
    }

    private static BorrowerDto toBorrowerDto(LegacyBorrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getBorrowerId());
        String middle = borrower.getMiddleInitial() != null ? " " + borrower.getMiddleInitial() + "." : "";
        dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
        dto.setEmail(borrower.getEmail());
        dto.setPhone(borrower.getPhoneNumber());
        dto.setCity(borrower.getCity());
        dto.setState(borrower.getStateCode());
        dto.setCreditScore(parseLegacyInteger(borrower.getCreditScore()));
        dto.setEmploymentStatus(borrower.getEmploymentStatus());
        return dto;
    }

    private static PaymentDto toPaymentDto(LegacyPayment pmt) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(pmt.getPaymentSequenceNumber());
        dto.setLoanAccountNumber(pmt.getLoanAccountNumber());
        dto.setPaymentDate(pmt.getPaymentDate());
        dto.setTotalAmount(parseLegacyAmount(pmt.getTotalAmount()));
        dto.setPrincipalAmount(parseLegacyAmount(pmt.getPrincipalAmount()));
        dto.setInterestAmount(parseLegacyAmount(pmt.getInterestAmount()));
        dto.setEscrowAmount(parseLegacyAmount(pmt.getEscrowAmount()));
        dto.setLateFee(parseLegacyAmount(pmt.getLateFee()));
        dto.setType(expandPaymentType(pmt.getTypeCode()));
        dto.setStatus(expandPaymentStatus(pmt.getStatusCode()));
        return dto;
    }

    private static BigDecimal parseLegacyAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(amount.replace(",", ""));
    }

    private static BigDecimal parseLegacyDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private static Integer parseLegacyInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private static String expandStatusCode(String code) {
        if (code == null) {
            return "Unknown";
        }
        return switch (code) {
            case "ACT" -> "Active";
            case "CLO" -> "Closed";
            case "DFT" -> "Default";
            case "FRB" -> "Forbearance";
            default -> code;
        };
    }

    private static String expandPropertyType(String code) {
        if (code == null) {
            return "Unknown";
        }
        return switch (code) {
            case "SFR" -> "Single Family Residence";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family Residence";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }

    private static String expandPaymentType(String code) {
        if (code == null) {
            return "Unknown";
        }
        return switch (code) {
            case "REG" -> "Regular";
            case "EXT" -> "Extra";
            case "PRT" -> "Partial";
            case "PRE" -> "Prepayment";
            default -> code;
        };
    }

    private static String expandPaymentStatus(String code) {
        if (code == null) {
            return "Unknown";
        }
        return switch (code) {
            case "PST" -> "Posted";
            case "REV" -> "Reversed";
            case "NSF" -> "Non-Sufficient Funds";
            case "PND" -> "Pending";
            default -> code;
        };
    }
}
