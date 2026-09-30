package com.workshop.loanservice.modern;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.modern.entity.Address;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.BorrowerRecordType;
import com.workshop.loanservice.modern.entity.BorrowerStatus;
import com.workshop.loanservice.modern.entity.EmploymentStatus;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.LoanStatus;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.entity.PaymentStatus;
import com.workshop.loanservice.modern.entity.PaymentType;
import com.workshop.loanservice.modern.entity.ProductType;
import com.workshop.loanservice.modern.entity.Property;
import com.workshop.loanservice.modern.entity.PropertyType;
import com.workshop.loanservice.modern.entity.RateType;
import com.workshop.loanservice.modern.repository.AddressRepository;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.modern.repository.PropertyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

/**
 * Maps the modern entities onto {@code schema-modern.sql} with Hibernate schema validation and
 * round-trips a full address → borrower → product → property → loan → payment graph.
 *
 * <p>The legacy schema is loaded alongside so that the legacy entities in the same persistence
 * unit also pass validation.
 */
@DataJpaTest
@TestPropertySource(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.sql.init.schema-locations="
          + "classpath:schema-legacy.sql,classpath:schema-modern.sql",
      "spring.sql.init.data-locations=classpath:data-modern-reference.sql"
    })
class ModernEntityMappingTest {

  private static final LocalDateTime MIGRATED_AT = LocalDateTime.of(2025, 12, 1, 0, 0);

  @Autowired private TestEntityManager entityManager;
  @Autowired private AddressRepository addressRepository;
  @Autowired private BorrowerRepository borrowerRepository;
  @Autowired private LoanProductRepository loanProductRepository;
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private LoanAccountRepository loanAccountRepository;
  @Autowired private PaymentRepository paymentRepository;

  private Long borrowerId;
  private Long loanAccountId;

  @BeforeEach
  void persistGraph() {
    Address address = new Address();
    address.setLine1("742 Elm Street");
    address.setLine2("Apt 3B");
    address.setCity("Springfield");
    address.setStateCode("IL");
    address.setPostalCode("62701");
    addressRepository.save(address);

    Borrower borrower = new Borrower();
    borrower.setLegacyBorrowerId("B-10001");
    borrower.setFirstName("James");
    borrower.setLastName("Mitchell");
    borrower.setMiddleInitial("R");
    borrower.setSsnEncrypted("ENC_XXX_001");
    borrower.setSsnLast4("0142");
    borrower.setDateOfBirth(LocalDate.of(1978, 3, 15));
    borrower.setMailingAddress(address);
    borrower.setPhoneNumber("217-555-0142");
    borrower.setEmailAddress("j.mitchell@email.com");
    borrower.setCreditScore((short) 745);
    borrower.setEmploymentStatus(reference(EmploymentStatus.class, "EMPLOYED"));
    borrower.setAnnualIncome(new BigDecimal("92500.00"));
    borrower.setStatus(reference(BorrowerStatus.class, "ACT"));
    borrower.setRecordType(reference(BorrowerRecordType.class, "PRI"));
    borrower.setCreatedAt(LocalDateTime.of(2019, 1, 15, 0, 0));
    borrower.setUpdatedAt(LocalDateTime.of(2025, 11, 3, 0, 0));
    borrowerRepository.save(borrower);

    LoanProduct product = new LoanProduct();
    product.setProductCode("FXD30");
    product.setDescription("30-Year Fixed Rate Mortgage");
    product.setProductType(reference(ProductType.class, "FXD"));
    product.setTermMonths((short) 360);
    product.setRateType(reference(RateType.class, "FIXED"));
    product.setMinAmount(new BigDecimal("50000.00"));
    product.setMaxAmount(new BigDecimal("1500000.00"));
    product.setEffectiveDate(LocalDate.of(2020, 1, 1));
    loanProductRepository.save(product);

    Property property = new Property();
    property.setAddress(address);
    property.setPropertyType(reference(PropertyType.class, "SFR"));
    property.setAppraisedValue(new BigDecimal("345000.00"));
    propertyRepository.save(property);

    LoanAccount loan = new LoanAccount();
    loan.setAccountNumber("LN-2019-00142");
    loan.setBorrower(borrower);
    loan.setProduct(product);
    loan.setProperty(property);
    loan.setOriginalAmount(new BigDecimal("285000.00"));
    loan.setCurrentBalance(new BigDecimal("271432.56"));
    loan.setInterestRate(new BigDecimal("4.750"));
    loan.setTermMonths((short) 360);
    loan.setMonthlyPaymentAmount(new BigDecimal("1487.02"));
    loan.setOriginationDate(LocalDate.of(2019, 2, 15));
    loan.setMaturityDate(LocalDate.of(2049, 2, 15));
    loan.setFirstPaymentDate(LocalDate.of(2019, 3, 15));
    loan.setNextPaymentDate(LocalDate.of(2026, 1, 15));
    loan.setStatus(reference(LoanStatus.class, "ACT"));
    loan.setEscrowBalance(new BigDecimal("3245.80"));
    loan.setLoanToValuePct(new BigDecimal("82.50"));
    loan.setCreatedAt(LocalDateTime.of(2019, 2, 1, 0, 0));
    loan.setUpdatedAt(MIGRATED_AT);
    loanAccountRepository.save(loan);

    // Chosen so that MM/dd/yyyy string ordering ("12/15/2024" > "11/15/2025") is wrong.
    paymentRepository.save(payment(loan, "PMT-2024120001", LocalDate.of(2024, 12, 15)));
    paymentRepository.save(payment(loan, "PMT-2025110001", LocalDate.of(2025, 11, 15)));
    paymentRepository.save(payment(loan, "PMT-2025010001", LocalDate.of(2025, 1, 15)));

    entityManager.flush();
    entityManager.clear();
    borrowerId = borrower.getId();
    loanAccountId = loan.getId();
  }

  @Test
  void referenceDataIsSeededWithLabelsAndFlags() {
    assertThat(entityManager.find(LoanStatus.class, "CLO").getLabel()).isEqualTo("Closed");
    assertThat(entityManager.find(LoanStatus.class, "CLO").isOpen()).isFalse();
    assertThat(entityManager.find(PaymentStatus.class, "PND").isFinalStatus()).isFalse();
    assertThat(entityManager.find(PropertyType.class, "SFR").getLabel())
        .isEqualTo("Single Family Residence");
  }

  @Test
  void borrowerRoundTripsWithTypedFieldsAndRelations() {
    Borrower borrower = borrowerRepository.findByLegacyBorrowerId("B-10001").orElseThrow();

    assertThat(borrower.getId()).isEqualTo(borrowerId);
    assertThat(borrower.getDateOfBirth()).isEqualTo(LocalDate.of(1978, 3, 15));
    assertThat(borrower.getCreditScore()).isEqualTo((short) 745);
    assertThat(borrower.getAnnualIncome()).isEqualByComparingTo("92500");
    assertThat(borrower.getSsnLast4()).isEqualTo("0142");
    assertThat(borrower.getMailingAddress().getCity()).isEqualTo("Springfield");
    assertThat(borrower.getMailingAddress().getCreatedAt()).isNotNull();
    assertThat(borrower.getEmploymentStatus().getLabel()).isEqualTo("Employed");
    assertThat(borrower.getStatus().getLabel()).isEqualTo("Active");
    assertThat(borrower.getRecordType().getLabel()).isEqualTo("Primary");
    assertThat(borrower.getLoanAccounts())
        .extracting(LoanAccount::getAccountNumber)
        .containsExactly("LN-2019-00142");
  }

  @Test
  void loanAccountRoundTripsWithTypedFieldsAndRelations() {
    LoanAccount loan = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();

    assertThat(loan.getId()).isEqualTo(loanAccountId);
    assertThat(loan.getInterestRate()).isEqualByComparingTo("4.750");
    assertThat(loan.getTermMonths()).isEqualTo((short) 360);
    assertThat(loan.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
    assertThat(loan.getDelinquencyDays()).isZero();
    assertThat(loan.getLoanToValuePct()).isEqualByComparingTo("82.50");
    assertThat(loan.getStatus().getLabel()).isEqualTo("Active");
    assertThat(loan.getStatus().isOpen()).isTrue();
    assertThat(loan.getBorrower().getLegacyBorrowerId()).isEqualTo("B-10001");
    assertThat(loan.getProduct().getProductType().getLabel()).isEqualTo("Fixed Rate");
    assertThat(loan.getProduct().getRateType().getCode()).isEqualTo("FIXED");
    assertThat(loan.getProduct().isActive()).isTrue();
    assertThat(loan.getProduct().getExpiryDate()).isNull();
    assertThat(loan.getProperty().getPropertyType().getLabel())
        .isEqualTo("Single Family Residence");
    assertThat(loan.getProperty().getAddress().getLine1()).isEqualTo("742 Elm Street");
  }

  @Test
  void finderMethodsResolveBusinessKeys() {
    assertThat(loanProductRepository.findByProductCode("FXD30")).isPresent();
    assertThat(loanAccountRepository.findByBorrowerId(borrowerId))
        .extracting(LoanAccount::getAccountNumber)
        .containsExactly("LN-2019-00142");
    Payment payment = paymentRepository.findByLegacyPaymentId("PMT-2025110001").orElseThrow();
    assertThat(payment.getTotalAmount()).isEqualByComparingTo("1487.02");
    assertThat(payment.getPaymentType().getLabel()).isEqualTo("Regular");
    assertThat(payment.getStatus().getLabel()).isEqualTo("Posted");
    assertThat(payment.getLoanAccount().getId()).isEqualTo(loanAccountId);
  }

  @Test
  void findAllWithBorrowerAndProductFetchesAssociations() {
    List<LoanAccount> loans = loanAccountRepository.findAllWithBorrowerAndProduct();

    assertThat(loans).hasSize(1);
    assertThat(Hibernate.isInitialized(loans.get(0).getBorrower())).isTrue();
    assertThat(Hibernate.isInitialized(loans.get(0).getProduct())).isTrue();
  }

  @Test
  void paymentsAreOrderedByRealDateNewestFirst() {
    List<LocalDate> expected =
        List.of(LocalDate.of(2025, 11, 15), LocalDate.of(2025, 1, 15), LocalDate.of(2024, 12, 15));

    assertThat(paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(loanAccountId))
        .extracting(Payment::getPaymentDate)
        .containsExactlyElementsOf(expected);
    assertThat(entityManager.find(LoanAccount.class, loanAccountId).getPayments())
        .extracting(Payment::getPaymentDate)
        .containsExactlyElementsOf(expected);
  }

  private <T> T reference(Class<T> type, String code) {
    return entityManager.getEntityManager().getReference(type, code);
  }

  private Payment payment(LoanAccount loan, String legacyId, LocalDate paymentDate) {
    Payment payment = new Payment();
    payment.setLegacyPaymentId(legacyId);
    payment.setLoanAccount(loan);
    payment.setPaymentDate(paymentDate);
    payment.setTotalAmount(new BigDecimal("1487.02"));
    payment.setPrincipalAmount(new BigDecimal("456.78"));
    payment.setInterestAmount(new BigDecimal("675.47"));
    payment.setEscrowAmount(new BigDecimal("354.77"));
    payment.setPaymentType(reference(PaymentType.class, "REG"));
    payment.setStatus(reference(PaymentStatus.class, "PST"));
    payment.setReceivedDate(paymentDate.minusDays(1));
    payment.setProcessedDate(paymentDate);
    payment.setCreatedAt(paymentDate.atStartOfDay());
    payment.setUpdatedAt(paymentDate.atStartOfDay());
    return payment;
  }
}
