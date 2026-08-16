package com.workshop.loanservice.provider;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.legacy.LegacyBorrower;
import com.workshop.loanservice.entity.legacy.LegacyLoanAccount;
import com.workshop.loanservice.entity.legacy.LegacyLoanProduct;
import com.workshop.loanservice.entity.legacy.LegacyPayment;
import com.workshop.loanservice.migration.LegacyTypeConverter;
import com.workshop.loanservice.repository.legacy.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.legacy.LegacyPaymentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads the CDW tables. Kept for reconciliation and for falling back via
 * {@code loanservice.datasource.mode=legacy}; the modern provider is the
 * end state.
 *
 * <p>Parsing and code expansion are delegated to {@link LegacyTypeConverter} and
 * {@link PresentationFormat} — the same classes the modern provider formats
 * through — so the two paths cannot drift.
 */
@Component
public class LegacyLoanDataProvider implements LoanDataProvider {

    public static final String NAME = "legacy";

    private final LegacyBorrowerRepository borrowerRepository;
    private final LegacyLoanAccountRepository loanAccountRepository;
    private final LegacyLoanProductRepository loanProductRepository;
    private final LegacyPaymentRepository paymentRepository;
    private final LegacyTypeConverter converter;
    private final PresentationFormat format;

    public LegacyLoanDataProvider(LegacyBorrowerRepository borrowerRepository,
                                  LegacyLoanAccountRepository loanAccountRepository,
                                  LegacyLoanProductRepository loanProductRepository,
                                  LegacyPaymentRepository paymentRepository,
                                  LegacyTypeConverter converter,
                                  PresentationFormat format) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.loanProductRepository = loanProductRepository;
        this.paymentRepository = paymentRepository;
        this.converter = converter;
        this.format = format;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<LoanSummaryDto> getAllLoans() {
        Map<String, LegacyLoanProduct> products = productsByCode();
        return loanAccountRepository.findAll().stream()
                .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
                .collect(Collectors.toList());
    }

    @Override
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LegacyLoanAccount acct = loanAccountRepository.findById(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        LegacyLoanProduct product = loanProductRepository.findById(acct.getProductCode()).orElse(null);
        return toLoanSummary(acct, product);
    }

    @Override
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    @Override
    public BorrowerDto getBorrowerById(String borrowerId) {
        LegacyBorrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);

        Map<String, LegacyLoanProduct> products = productsByCode();
        dto.setLoans(loanAccountRepository.findByBorrowerId(borrowerId).stream()
                .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
                .collect(Collectors.toList()));
        return dto;
    }

    @Override
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    private Map<String, LegacyLoanProduct> productsByCode() {
        return loanProductRepository.findAll().stream()
                .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));
    }

    private LoanSummaryDto toLoanSummary(LegacyLoanAccount acct, LegacyLoanProduct product) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getLoanAccountNumber());
        dto.setBorrowerName(acct.getBorrowerFirstName() + " " + acct.getBorrowerLastName());
        dto.setProductDescription(product != null ? product.getDescription() : acct.getProductCode());
        dto.setOriginalAmount(format.wholeDollars(converter.parseAmount(acct.getOriginalAmount())));
        dto.setCurrentBalance(format.money(converter.parseAmount(acct.getCurrentBalance())));
        dto.setInterestRate(format.rate(converter.parseDecimal(acct.getInterestRate())));
        dto.setMonthlyPayment(format.money(converter.parseAmount(acct.getMonthlyPayment())));
        dto.setStatus(format.loanStatus(converter.canonicalLoanStatus(acct.getStatusCode())));
        dto.setOriginationDate(format.date(converter.parseDate(acct.getOriginationDate())));
        dto.setPropertyAddress(format.fullAddress(acct.getPropertyAddress(), acct.getPropertyCity(),
                acct.getPropertyState(), acct.getPropertyZip()));
        dto.setPropertyType(format.propertyType(converter.canonicalPropertyType(acct.getPropertyType())));
        return dto;
    }

    private BorrowerDto toBorrowerDto(LegacyBorrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getBorrowerId());
        dto.setFullName(format.borrowerFullName(borrower.getFirstName(), borrower.getMiddleInitial(),
                borrower.getLastName()));
        dto.setEmail(borrower.getEmail());
        dto.setPhone(borrower.getPhoneNumber());
        dto.setCity(borrower.getCity());
        dto.setState(borrower.getStateCode());
        dto.setCreditScore(converter.parseInteger(borrower.getCreditScore()));
        dto.setEmploymentStatus(borrower.getEmploymentStatus());
        return dto;
    }

    private PaymentDto toPaymentDto(LegacyPayment pmt) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(pmt.getPaymentSequenceNumber());
        dto.setLoanAccountNumber(pmt.getLoanAccountNumber());
        dto.setPaymentDate(format.date(converter.parseDate(pmt.getPaymentDate())));
        dto.setTotalAmount(format.money(converter.parseAmount(pmt.getTotalAmount())));
        dto.setPrincipalAmount(format.money(converter.parseAmount(pmt.getPrincipalAmount())));
        dto.setInterestAmount(format.money(converter.parseAmount(pmt.getInterestAmount())));
        dto.setEscrowAmount(format.money(converter.parseAmount(pmt.getEscrowAmount())));
        dto.setLateFee(format.money(converter.parseAmount(pmt.getLateFee())));
        dto.setType(format.paymentType(converter.canonicalPaymentType(pmt.getTypeCode())));
        dto.setStatus(format.paymentStatus(converter.canonicalPaymentStatus(pmt.getStatusCode())));
        return dto;
    }
}
