package com.workshop.loanservice.migration;

import static com.workshop.loanservice.migration.LegacyValueParser.parseAmount;
import static com.workshop.loanservice.migration.LegacyValueParser.parseDate;
import static com.workshop.loanservice.migration.LegacyValueParser.parseDecimal;
import static com.workshop.loanservice.migration.LegacyValueParser.parseInteger;
import static com.workshop.loanservice.migration.LegacyValueParser.parseTimestamp;
import static com.workshop.loanservice.migration.LegacyValueParser.trimToNull;

import com.workshop.loanservice.domain.BorrowerStatus;
import com.workshop.loanservice.domain.CodedValue;
import com.workshop.loanservice.domain.LoanStatus;
import com.workshop.loanservice.domain.PaymentStatus;
import com.workshop.loanservice.domain.PaymentType;
import com.workshop.loanservice.domain.PropertyType;
import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Field-by-field transformation of legacy CDW rows into modern entities, implementing
 * data/mappings/column_mappings.md. Foreign keys are resolved by the caller.
 *
 * <p>Dropped columns (not mapped): CDW_BORR_MSTR.BORR_REC_TYP and the denormalized
 * CDW_LN_ACCT.BORR_FST_NM / BORR_LST_NM / BORR_SSN_LST4.
 */
@Component
@SuppressWarnings("deprecation")
public class LegacyRecordMapper {

  private static final Logger log = LoggerFactory.getLogger(LegacyRecordMapper.class);

  /** CDW_BORR_MSTR → borrowers. */
  public Borrower toBorrower(LegacyBorrower legacy) {
    Borrower borrower = new Borrower();
    borrower.setExternalId(required("BORR_ID", legacy.getBorrowerId()));
    borrower.setFirstName(required("BORR_FST_NM", legacy.getFirstName()));
    borrower.setLastName(required("BORR_LST_NM", legacy.getLastName()));
    borrower.setMiddleInitial(legacy.getMiddleInitial());
    borrower.setSsnHash(legacy.getSsnEncrypted());
    borrower.setDateOfBirth(parseDate("BORR_DOB_DT", legacy.getDateOfBirth()));
    borrower.setAddressLine1(legacy.getAddressLine1());
    borrower.setAddressLine2(legacy.getAddressLine2());
    borrower.setCity(legacy.getCity());
    borrower.setState(legacy.getStateCode());
    borrower.setZipCode(legacy.getZipCode());
    borrower.setPhone(legacy.getPhoneNumber());
    borrower.setEmail(legacy.getEmail());
    borrower.setCreditScore(parseInteger("BORR_CRDT_SCR", legacy.getCreditScore()));
    borrower.setEmploymentStatus(legacy.getEmploymentStatus());
    borrower.setAnnualIncome(parseAmount("BORR_ANN_INCM", legacy.getAnnualIncome()));
    borrower.setCreatedAt(parseTimestamp("BORR_CRET_DT", legacy.getCreatedDate()));
    borrower.setUpdatedAt(parseTimestamp("BORR_UPDT_DT", legacy.getUpdatedDate()));
    borrower.setStatus(expandCode(BorrowerStatus.class, "BORR_STAT_CD", legacy.getStatusCode()));
    return borrower;
  }

  /** CDW_LN_PROD → loan_products. */
  public LoanProduct toLoanProduct(LegacyLoanProduct legacy) {
    LoanProduct product = new LoanProduct();
    product.setCode(required("PROD_CD", legacy.getProductCode()));
    product.setName(required("PROD_DESC_TXT", legacy.getDescription()));
    product.setType(required("PROD_TYP_CD", legacy.getTypeCode()));
    product.setTermMonths(required("PROD_TERM_MOS",
        parseInteger("PROD_TERM_MOS", legacy.getTermMonths())));
    product.setRateType(required("PROD_RT_TYP", legacy.getRateType()));
    product.setMinAmount(parseAmount("PROD_MIN_AMT", legacy.getMinAmount()));
    product.setMaxAmount(parseAmount("PROD_MAX_AMT", legacy.getMaxAmount()));
    product.setActive(toActiveFlag(legacy.getStatusCode()));
    product.setEffectiveDate(parseDate("PROD_EFF_DT", legacy.getEffectiveDate()));
    product.setExpirationDate(parseDate("PROD_EXP_DT", legacy.getExpirationDate()));
    return product;
  }

  /** CDW_LN_ACCT → loan_accounts, with BORR_ID and PROD_CD already resolved to entities. */
  public LoanAccount toLoanAccount(LegacyLoanAccount legacy, Borrower borrower,
      LoanProduct product) {
    LoanAccount account = new LoanAccount();
    account.setAccountNumber(required("LN_ACCT_NBR", legacy.getLoanAccountNumber()));
    account.setBorrower(required("BORR_ID", borrower));
    account.setProduct(required("PROD_CD", product));
    account.setOriginalAmount(required("LN_ORIG_AMT",
        parseAmount("LN_ORIG_AMT", legacy.getOriginalAmount())));
    account.setCurrentBalance(required("LN_CURR_BAL",
        parseAmount("LN_CURR_BAL", legacy.getCurrentBalance())));
    account.setInterestRate(required("LN_INT_RT",
        parseDecimal("LN_INT_RT", legacy.getInterestRate())));
    account.setTermMonths(required("LN_TERM_MOS",
        parseInteger("LN_TERM_MOS", legacy.getTermMonths())));
    account.setMonthlyPayment(required("LN_PMT_AMT",
        parseAmount("LN_PMT_AMT", legacy.getMonthlyPayment())));
    account.setOriginationDate(required("LN_ORIG_DT",
        parseDate("LN_ORIG_DT", legacy.getOriginationDate())));
    account.setMaturityDate(required("LN_MAT_DT",
        parseDate("LN_MAT_DT", legacy.getMaturityDate())));
    account.setFirstPaymentDate(parseDate("LN_1ST_PMT_DT", legacy.getFirstPaymentDate()));
    account.setNextPaymentDate(parseDate("LN_NXT_PMT_DT", legacy.getNextPaymentDate()));
    account.setStatus(expandCode(LoanStatus.class, "LN_STAT_CD", legacy.getStatusCode()));
    account.setDelinquencyDays(parseInteger("LN_DLQ_DAYS", legacy.getDelinquencyDays()));
    account.setEscrowBalance(parseAmount("LN_ESCROW_BAL", legacy.getEscrowBalance()));
    account.setLtvPercent(parseDecimal("LN_LTV_PCT", legacy.getLtvPercent()));
    account.setPropertyAddress(legacy.getPropertyAddress());
    account.setPropertyCity(legacy.getPropertyCity());
    account.setPropertyState(legacy.getPropertyState());
    account.setPropertyZip(legacy.getPropertyZip());
    account.setPropertyType(expandCode(PropertyType.class, "PROP_TYP_CD",
        legacy.getPropertyType()));
    account.setAppraisedValue(parseAmount("PROP_APRS_VAL", legacy.getAppraisedValue()));
    account.setCreatedAt(parseTimestamp("LN_CRET_DT", legacy.getCreatedDate()));
    account.setUpdatedAt(parseTimestamp("LN_UPDT_DT", legacy.getUpdatedDate()));
    return account;
  }

  /** CDW_PMT_HIST → payments, with LN_ACCT_NBR already resolved to an entity. */
  public Payment toPayment(LegacyPayment legacy, LoanAccount loanAccount) {
    Payment payment = new Payment();
    payment.setExternalId(required("PMT_SEQ_NBR", legacy.getPaymentSequenceNumber()));
    payment.setLoanAccount(required("LN_ACCT_NBR", loanAccount));
    payment.setPaymentDate(required("PMT_DT", parseDate("PMT_DT", legacy.getPaymentDate())));
    payment.setTotalAmount(required("PMT_AMT", parseAmount("PMT_AMT", legacy.getTotalAmount())));
    payment.setPrincipalAmount(parseAmount("PMT_PRIN_AMT", legacy.getPrincipalAmount()));
    payment.setInterestAmount(parseAmount("PMT_INT_AMT", legacy.getInterestAmount()));
    payment.setEscrowAmount(parseAmount("PMT_ESCROW_AMT", legacy.getEscrowAmount()));
    payment.setLateFee(parseAmount("PMT_LATE_FEE", legacy.getLateFee()));
    payment.setType(required("PMT_TYP_CD",
        expandCode(PaymentType.class, "PMT_TYP_CD", legacy.getTypeCode())));
    payment.setStatus(required("PMT_STAT_CD",
        expandCode(PaymentStatus.class, "PMT_STAT_CD", legacy.getStatusCode())));
    payment.setReceivedDate(parseDate("PMT_RECV_DT", legacy.getReceivedDate()));
    payment.setProcessedDate(parseDate("PMT_PROC_DT", legacy.getProcessedDate()));
    payment.setCreatedAt(parseTimestamp("PMT_CRET_DT", legacy.getCreatedDate()));
    payment.setUpdatedAt(parseTimestamp("PMT_UPDT_DT", legacy.getUpdatedDate()));
    return payment;
  }

  /**
   * Expands a legacy short code to its modern value. Unrecognised codes are copied as-is (the
   * legacy API passed them through too) and logged.
   */
  private static <E extends Enum<E> & CodedValue> String expandCode(Class<E> type, String column,
      String code) {
    String text = trimToNull(code);
    if (text == null) {
      return null;
    }
    return CodedValue.fromLegacyCode(type, text)
        .map(CodedValue::value)
        .orElseGet(() -> {
          log.warn("Unrecognised {} code '{}'; copied as-is", column, text);
          return text;
        });
  }

  /** PROD_STAT_CD: ACT → true, INA → false. */
  private static Boolean toActiveFlag(String code) {
    String text = trimToNull(code);
    if (text == null) {
      return null;
    }
    return switch (text) {
      case "ACT" -> Boolean.TRUE;
      case "INA" -> Boolean.FALSE;
      default -> throw new LegacyDataException("PROD_STAT_CD has malformed value '" + code + "'");
    };
  }

  private static String required(String column, String value) {
    if (trimToNull(value) == null) {
      throw new LegacyDataException(column + " is required");
    }
    return value;
  }

  private static <T> T required(String column, T value) {
    if (value == null) {
      throw new LegacyDataException(column + " is required");
    }
    return value;
  }
}
