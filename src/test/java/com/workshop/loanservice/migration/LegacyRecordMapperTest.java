package com.workshop.loanservice.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class LegacyRecordMapperTest {

  private final LegacyRecordMapper mapper = new LegacyRecordMapper();

  @Test
  void mapsBorrowerPerColumnMappings() {
    Borrower borrower = mapper.toBorrower(legacyBorrower());

    assertEquals("B-10001", borrower.getExternalId());
    assertEquals("R", borrower.getMiddleInitial());
    assertEquals("ENC_XXX_001", borrower.getSsnHash());
    assertEquals(LocalDate.of(1978, 3, 15), borrower.getDateOfBirth());
    assertEquals(745, borrower.getCreditScore());
    assertEquals(new BigDecimal("92500"), borrower.getAnnualIncome());
    assertEquals(LocalDateTime.of(2019, 1, 15, 0, 0), borrower.getCreatedAt());
    assertEquals("ACTIVE", borrower.getStatus());
  }

  @Test
  void expandsInactiveBorrowerStatus() {
    LegacyBorrower legacy = legacyBorrower();
    legacy.setStatusCode("INA");
    assertEquals("INACTIVE", mapper.toBorrower(legacy).getStatus());
  }

  @Test
  void blankOptionalBorrowerFieldsBecomeNull() {
    LegacyBorrower legacy = legacyBorrower();
    legacy.setDateOfBirth(" ");
    legacy.setCreditScore("");
    legacy.setStatusCode(null);

    Borrower borrower = mapper.toBorrower(legacy);

    assertNull(borrower.getDateOfBirth());
    assertNull(borrower.getCreditScore());
    assertNull(borrower.getStatus());
  }

  @Test
  void rejectsBorrowerWithoutRequiredName() {
    LegacyBorrower legacy = legacyBorrower();
    legacy.setLastName(" ");
    LegacyDataException e = assertThrows(LegacyDataException.class,
        () -> mapper.toBorrower(legacy));
    assertEquals("BORR_LST_NM is required", e.getMessage());
  }

  @Test
  void rejectsBorrowerWithMalformedDate() {
    LegacyBorrower legacy = legacyBorrower();
    legacy.setDateOfBirth("13/45/1978");
    assertThrows(LegacyDataException.class, () -> mapper.toBorrower(legacy));
  }

  @Test
  void mapsProductStatusToActiveFlag() {
    LegacyLoanProduct legacy = legacyProduct();
    LoanProduct product = mapper.toLoanProduct(legacy);
    assertTrue(product.getActive());
    assertEquals(360, product.getTermMonths());
    assertEquals(new BigDecimal("1500000"), product.getMaxAmount());
    assertEquals(LocalDate.of(2099, 12, 31), product.getExpirationDate());

    legacy.setStatusCode("INA");
    assertFalse(mapper.toLoanProduct(legacy).getActive());

    legacy.setStatusCode("XYZ");
    assertThrows(LegacyDataException.class, () -> mapper.toLoanProduct(legacy));
  }

  @Test
  void mapsLoanAccountWithResolvedForeignKeys() {
    Borrower borrower = new Borrower();
    LoanProduct product = new LoanProduct();

    LoanAccount account = mapper.toLoanAccount(legacyLoanAccount(), borrower, product);

    assertSame(borrower, account.getBorrower());
    assertSame(product, account.getProduct());
    assertEquals(new BigDecimal("285000"), account.getOriginalAmount());
    assertEquals(new BigDecimal("271432.56"), account.getCurrentBalance());
    assertEquals(new BigDecimal("4.750"), account.getInterestRate());
    assertEquals(new BigDecimal("82.5"), account.getLtvPercent());
    assertEquals(LocalDate.of(2019, 2, 15), account.getOriginationDate());
    assertEquals(0, account.getDelinquencyDays());
    assertEquals("ACTIVE", account.getStatus());
    assertEquals("Single Family", account.getPropertyType());
  }

  @Test
  void expandsAllLoanStatusAndPropertyTypeCodes() {
    String[][] loanStatuses = {
        {"ACT", "ACTIVE"}, {"CLO", "CLOSED"}, {"DFT", "DEFAULT"}, {"FRB", "FORBEARANCE"}};
    String[][] propertyTypes = {
        {"SFR", "Single Family"}, {"CND", "Condominium"}, {"MFR", "Multi-Family"},
        {"TWN", "Townhouse"}};
    LegacyLoanAccount legacy = legacyLoanAccount();
    for (String[] pair : loanStatuses) {
      legacy.setStatusCode(pair[0]);
      assertEquals(pair[1], mapLoan(legacy).getStatus());
    }
    for (String[] pair : propertyTypes) {
      legacy.setPropertyType(pair[0]);
      assertEquals(pair[1], mapLoan(legacy).getPropertyType());
    }
  }

  @Test
  void unknownCodesAreCopiedAsIs() {
    LegacyLoanAccount legacy = legacyLoanAccount();
    legacy.setStatusCode("XYZ");
    assertEquals("XYZ", mapLoan(legacy).getStatus());
  }

  @Test
  void rejectsLoanAccountWithUnresolvedBorrower() {
    LegacyDataException e = assertThrows(LegacyDataException.class,
        () -> mapper.toLoanAccount(legacyLoanAccount(), null, new LoanProduct()));
    assertEquals("BORR_ID is required", e.getMessage());
  }

  @Test
  void rejectsLoanAccountWithMissingRequiredAmount() {
    LegacyLoanAccount legacy = legacyLoanAccount();
    legacy.setOriginalAmount("");
    assertThrows(LegacyDataException.class, () -> mapLoan(legacy));
  }

  @Test
  void mapsPaymentAndExpandsCodes() {
    LoanAccount account = new LoanAccount();

    Payment payment = mapper.toPayment(legacyPayment(), account);

    assertEquals("PMT-2025110003", payment.getExternalId());
    assertSame(account, payment.getLoanAccount());
    assertEquals(LocalDate.of(2025, 11, 1), payment.getPaymentDate());
    assertEquals(new BigDecimal("1077.05"), payment.getTotalAmount());
    assertEquals(new BigDecimal("47.50"), payment.getLateFee());
    assertEquals("REGULAR", payment.getType());
    assertEquals("POSTED", payment.getStatus());
    assertEquals(LocalDate.of(2025, 11, 19), payment.getProcessedDate());
  }

  @Test
  void expandsAllPaymentTypeAndStatusCodes() {
    String[][] types = {
        {"REG", "REGULAR"}, {"EXT", "EXTRA"}, {"PRT", "PARTIAL"}, {"PRE", "PREPAYMENT"}};
    String[][] statuses = {
        {"PST", "POSTED"}, {"REV", "REVERSED"}, {"NSF", "NSF"}, {"PND", "PENDING"}};
    LegacyPayment legacy = legacyPayment();
    for (String[] pair : types) {
      legacy.setTypeCode(pair[0]);
      assertEquals(pair[1], mapper.toPayment(legacy, new LoanAccount()).getType());
    }
    for (String[] pair : statuses) {
      legacy.setStatusCode(pair[0]);
      assertEquals(pair[1], mapper.toPayment(legacy, new LoanAccount()).getStatus());
    }
  }

  @Test
  void rejectsPaymentWithoutRequiredType() {
    LegacyPayment legacy = legacyPayment();
    legacy.setTypeCode(null);
    assertThrows(LegacyDataException.class, () -> mapper.toPayment(legacy, new LoanAccount()));
  }

  private LoanAccount mapLoan(LegacyLoanAccount legacy) {
    return mapper.toLoanAccount(legacy, new Borrower(), new LoanProduct());
  }

  private static LegacyBorrower legacyBorrower() {
    LegacyBorrower legacy = new LegacyBorrower();
    legacy.setBorrowerId("B-10001");
    legacy.setFirstName("James");
    legacy.setLastName("Mitchell");
    legacy.setMiddleInitial("R");
    legacy.setSsnEncrypted("ENC_XXX_001");
    legacy.setDateOfBirth("03/15/1978");
    legacy.setCreditScore("745");
    legacy.setAnnualIncome("92,500");
    legacy.setCreatedDate("01/15/2019");
    legacy.setUpdatedDate("11/03/2025");
    legacy.setStatusCode("ACT");
    legacy.setRecordType("PRI");
    return legacy;
  }

  private static LegacyLoanProduct legacyProduct() {
    LegacyLoanProduct legacy = new LegacyLoanProduct();
    legacy.setProductCode("FXD30");
    legacy.setDescription("30-Year Fixed Rate Mortgage");
    legacy.setTypeCode("FXD");
    legacy.setTermMonths("360");
    legacy.setRateType("FIXED");
    legacy.setMinAmount("50,000");
    legacy.setMaxAmount("1,500,000");
    legacy.setStatusCode("ACT");
    legacy.setEffectiveDate("01/01/2020");
    legacy.setExpirationDate("12/31/2099");
    return legacy;
  }

  private static LegacyLoanAccount legacyLoanAccount() {
    LegacyLoanAccount legacy = new LegacyLoanAccount();
    legacy.setLoanAccountNumber("LN-2019-00142");
    legacy.setBorrowerId("B-10001");
    legacy.setProductCode("FXD30");
    legacy.setOriginalAmount("285,000");
    legacy.setCurrentBalance("271,432.56");
    legacy.setInterestRate("4.750");
    legacy.setTermMonths("360");
    legacy.setMonthlyPayment("1,487.02");
    legacy.setOriginationDate("02/15/2019");
    legacy.setMaturityDate("02/15/2049");
    legacy.setStatusCode("ACT");
    legacy.setDelinquencyDays("0");
    legacy.setLtvPercent("82.5");
    legacy.setPropertyType("SFR");
    return legacy;
  }

  private static LegacyPayment legacyPayment() {
    LegacyPayment legacy = new LegacyPayment();
    legacy.setPaymentSequenceNumber("PMT-2025110003");
    legacy.setLoanAccountNumber("LN-2018-00089");
    legacy.setPaymentDate("11/01/2025");
    legacy.setTotalAmount("1,077.05");
    legacy.setLateFee("47.50");
    legacy.setTypeCode("REG");
    legacy.setStatusCode("PST");
    legacy.setProcessedDate("11/19/2025");
    return legacy;
  }
}
