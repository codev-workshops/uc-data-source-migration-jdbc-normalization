package com.workshop.loanservice.migration;

import static com.workshop.loanservice.migration.LegacyValueParser.activeFlag;
import static com.workshop.loanservice.migration.LegacyValueParser.expirySentinelToNull;
import static com.workshop.loanservice.migration.LegacyValueParser.normaliseCode;
import static com.workshop.loanservice.migration.LegacyValueParser.parseAmount;
import static com.workshop.loanservice.migration.LegacyValueParser.parseDate;
import static com.workshop.loanservice.migration.LegacyValueParser.parseDecimal;
import static com.workshop.loanservice.migration.LegacyValueParser.parseInteger;
import static com.workshop.loanservice.migration.LegacyValueParser.parseShort;
import static com.workshop.loanservice.migration.LegacyValueParser.requireAmount;
import static com.workshop.loanservice.migration.LegacyValueParser.requireDate;
import static com.workshop.loanservice.migration.LegacyValueParser.requireDecimal;
import static com.workshop.loanservice.migration.LegacyValueParser.requireText;
import static com.workshop.loanservice.migration.LegacyValueParser.requireTimestamp;
import static com.workshop.loanservice.migration.LegacyValueParser.text;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.migration.MigrationReport.NameDivergence;
import com.workshop.loanservice.migration.MigrationReport.QuarantinedRow;
import com.workshop.loanservice.modern.entity.Address;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.BorrowerRecordType;
import com.workshop.loanservice.modern.entity.BorrowerStatus;
import com.workshop.loanservice.modern.entity.EmploymentStatus;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.LoanStatus;
import com.workshop.loanservice.modern.entity.MigrationQuarantine;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.entity.PaymentStatus;
import com.workshop.loanservice.modern.entity.PaymentType;
import com.workshop.loanservice.modern.entity.ProductType;
import com.workshop.loanservice.modern.entity.Property;
import com.workshop.loanservice.modern.entity.PropertyType;
import com.workshop.loanservice.modern.entity.QuarantineReason;
import com.workshop.loanservice.modern.entity.RateType;
import com.workshop.loanservice.modern.repository.AddressRepository;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.MigrationQuarantineRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.modern.repository.PropertyRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the legacy CDW tables into the modern schema following {@code
 * docs/proposed-column-mappings.md}, in FK order: borrower (+ address) → loan_product → loan_account
 * (+ property, address) → payment.
 *
 * <p>Each legacy row is loaded in its own transaction. A row that fails parsing, reference
 * resolution, a business rule such as {@link PaymentSplitValidator}, or a database constraint is
 * rolled back, logged and written to {@code migration_quarantine}; the load then continues. The
 * target schema's constraints are never relaxed to admit a row. Expects an empty target schema.
 */
@Service
public class LegacyToModernMigrator {

  static final String BORROWER_TABLE = "CDW_BORR_MSTR";
  static final String PRODUCT_TABLE = "CDW_LN_PROD";
  static final String LOAN_TABLE = "CDW_LN_ACCT";
  static final String PAYMENT_TABLE = "CDW_PMT_HIST";

  private static final Logger log = LoggerFactory.getLogger(LegacyToModernMigrator.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int INTEREST_RATE_SCALE = 3;
  private static final int LTV_SCALE = 2;
  private static final int MAX_SOURCE_ROW_LENGTH = 4000;
  private static final int MAX_DETAIL_LENGTH = 500;

  private final LegacyBorrowerRepository legacyBorrowers;
  private final LegacyLoanProductRepository legacyProducts;
  private final LegacyLoanAccountRepository legacyLoans;
  private final LegacyPaymentRepository legacyPayments;
  private final AddressRepository addressRepository;
  private final BorrowerRepository borrowerRepository;
  private final LoanProductRepository loanProductRepository;
  private final PropertyRepository propertyRepository;
  private final LoanAccountRepository loanAccountRepository;
  private final PaymentRepository paymentRepository;
  private final MigrationQuarantineRepository quarantineRepository;
  private final EntityManager entityManager;
  private final TransactionTemplate rowTransaction;

  public LegacyToModernMigrator(
      LegacyBorrowerRepository legacyBorrowers,
      LegacyLoanProductRepository legacyProducts,
      LegacyLoanAccountRepository legacyLoans,
      LegacyPaymentRepository legacyPayments,
      AddressRepository addressRepository,
      BorrowerRepository borrowerRepository,
      LoanProductRepository loanProductRepository,
      PropertyRepository propertyRepository,
      LoanAccountRepository loanAccountRepository,
      PaymentRepository paymentRepository,
      MigrationQuarantineRepository quarantineRepository,
      EntityManager entityManager,
      PlatformTransactionManager transactionManager) {
    this.legacyBorrowers = legacyBorrowers;
    this.legacyProducts = legacyProducts;
    this.legacyLoans = legacyLoans;
    this.legacyPayments = legacyPayments;
    this.addressRepository = addressRepository;
    this.borrowerRepository = borrowerRepository;
    this.loanProductRepository = loanProductRepository;
    this.propertyRepository = propertyRepository;
    this.loanAccountRepository = loanAccountRepository;
    this.paymentRepository = paymentRepository;
    this.quarantineRepository = quarantineRepository;
    this.entityManager = entityManager;
    this.rowTransaction = new TransactionTemplate(transactionManager);
    this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public MigrationReport migrate() {
    MigrationReport report = new MigrationReport();
    List<LegacyBorrower> borrowers = legacyBorrowers.findAll();
    List<LegacyLoanAccount> loans = legacyLoans.findAll();
    Map<String, Set<String>> ssnLast4ByBorrower = collectSsnLast4(loans);
    reportNameDivergences(borrowers, loans, report);

    migrateRows(
        report,
        BORROWER_TABLE,
        borrowers,
        LegacyBorrower::getBorrowerId,
        row -> loadBorrower(row, ssnLast4ByBorrower));
    migrateRows(
        report,
        PRODUCT_TABLE,
        legacyProducts.findAll(),
        LegacyLoanProduct::getProductCode,
        this::loadProduct);
    migrateRows(report, LOAN_TABLE, loans, LegacyLoanAccount::getLoanAccountNumber, this::loadLoan);
    migrateRows(
        report,
        PAYMENT_TABLE,
        legacyPayments.findAll(),
        LegacyPayment::getPaymentSequenceNumber,
        this::loadPayment);

    log.info("{}", report);
    return report;
  }

  private <T> void migrateRows(
      MigrationReport report,
      String table,
      List<T> rows,
      Function<T, String> keyOf,
      Consumer<T> loader) {
    for (T row : rows) {
      report.recordRead(table);
      String key = keyOf.apply(row);
      try {
        rowTransaction.executeWithoutResult(status -> loader.accept(row));
        report.recordLoaded(table);
      } catch (RowRejectedException e) {
        quarantine(report, table, key, row, e.getReason(), e.getField(), e.getMessage());
      } catch (DataIntegrityViolationException e) {
        quarantine(
            report,
            table,
            key,
            row,
            QuarantineReason.CONSTRAINT_VIOLATION,
            null,
            e.getMostSpecificCause().getMessage());
      }
    }
  }

  private void quarantine(
      MigrationReport report,
      String table,
      String key,
      Object row,
      QuarantineReason reason,
      String field,
      String detail) {
    String boundedDetail = truncate(detail, MAX_DETAIL_LENGTH);
    log.warn("Quarantined {} row {}: {} on {} - {}", table, key, reason, field, boundedDetail);
    rowTransaction.executeWithoutResult(
        status ->
            quarantineRepository.save(
                new MigrationQuarantine(
                    table,
                    key,
                    truncate(toJson(row), MAX_SOURCE_ROW_LENGTH),
                    reason,
                    field,
                    boundedDetail)));
    report.recordQuarantined(table, new QuarantinedRow(table, key, reason, field, boundedDetail));
  }

  private void loadBorrower(LegacyBorrower src, Map<String, Set<String>> ssnLast4ByBorrower) {
    String legacyId = requireText("BORR_ID", src.getBorrowerId());
    Borrower borrower = new Borrower();
    borrower.setLegacyBorrowerId(legacyId);
    borrower.setFirstName(requireText("BORR_FST_NM", src.getFirstName()));
    borrower.setLastName(requireText("BORR_LST_NM", src.getLastName()));
    borrower.setMiddleInitial(text(src.getMiddleInitial()));
    borrower.setSsnEncrypted(requireText("BORR_SSN_ENCR", src.getSsnEncrypted()));
    borrower.setSsnLast4(resolveSsnLast4(legacyId, ssnLast4ByBorrower));
    borrower.setDateOfBirth(requireDate("BORR_DOB_DT", src.getDateOfBirth()));
    borrower.setMailingAddress(
        findOrCreateAddress(
            requireText("BORR_ADDR_LN1", src.getAddressLine1()),
            text(src.getAddressLine2()),
            requireText("BORR_CTY_NM", src.getCity()),
            normaliseCode("BORR_ST_CD", src.getStateCode()),
            requireText("BORR_ZIP_CD", src.getZipCode())));
    borrower.setPhoneNumber(text(src.getPhoneNumber()));
    String email = text(src.getEmail());
    borrower.setEmailAddress(email == null ? null : email.toLowerCase(Locale.ROOT));
    borrower.setCreditScore(parseShort("BORR_CRDT_SCR", src.getCreditScore()));
    borrower.setEmploymentStatus(
        reference(EmploymentStatus.class, "BORR_EMP_STAT", src.getEmploymentStatus()));
    borrower.setAnnualIncome(parseAmount("BORR_ANN_INCM", src.getAnnualIncome()));
    borrower.setStatus(reference(BorrowerStatus.class, "BORR_STAT_CD", src.getStatusCode()));
    borrower.setRecordType(
        reference(BorrowerRecordType.class, "BORR_REC_TYP", src.getRecordType()));
    borrower.setCreatedAt(requireTimestamp("BORR_CRET_DT", src.getCreatedDate()));
    borrower.setUpdatedAt(requireTimestamp("BORR_UPDT_DT", src.getUpdatedDate()));
    borrowerRepository.saveAndFlush(borrower);
  }

  private void loadProduct(LegacyLoanProduct src) {
    LoanProduct product = new LoanProduct();
    product.setProductCode(requireText("PROD_CD", src.getProductCode()));
    product.setDescription(requireText("PROD_DESC_TXT", src.getDescription()));
    product.setProductType(reference(ProductType.class, "PROD_TYP_CD", src.getTypeCode()));
    product.setTermMonths(requireShort("PROD_TERM_MOS", src.getTermMonths()));
    product.setRateType(reference(RateType.class, "PROD_RT_TYP", src.getRateType()));
    product.setMinAmount(requireAmount("PROD_MIN_AMT", src.getMinAmount()));
    product.setMaxAmount(requireAmount("PROD_MAX_AMT", src.getMaxAmount()));
    product.setActive(activeFlag(src.getStatusCode()));
    product.setEffectiveDate(requireDate("PROD_EFF_DT", src.getEffectiveDate()));
    product.setExpiryDate(expirySentinelToNull(parseDate("PROD_EXP_DT", src.getExpirationDate())));
    loanProductRepository.saveAndFlush(product);
  }

  private void loadLoan(LegacyLoanAccount src) {
    String borrowerId = requireText("BORR_ID", src.getBorrowerId());
    String productCode = requireText("PROD_CD", src.getProductCode());

    LoanAccount loan = new LoanAccount();
    loan.setAccountNumber(requireText("LN_ACCT_NBR", src.getLoanAccountNumber()));
    loan.setBorrower(
        borrowerRepository
            .findByLegacyBorrowerId(borrowerId)
            .orElseThrow(() -> orphan("BORR_ID", "borrower", borrowerId)));
    loan.setProduct(
        loanProductRepository
            .findByProductCode(productCode)
            .orElseThrow(() -> orphan("PROD_CD", "loan product", productCode)));
    loan.setProperty(loadProperty(src));
    loan.setOriginalAmount(requireAmount("LN_ORIG_AMT", src.getOriginalAmount()));
    loan.setCurrentBalance(requireAmount("LN_CURR_BAL", src.getCurrentBalance()));
    loan.setInterestRate(requireDecimal("LN_INT_RT", src.getInterestRate(), INTEREST_RATE_SCALE));
    loan.setTermMonths(requireShort("LN_TERM_MOS", src.getTermMonths()));
    loan.setMonthlyPaymentAmount(requireAmount("LN_PMT_AMT", src.getMonthlyPayment()));
    loan.setOriginationDate(requireDate("LN_ORIG_DT", src.getOriginationDate()));
    loan.setMaturityDate(requireDate("LN_MAT_DT", src.getMaturityDate()));
    loan.setFirstPaymentDate(requireDate("LN_1ST_PMT_DT", src.getFirstPaymentDate()));
    loan.setNextPaymentDate(parseDate("LN_NXT_PMT_DT", src.getNextPaymentDate()));
    loan.setStatus(reference(LoanStatus.class, "LN_STAT_CD", src.getStatusCode()));
    Integer delinquencyDays = parseInteger("LN_DLQ_DAYS", src.getDelinquencyDays());
    loan.setDelinquencyDays(delinquencyDays == null ? 0 : delinquencyDays);
    loan.setEscrowBalance(requireAmount("LN_ESCROW_BAL", src.getEscrowBalance()));
    loan.setLoanToValuePct(parseDecimal("LN_LTV_PCT", src.getLtvPercent(), LTV_SCALE));
    loan.setCreatedAt(requireTimestamp("LN_CRET_DT", src.getCreatedDate()));
    loan.setUpdatedAt(requireTimestamp("LN_UPDT_DT", src.getUpdatedDate()));
    loanAccountRepository.saveAndFlush(loan);
  }

  private Property loadProperty(LegacyLoanAccount src) {
    Property property = new Property();
    property.setAddress(
        findOrCreateAddress(
            requireText("PROP_ADDR_LN1", src.getPropertyAddress()),
            null,
            requireText("PROP_CTY_NM", src.getPropertyCity()),
            normaliseCode("PROP_ST_CD", src.getPropertyState()),
            requireText("PROP_ZIP_CD", src.getPropertyZip())));
    property.setPropertyType(reference(PropertyType.class, "PROP_TYP_CD", src.getPropertyType()));
    property.setAppraisedValue(requireAmount("PROP_APRS_VAL", src.getAppraisedValue()));
    return propertyRepository.save(property);
  }

  private void loadPayment(LegacyPayment src) {
    String accountNumber = requireText("LN_ACCT_NBR", src.getLoanAccountNumber());

    Payment payment = new Payment();
    payment.setLegacyPaymentId(requireText("PMT_SEQ_NBR", src.getPaymentSequenceNumber()));
    payment.setLoanAccount(
        loanAccountRepository
            .findByAccountNumber(accountNumber)
            .orElseThrow(() -> orphan("LN_ACCT_NBR", "loan account", accountNumber)));
    payment.setPaymentDate(requireDate("PMT_DT", src.getPaymentDate()));
    payment.setTotalAmount(requireAmount("PMT_AMT", src.getTotalAmount()));
    payment.setPrincipalAmount(requireAmount("PMT_PRIN_AMT", src.getPrincipalAmount()));
    payment.setInterestAmount(requireAmount("PMT_INT_AMT", src.getInterestAmount()));
    payment.setEscrowAmount(requireAmount("PMT_ESCROW_AMT", src.getEscrowAmount()));
    payment.setLateFeeAmount(requireAmount("PMT_LATE_FEE", src.getLateFee()));
    PaymentSplitValidator.validate(
        payment.getTotalAmount(),
        payment.getPrincipalAmount(),
        payment.getInterestAmount(),
        payment.getEscrowAmount());
    payment.setPaymentType(reference(PaymentType.class, "PMT_TYP_CD", src.getTypeCode()));
    payment.setStatus(reference(PaymentStatus.class, "PMT_STAT_CD", src.getStatusCode()));
    payment.setReceivedDate(parseDate("PMT_RECV_DT", src.getReceivedDate()));
    payment.setProcessedDate(parseDate("PMT_PROC_DT", src.getProcessedDate()));
    payment.setCreatedAt(requireTimestamp("PMT_CRET_DT", src.getCreatedDate()));
    payment.setUpdatedAt(requireTimestamp("PMT_UPDT_DT", src.getUpdatedDate()));
    paymentRepository.saveAndFlush(payment);
  }

  private Address findOrCreateAddress(
      String line1, String line2, String city, String stateCode, String postalCode) {
    return addressRepository
        .findFirstByLine1AndLine2AndCityAndStateCodeAndPostalCode(
            line1, line2, city, stateCode, postalCode)
        .orElseGet(
            () -> {
              Address address = new Address();
              address.setLine1(line1);
              address.setLine2(line2);
              address.setCity(city);
              address.setStateCode(stateCode);
              address.setPostalCode(postalCode);
              return addressRepository.saveAndFlush(address);
            });
  }

  private <T> T reference(Class<T> type, String field, String raw) {
    String code = normaliseCode(field, raw);
    T value = entityManager.find(type, code);
    if (value == null) {
      throw new RowRejectedException(
          QuarantineReason.UNKNOWN_CODE, field, "unknown code '" + code + "'");
    }
    return value;
  }

  private static Short requireShort(String field, String raw) {
    Short value = parseShort(field, raw);
    if (value == null) {
      throw new RowRejectedException(QuarantineReason.MISSING_REQUIRED, field, "value is required");
    }
    return value;
  }

  private static RowRejectedException orphan(String field, String parent, String key) {
    return new RowRejectedException(
        QuarantineReason.ORPHAN_REFERENCE, field, "no migrated " + parent + " '" + key + "'");
  }

  private static Map<String, Set<String>> collectSsnLast4(List<LegacyLoanAccount> loans) {
    Map<String, Set<String>> byBorrower = new HashMap<>();
    for (LegacyLoanAccount loan : loans) {
      String borrowerId = text(loan.getBorrowerId());
      String last4 = text(loan.getBorrowerSsnLast4());
      if (borrowerId != null && last4 != null) {
        byBorrower.computeIfAbsent(borrowerId, id -> new TreeSet<>()).add(last4);
      }
    }
    return byBorrower;
  }

  private static String resolveSsnLast4(String borrowerId, Map<String, Set<String>> byBorrower) {
    Set<String> values = byBorrower.getOrDefault(borrowerId, Set.of());
    if (values.size() > 1) {
      throw new RowRejectedException(
          QuarantineReason.CONFLICTING_VALUE,
          "BORR_SSN_LST4",
          "loan rows disagree on SSN last-4: " + values.size() + " distinct values");
    }
    return values.isEmpty() ? null : values.iterator().next();
  }

  private static void reportNameDivergences(
      List<LegacyBorrower> borrowers, List<LegacyLoanAccount> loans, MigrationReport report) {
    Map<String, LegacyBorrower> masterById = new HashMap<>();
    borrowers.forEach(b -> masterById.put(text(b.getBorrowerId()), b));
    for (LegacyLoanAccount loan : loans) {
      LegacyBorrower master = masterById.get(text(loan.getBorrowerId()));
      if (master == null) {
        continue;
      }
      String loanName = fullName(loan.getBorrowerFirstName(), loan.getBorrowerLastName());
      String masterName = fullName(master.getFirstName(), master.getLastName());
      if (!Objects.equals(loanName, masterName)) {
        report.recordNameDivergence(
            new NameDivergence(
                loan.getLoanAccountNumber(), master.getBorrowerId(), loanName, masterName));
      }
    }
  }

  private static String fullName(String first, String last) {
    return Objects.toString(text(first), "") + " " + Objects.toString(text(last), "");
  }

  private static String toJson(Object row) {
    try {
      return JSON.writeValueAsString(row);
    } catch (JsonProcessingException e) {
      return String.valueOf(row);
    }
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
