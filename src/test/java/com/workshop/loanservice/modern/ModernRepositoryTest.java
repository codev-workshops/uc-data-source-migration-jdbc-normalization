package com.workshop.loanservice.modern;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
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
        modernEntityManager.flush();
        modernEntityManager.clear();

        assertThat(borrowers.findByExternalId("B-1")).isPresent();
        assertThat(products.findByCode("P30")).isPresent();
        assertThat(loans.findByBorrowerExternalId("B-1"))
                .extracting(LoanAccount::getAccountNumber)
                .containsExactly("LN-1");
        LoanAccount found = loans.findByAccountNumber("LN-1").orElseThrow();
        assertThat(found.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(found.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(found.getProduct().getCode()).isEqualTo("P30");

        List<Payment> history = payments.findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN-1");
        assertThat(history).extracting(Payment::getLegacyPaymentId).containsExactly("PMT-2", "PMT-1");
        assertThat(payments.findByLegacyPaymentId("PMT-1")).isPresent();
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
