package com.workshop.loanservice.service;

import static com.workshop.loanservice.migration.LegacyValueParser.parseDate;
import static com.workshop.loanservice.migration.LegacyValueParser.parseDecimal;
import static com.workshop.loanservice.migration.LegacyValueParser.parseInteger;
import static com.workshop.loanservice.migration.LegacyValueParser.require;
import static com.workshop.loanservice.migration.LegacyValueParser.trimToNull;
import static com.workshop.loanservice.service.ApiFormats.formatDate;
import static com.workshop.loanservice.service.ApiFormats.zeroIfNull;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.legacy.LegacyBorrower;
import com.workshop.loanservice.entity.legacy.LegacyLoanAccount;
import com.workshop.loanservice.entity.legacy.LegacyLoanProduct;
import com.workshop.loanservice.entity.legacy.LegacyPayment;
import com.workshop.loanservice.entity.modern.LoanStatus;
import com.workshop.loanservice.migration.LegacyCodeMapper;
import com.workshop.loanservice.migration.LegacyDataException;
import com.workshop.loanservice.repository.legacy.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.legacy.LegacyPaymentRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the legacy CDW tables and applies the same {@code LegacyValueParser}/{@code LegacyCodeMapper}
 * transformations as {@code DataMigrationService}, so every DTO matches what {@link ModernLoanDataSource}
 * returns for the migrated row. In particular {@code borrowerName} comes from {@code CDW_BORR_MSTR} (not the
 * denormalized copy on {@code CDW_LN_ACCT}) and payments are ordered by parsed date, newest first.
 *
 * <p>Rows the migration would reject (malformed required values, unknown payment codes, orphan keys) raise
 * {@link LegacyDataException} instead of being rendered.
 */
@SuppressWarnings("deprecation")
@Component
@ConditionalOnProperty(name = LoanDataSource.READ_SOURCE_PROPERTY, havingValue = "legacy")
@Transactional(transactionManager = "legacyTransactionManager", readOnly = true)
public class LegacyLoanDataSource implements LoanDataSource {

    private final LegacyBorrowerRepository borrowerRepository;
    private final LegacyLoanProductRepository productRepository;
    private final LegacyLoanAccountRepository loanAccountRepository;
    private final LegacyPaymentRepository paymentRepository;

    public LegacyLoanDataSource(LegacyBorrowerRepository borrowerRepository,
                                LegacyLoanProductRepository productRepository,
                                LegacyLoanAccountRepository loanAccountRepository,
                                LegacyPaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.productRepository = productRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public List<LoanSummaryDto> getAllLoans() {
        return toLoanSummaries(loanAccountRepository.findAll());
    }

    @Override
    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LegacyLoanAccount account = loanAccountRepository.findById(loanAccountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanAccountNumber));
        LegacyBorrower borrower = resolve("BORR_ID", account.getBorrowerId(), borrowerRepository::findById);
        LegacyLoanProduct product = resolve("PROD_CD", account.getProductCode(), productRepository::findById);
        return toLoanSummary(account, borrower, product);
    }

    @Override
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .toList();
    }

    @Override
    public BorrowerDto getBorrowerById(String borrowerId) {
        LegacyBorrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);
        dto.setLoans(toLoanSummaries(loanAccountRepository.findByBorrowerId(borrowerId)));
        return dto;
    }

    /** Sorted on the parsed date: the VARCHAR {@code PMT_DT} would sort MM/DD/YYYY text, wrong across years. */
    @Override
    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentRepository.findByLoanAccountNumber(loanAccountNumber).stream()
                .map(this::toPaymentDto)
                .sorted(Comparator.comparing(LegacyLoanDataSource::paymentDate).reversed())
                .toList();
    }

    private List<LoanSummaryDto> toLoanSummaries(List<LegacyLoanAccount> accounts) {
        Map<String, LegacyBorrower> borrowers = byKey(borrowerRepository.findAll(), LegacyBorrower::getBorrowerId);
        Map<String, LegacyLoanProduct> products =
                byKey(productRepository.findAll(), LegacyLoanProduct::getProductCode);
        return accounts.stream()
                .map(account -> toLoanSummary(account,
                        resolve("BORR_ID", account.getBorrowerId(), id -> Optional.ofNullable(borrowers.get(id))),
                        resolve("PROD_CD", account.getProductCode(), code -> Optional.ofNullable(products.get(code)))))
                .toList();
    }

    private LoanSummaryDto toLoanSummary(LegacyLoanAccount account, LegacyBorrower borrower,
                                         LegacyLoanProduct product) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(require("LN_ACCT_NBR", account.getLoanAccountNumber()));
        dto.setBorrowerName(require("BORR_FST_NM", borrower.getFirstName())
                + " " + require("BORR_LST_NM", borrower.getLastName()));
        dto.setProductDescription(require("PROD_DESC_TXT", product.getDescription()));
        dto.setOriginalAmount(require("LN_ORIG_AMT", parseDecimal("LN_ORIG_AMT", account.getOriginalAmount())));
        dto.setCurrentBalance(require("LN_CURR_BAL", parseDecimal("LN_CURR_BAL", account.getCurrentBalance())));
        dto.setInterestRate(require("LN_INT_RT", parseDecimal("LN_INT_RT", account.getInterestRate())));
        dto.setMonthlyPayment(require("LN_PMT_AMT", parseDecimal("LN_PMT_AMT", account.getMonthlyPayment())));
        dto.setStatus(statusLabel(account.getStatusCode()));
        dto.setOriginationDate(formatDate(
                require("LN_ORIG_DT", parseDate("LN_ORIG_DT", account.getOriginationDate()))));
        dto.setPropertyAddress(ApiFormats.propertyAddress(account.getPropertyAddress(), account.getPropertyCity(),
                account.getPropertyState(), account.getPropertyZip()));
        dto.setPropertyType(ApiFormats.unknownIfNull(LegacyCodeMapper.propertyType(account.getPropertyType())));
        return dto;
    }

    /** Same labels as the modern path: blank is "Unknown", unmapped codes are shown verbatim. */
    private static String statusLabel(String code) {
        LoanStatus status = LegacyCodeMapper.loanStatus(code);
        if (status == null) {
            return ApiFormats.UNKNOWN;
        }
        return status == LoanStatus.UNKNOWN ? trimToNull(code) : status.getDisplayName();
    }

    private BorrowerDto toBorrowerDto(LegacyBorrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(require("BORR_ID", borrower.getBorrowerId()));
        dto.setFullName(ApiFormats.fullName(require("BORR_FST_NM", borrower.getFirstName()),
                trimToNull(borrower.getMiddleInitial()), require("BORR_LST_NM", borrower.getLastName())));
        dto.setEmail(trimToNull(borrower.getEmail()));
        dto.setPhone(trimToNull(borrower.getPhoneNumber()));
        dto.setCity(trimToNull(borrower.getCity()));
        dto.setState(trimToNull(borrower.getStateCode()));
        dto.setCreditScore(lenientCreditScore(borrower.getCreditScore()));
        dto.setEmploymentStatus(trimToNull(borrower.getEmploymentStatus()));
        return dto;
    }

    /** The migration stores a malformed (optional) credit score as NULL, so the legacy read shows null too. */
    private static Integer lenientCreditScore(String raw) {
        try {
            return parseInteger("BORR_CRDT_SCR", raw);
        } catch (LegacyDataException e) {
            return null;
        }
    }

    private PaymentDto toPaymentDto(LegacyPayment payment) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(require("PMT_SEQ_NBR", payment.getPaymentSequenceNumber()));
        dto.setLoanAccountNumber(require("LN_ACCT_NBR", payment.getLoanAccountNumber()));
        dto.setPaymentDate(formatDate(require("PMT_DT", parseDate("PMT_DT", payment.getPaymentDate()))));
        dto.setTotalAmount(require("PMT_AMT", parseDecimal("PMT_AMT", payment.getTotalAmount())));
        dto.setPrincipalAmount(zeroIfNull(parseDecimal("PMT_PRIN_AMT", payment.getPrincipalAmount())));
        dto.setInterestAmount(zeroIfNull(parseDecimal("PMT_INT_AMT", payment.getInterestAmount())));
        dto.setEscrowAmount(zeroIfNull(parseDecimal("PMT_ESCROW_AMT", payment.getEscrowAmount())));
        dto.setLateFee(zeroIfNull(parseDecimal("PMT_LATE_FEE", payment.getLateFee())));
        dto.setType(LegacyCodeMapper.paymentType(payment.getTypeCode()).getDisplayName());
        dto.setStatus(LegacyCodeMapper.paymentStatus(payment.getStatusCode()).getDisplayName());
        return dto;
    }

    private static LocalDate paymentDate(PaymentDto payment) {
        return parseDate("PMT_DT", payment.getPaymentDate());
    }

    /** Keyed by trimmed business key; blank keys are dropped and the first of two equal keys wins. */
    private static <T> Map<String, T> byKey(List<T> rows, Function<T, String> key) {
        return rows.stream()
                .filter(row -> trimToNull(key.apply(row)) != null)
                .collect(Collectors.toMap(
                        row -> trimToNull(key.apply(row)), Function.identity(), (first, duplicate) -> first));
    }

    private static <T> T resolve(String column, String legacyKey, Function<String, Optional<T>> lookup) {
        String key = require(column, legacyKey);
        return lookup.apply(key)
                .orElseThrow(() -> new LegacyDataException(column + " '" + key + "' does not resolve to a row"));
    }
}
