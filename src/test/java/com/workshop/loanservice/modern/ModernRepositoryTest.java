package com.workshop.loanservice.modern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.BorrowerStatus;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.LoanStatus;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.entity.PaymentStatus;
import com.workshop.loanservice.modern.entity.PaymentType;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:modern-repository-test-legacy;DB_CLOSE_DELAY=-1",
            "app.datasource.modern.url=jdbc:h2:mem:modern-repository-test;DB_CLOSE_DELAY=-1",
            "app.migration.run-on-startup=false"
        })
@Transactional("modernTransactionManager")
class ModernRepositoryTest {

    @Autowired private BorrowerRepository borrowers;
    @Autowired private LoanProductRepository products;
    @Autowired private LoanAccountRepository loans;
    @Autowired private PaymentRepository payments;
    @Autowired private LegacyLoanAccountRepository legacyLoans;

    @PersistenceContext(unitName = "modern")
    private EntityManager modernEntityManager;

    @Test
    void modernSchemaStartsEmptyWhileLegacyKeepsItsData() {
        assertThat(borrowers.count()).isZero();
        assertThat(legacyLoans.count()).isEqualTo(5);
    }

    @Test
    void savesAndFindsAGraphByNaturalKeys() {
        Borrower borrower = borrowers.save(borrower("B-1"));
        LoanProduct product = products.save(product("P30"));
        LoanAccount loan = loans.save(loan("LN-1", borrower, product));
        payments.save(payment("PMT-2", loan, LocalDate.of(2026, 1, 1)));
        payments.save(payment("PMT-1", loan, LocalDate.of(2025, 12, 1)));
        flushAndClear();

        assertThat(borrowers.findByExternalId("B-1")).isPresent();
        assertThat(products.findByCode("P30")).isPresent();
        assertThat(loans.findByBorrowerExternalId("B-1"))
                .extracting(LoanAccount::getAccountNumber)
                .containsExactly("LN-1");
        LoanAccount found = loans.findByAccountNumber("LN-1").orElseThrow();
        assertThat(found.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(found.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(found.getProduct().getCode()).isEqualTo("P30");

        assertThat(paymentHistory("LN-1"))
                .extracting(Payment::getLegacyPaymentId)
                .containsExactly("PMT-2", "PMT-1");
        assertThat(payments.findByLegacyPaymentId("PMT-1")).isPresent();
    }

    @Test
    void borrowerWithoutLoansIsStoredAndHasNoLoanAccounts() {
        borrowers.save(borrower("B-NOLOAN"));
        flushAndClear();

        assertThat(borrowers.findByExternalId("B-NOLOAN")).isPresent();
        assertThat(loans.findByBorrowerExternalId("B-NOLOAN")).isEmpty();
    }

    @Test
    void borrowerCanHoldLoansOnSeveralProducts() {
        Borrower borrower = borrowers.save(borrower("B-MULTI"));
        LoanProduct fixed30 = products.save(product("P30"));
        LoanProduct fixed15 = products.save(product("P15"));
        loans.save(loan("LN-A", borrower, fixed30));
        loans.save(loan("LN-B", borrower, fixed15));
        loans.save(loan("LN-OTHER", borrowers.save(borrower("B-OTHER")), fixed30));
        flushAndClear();

        List<LoanAccount> borrowerLoans = loans.findByBorrowerExternalId("B-MULTI");
        assertThat(borrowerLoans)
                .extracting(LoanAccount::getAccountNumber)
                .containsExactlyInAnyOrder("LN-A", "LN-B");
        assertThat(borrowerLoans)
                .extracting(loan -> loan.getProduct().getCode())
                .containsExactlyInAnyOrder("P30", "P15");
    }

    @Test
    void missedMonthLeavesAGapInPaymentHistory() {
        LoanAccount loan = savedLoan("LN-GAP", "B-GAP");
        payments.save(payment("PMT-OCT", loan, LocalDate.of(2025, 10, 1)));
        payments.save(payment("PMT-DEC", loan, LocalDate.of(2025, 12, 1)));
        flushAndClear();

        List<Payment> history = paymentHistory("LN-GAP");
        assertThat(history)
                .extracting(Payment::getPaymentDate)
                .containsExactly(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 10, 1));
        assertThat(history).noneMatch(p -> p.getPaymentDate().getMonth() == Month.NOVEMBER);
    }

    @Test
    void duplicateLegacyPaymentIdIsRejected() {
        LoanAccount loan = savedLoan("LN-DUP", "B-DUP");
        payments.saveAndFlush(payment("PMT-SAME", loan, LocalDate.of(2025, 11, 1)));
        Payment duplicate = payment("PMT-SAME", loan, LocalDate.of(2025, 12, 1));

        assertThatThrownBy(() -> payments.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paymentForUnknownLoanAccountIsRejectedByForeignKey() {
        String insertOrphanPayment =
                "INSERT INTO payments (loan_account_id, payment_date, total_amount, type, status)"
                        + " VALUES (999999, DATE '2025-12-01', 100.00, 'REGULAR', 'POSTED')";

        assertThatThrownBy(
                () -> modernEntityManager.createNativeQuery(insertOrphanPayment).executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    private LoanAccount savedLoan(String accountNumber, String borrowerExternalId) {
        Borrower borrower = borrowers.save(borrower(borrowerExternalId));
        LoanProduct product = products.save(product("P30"));
        return loans.save(loan(accountNumber, borrower, product));
    }

    private List<Payment> paymentHistory(String accountNumber) {
        return payments.findByLoanAccountAccountNumberOrderByPaymentDateDesc(accountNumber);
    }

    private void flushAndClear() {
        modernEntityManager.flush();
        modernEntityManager.clear();
    }

    private static Borrower borrower(String externalId) {
        Borrower borrower = new Borrower();
        borrower.setExternalId(externalId);
        borrower.setFirstName("Ada");
        borrower.setLastName("Lovelace");
        borrower.setDateOfBirth(LocalDate.of(1980, 12, 10));
        borrower.setCreditScore(780);
        borrower.setAnnualIncome(new BigDecimal("125000.00"));
        borrower.setStatus(BorrowerStatus.ACTIVE);
        return borrower;
    }

    private static LoanProduct product(String code) {
        LoanProduct product = new LoanProduct();
        product.setCode(code);
        product.setName("30-Year Fixed");
        product.setType("PRI");
        product.setTermMonths(360);
        product.setRateType("FXD");
        product.setActive(true);
        return product;
    }

    private static LoanAccount loan(String accountNumber, Borrower borrower, LoanProduct product) {
        LoanAccount loan = new LoanAccount();
        loan.setAccountNumber(accountNumber);
        loan.setBorrower(borrower);
        loan.setProduct(product);
        loan.setOriginalAmount(new BigDecimal("285000.00"));
        loan.setCurrentBalance(new BigDecimal("250000.00"));
        loan.setInterestRate(new BigDecimal("6.125"));
        loan.setTermMonths(360);
        loan.setMonthlyPayment(new BigDecimal("1731.68"));
        loan.setOriginationDate(LocalDate.of(2019, 3, 15));
        loan.setMaturityDate(LocalDate.of(2049, 4, 1));
        loan.setStatus(LoanStatus.ACTIVE);
        return loan;
    }

    private static Payment payment(String legacyId, LoanAccount loan, LocalDate date) {
        Payment payment = new Payment();
        payment.setLegacyPaymentId(legacyId);
        payment.setLoanAccount(loan);
        payment.setPaymentDate(date);
        payment.setTotalAmount(new BigDecimal("2100.00"));
        payment.setType(PaymentType.REGULAR);
        payment.setStatus(PaymentStatus.POSTED);
        return payment;
    }
}
