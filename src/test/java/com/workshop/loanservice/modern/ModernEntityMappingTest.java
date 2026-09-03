package com.workshop.loanservice.modern;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the modern JPA model in {@code com.workshop.loanservice.modern} matches the
 * target DDL. The test boots an isolated in-memory H2 database from the modern schema and
 * reference-data scripts, and runs Hibernate with {@code hbm2ddl.auto=validate} so any
 * column/type mismatch between an entity and the DDL fails the context start-up.
 *
 * <p>Only the modern entities and repositories are scanned here; the legacy {@code CDW_*}
 * model has no tables in this database and would fail validation.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:modern-mapping-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:modern/schema-modern-test.sql",
        "spring.sql.init.data-locations=classpath:modern/data-modern-reference-test.sql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = Address.class)
@EnableJpaRepositories(basePackageClasses = AddressRepository.class)
class ModernEntityMappingTest {

    @Autowired private TestEntityManager em;
    @Autowired private AddressRepository addressRepository;
    @Autowired private BorrowerRepository borrowerRepository;
    @Autowired private LoanProductRepository loanProductRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private LoanAccountRepository loanAccountRepository;
    @Autowired private PaymentRepository paymentRepository;

    private static final LocalDateTime AUDIT_TS = LocalDateTime.of(2019, 2, 15, 0, 0);

    private Borrower borrower;
    private LoanProduct product;
    private LoanAccount loan;

    @BeforeEach
    void persistGraph() {
        Address address = new Address();
        address.setLine1("123 Main St");
        address.setLine2("Apt 4");
        address.setCity("Springfield");
        address.setStateCode("IL");
        address.setPostalCode("62701");
        address = addressRepository.save(address);

        borrower = new Borrower();
        borrower.setLegacyBorrowerId("B-10001");
        borrower.setFirstName("John");
        borrower.setLastName("Smith");
        borrower.setMiddleInitial("A");
        borrower.setSsnEncrypted("ENC(abc123)");
        borrower.setSsnLast4("6789");
        borrower.setDateOfBirth(LocalDate.of(1975, 3, 22));
        borrower.setMailingAddress(address);
        borrower.setPhoneNumber("555-0100");
        borrower.setEmailAddress("john.smith@example.com");
        borrower.setCreditScore((short) 742);
        borrower.setEmploymentStatus(em.find(EmploymentStatus.class, "EMPLOYED"));
        borrower.setAnnualIncome(new BigDecimal("85000.00"));
        borrower.setStatus(em.find(BorrowerStatus.class, "ACT"));
        borrower.setRecordType(em.find(BorrowerRecordType.class, "PRI"));
        borrower.setCreatedAt(AUDIT_TS);
        borrower.setUpdatedAt(AUDIT_TS);
        borrower = borrowerRepository.save(borrower);

        product = new LoanProduct();
        product.setProductCode("FXD30");
        product.setDescription("30-Year Fixed Rate Mortgage");
        product.setProductType(em.find(ProductType.class, "FXD"));
        product.setTermMonths((short) 360);
        product.setRateType(em.find(RateType.class, "FIXED"));
        product.setMinAmount(new BigDecimal("50000.00"));
        product.setMaxAmount(new BigDecimal("1500000.00"));
        product.setIsActive(true);
        product.setEffectiveDate(LocalDate.of(2015, 1, 1));
        product.setExpiryDate(null);
        product = loanProductRepository.save(product);

        Property property = new Property();
        property.setAddress(address);
        property.setPropertyType(em.find(PropertyType.class, "SFR"));
        property.setAppraisedValue(new BigDecimal("400000.00"));
        property = propertyRepository.save(property);

        loan = new LoanAccount();
        loan.setAccountNumber("LN-2019-00142");
        loan.setBorrower(borrower);
        loan.setProduct(product);
        loan.setProperty(property);
        loan.setOriginalAmount(new BigDecimal("330000.00"));
        loan.setCurrentBalance(new BigDecimal("298456.78"));
        loan.setInterestRate(new BigDecimal("4.750"));
        loan.setTermMonths((short) 360);
        loan.setMonthlyPaymentAmount(new BigDecimal("1721.45"));
        loan.setOriginationDate(LocalDate.of(2019, 2, 15));
        loan.setMaturityDate(LocalDate.of(2049, 2, 15));
        loan.setFirstPaymentDate(LocalDate.of(2019, 4, 1));
        loan.setNextPaymentDate(LocalDate.of(2026, 1, 1));
        loan.setStatus(em.find(LoanStatus.class, "ACT"));
        loan.setDelinquencyDays(0);
        loan.setEscrowBalance(new BigDecimal("3200.50"));
        loan.setLoanToValuePct(new BigDecimal("82.50"));
        loan.setCreatedAt(AUDIT_TS);
        loan.setUpdatedAt(AUDIT_TS);
        loan = loanAccountRepository.save(loan);

        // Deliberately inserted out of order and across a year boundary: a string sort of
        // MM/dd/yyyy would put 12/01/2025 after 01/01/2026, a real DATE sort must not.
        paymentRepository.save(payment("PMT-2025120001", LocalDate.of(2025, 12, 1)));
        paymentRepository.save(payment("PMT-2026010001", LocalDate.of(2026, 1, 1)));
        paymentRepository.save(payment("PMT-2025110001", LocalDate.of(2025, 11, 1)));

        em.flush();
        em.clear();
    }

    private Payment payment(String legacyId, LocalDate paymentDate) {
        Payment p = new Payment();
        p.setLegacyPaymentId(legacyId);
        p.setLoanAccount(loan);
        p.setPaymentDate(paymentDate);
        p.setPrincipalAmount(new BigDecimal("540.20"));
        p.setInterestAmount(new BigDecimal("1181.25"));
        p.setEscrowAmount(new BigDecimal("0.00"));
        p.setTotalAmount(new BigDecimal("1721.45"));
        p.setLateFeeAmount(new BigDecimal("0.00"));
        p.setPaymentType(em.find(PaymentType.class, "REG"));
        p.setStatus(em.find(PaymentStatus.class, "PST"));
        p.setReceivedDate(paymentDate.minusDays(2));
        p.setProcessedDate(paymentDate.minusDays(1));
        p.setCreatedAt(paymentDate.atStartOfDay());
        p.setUpdatedAt(paymentDate.atStartOfDay());
        return p;
    }

    @Test
    void schemaValidationPassesAndReferenceDataIsSeeded() {
        assertThat(em.find(LoanStatus.class, "CLO").getIsOpen()).isFalse();
        assertThat(em.find(LoanStatus.class, "ACT").getIsOpen()).isTrue();
        assertThat(em.find(PaymentStatus.class, "PND").getIsFinal()).isFalse();
        assertThat(em.find(PropertyType.class, "SFR").getLabel()).isEqualTo("Single Family Residence");
        assertThat(em.find(EmploymentStatus.class, "SELF_EMPLOYED").getLabel()).isEqualTo("Self-Employed");
    }

    @Test
    void fullGraphRoundTripsWithRealTypesAndRelationships() {
        LoanAccount found = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();

        assertThat(found.getId()).isNotNull().isEqualTo(loan.getId());
        assertThat(found.getOriginalAmount()).isEqualByComparingTo("330000.00");
        assertThat(found.getCurrentBalance()).isEqualByComparingTo("298456.78");
        assertThat(found.getInterestRate()).isEqualByComparingTo("4.750");
        assertThat(found.getInterestRate().scale()).isEqualTo(3);
        assertThat(found.getTermMonths()).isEqualTo((short) 360);
        assertThat(found.getDelinquencyDays()).isZero();
        assertThat(found.getLoanToValuePct()).isEqualByComparingTo("82.50");
        assertThat(found.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(found.getMaturityDate()).isEqualTo(LocalDate.of(2049, 2, 15));
        assertThat(found.getNextPaymentDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(found.getCreatedAt()).isEqualTo(AUDIT_TS);
        assertThat(found.getStatus().getCode()).isEqualTo("ACT");
        assertThat(found.getStatus().getLabel()).isEqualTo("Active");

        Borrower b = found.getBorrower();
        assertThat(b.getId()).isEqualTo(borrower.getId());
        assertThat(b.getLegacyBorrowerId()).isEqualTo("B-10001");
        assertThat(b.getDateOfBirth()).isEqualTo(LocalDate.of(1975, 3, 22));
        assertThat(b.getCreditScore()).isEqualTo((short) 742);
        assertThat(b.getAnnualIncome()).isEqualByComparingTo("85000.00");
        assertThat(b.getSsnLast4()).isEqualTo("6789");
        assertThat(b.getMiddleInitial()).isEqualTo("A");
        assertThat(b.getEmploymentStatus().getLabel()).isEqualTo("Employed");
        assertThat(b.getStatus().getLabel()).isEqualTo("Active");
        assertThat(b.getRecordType().getLabel()).isEqualTo("Primary");
        assertThat(b.getMailingAddress().getStateCode()).isEqualTo("IL");
        assertThat(b.getMailingAddress().getCreatedAt()).isNotNull();

        LoanProduct p = found.getProduct();
        assertThat(p.getId()).isEqualTo(product.getId());
        assertThat(p.getProductCode()).isEqualTo("FXD30");
        assertThat(p.getTermMonths()).isEqualTo((short) 360);
        assertThat(p.getIsActive()).isTrue();
        assertThat(p.getEffectiveDate()).isEqualTo(LocalDate.of(2015, 1, 1));
        assertThat(p.getExpiryDate()).isNull();
        assertThat(p.getProductType().getLabel()).isEqualTo("Fixed Rate");
        assertThat(p.getRateType().getLabel()).isEqualTo("Fixed");

        Property prop = found.getProperty();
        assertThat(prop.getAppraisedValue()).isEqualByComparingTo("400000.00");
        assertThat(prop.getPropertyType().getLabel()).isEqualTo("Single Family Residence");
        assertThat(prop.getAddress().getId()).isEqualTo(b.getMailingAddress().getId());
        assertThat(prop.getAddress().getLine1()).isEqualTo("123 Main St");
    }

    @Test
    void borrowerAndProductFindersResolveLegacyBusinessKeys() {
        assertThat(borrowerRepository.findByLegacyBorrowerId("B-10001"))
                .get().extracting(Borrower::getLastName).isEqualTo("Smith");
        assertThat(borrowerRepository.findByLegacyBorrowerId("B-99999")).isEmpty();

        assertThat(loanProductRepository.findByProductCode("FXD30"))
                .get().extracting(LoanProduct::getDescription).isEqualTo("30-Year Fixed Rate Mortgage");
        assertThat(loanProductRepository.findByProductCode("NOPE")).isEmpty();
    }

    @Test
    void loanAccountFindersByBorrowerAndWithJoinFetch() {
        List<LoanAccount> byBorrower = loanAccountRepository.findByBorrowerId(borrower.getId());
        assertThat(byBorrower).extracting(LoanAccount::getAccountNumber).containsExactly("LN-2019-00142");
        assertThat(loanAccountRepository.findByBorrowerId(-1L)).isEmpty();

        List<LoanAccount> all = loanAccountRepository.findAllWithBorrowerAndProduct();
        assertThat(all).hasSize(1);
        LoanAccount l = all.get(0);
        assertThat(em.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(l, "borrower")).isTrue();
        assertThat(em.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(l, "product")).isTrue();
        assertThat(em.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(l, "property")).isFalse();
        assertThat(l.getBorrower().getFirstName() + " " + l.getBorrower().getLastName()).isEqualTo("John Smith");
        assertThat(l.getProduct().getProductCode()).isEqualTo("FXD30");
    }

    @Test
    void paymentsAreOrderedByRealDateDescendingAcrossYearBoundary() {
        List<Payment> payments = paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(loan.getId());

        assertThat(payments).extracting(Payment::getPaymentDate).containsExactly(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2025, 12, 1),
                LocalDate.of(2025, 11, 1));
        assertThat(payments).extracting(Payment::getLegacyPaymentId)
                .containsExactly("PMT-2026010001", "PMT-2025120001", "PMT-2025110001");

        Payment newest = payments.get(0);
        assertThat(newest.getTotalAmount()).isEqualByComparingTo("1721.45");
        assertThat(newest.getPrincipalAmount().add(newest.getInterestAmount()).add(newest.getEscrowAmount()))
                .isEqualByComparingTo(newest.getTotalAmount());
        assertThat(newest.getLateFeeAmount()).isEqualByComparingTo("0.00");
        assertThat(newest.getReceivedDate()).isEqualTo(LocalDate.of(2025, 12, 30));
        assertThat(newest.getProcessedDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(newest.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(newest.getPaymentType().getLabel()).isEqualTo("Regular");
        assertThat(newest.getStatus().getLabel()).isEqualTo("Posted");
        assertThat(newest.getLoanAccount().getAccountNumber()).isEqualTo("LN-2019-00142");

        assertThat(paymentRepository.findByLegacyPaymentId("PMT-2025110001"))
                .get().extracting(Payment::getPaymentDate).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(paymentRepository.findByLegacyPaymentId("PMT-0")).isEmpty();
        assertThat(paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(-1L)).isEmpty();
    }
}
