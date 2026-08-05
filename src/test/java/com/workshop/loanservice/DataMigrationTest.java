package com.workshop.loanservice;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the startup migration populated the modern tables with the
 * correct row counts, properly-typed values, resolved foreign keys, and
 * expanded enum codes.
 */
@SpringBootTest
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:loansvc-${random.uuid};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class DataMigrationTest {

    @Autowired private BorrowerRepository borrowerRepository;
    @Autowired private LoanProductRepository loanProductRepository;
    @Autowired private LoanAccountRepository loanAccountRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Test
    void rowCountsMatchLegacySeed() {
        assertThat(borrowerRepository.count()).isEqualTo(5);
        assertThat(loanProductRepository.count()).isEqualTo(5);
        assertThat(loanAccountRepository.count()).isEqualTo(5);
        assertThat(paymentRepository.count()).isEqualTo(10);
    }

    @Test
    void borrowerFieldsAreProperlyTyped() {
        Borrower b = borrowerRepository.findByExternalId("B-10001").orElseThrow();
        assertThat(b.getFirstName()).isEqualTo("James");
        assertThat(b.getMiddleInitial()).isEqualTo("R");
        assertThat(b.getCreditScore()).isEqualTo(745);                       // string -> Integer
        assertThat(b.getAnnualIncome()).isEqualByComparingTo("92500");        // "92,500" -> decimal
        assertThat(b.getDateOfBirth()).isEqualTo(LocalDate.of(1978, 3, 15));  // MM/DD/YYYY -> LocalDate
        assertThat(b.getStatus()).isEqualTo("ACTIVE");                        // ACT -> ACTIVE
        assertThat(b.getCreatedAt()).isEqualTo(LocalDate.of(2019, 1, 15).atStartOfDay());
    }

    @Test
    void nullMiddleInitialIsPreserved() {
        Borrower b = borrowerRepository.findByExternalId("B-10005").orElseThrow();
        assertThat(b.getMiddleInitial()).isNull();
    }

    @Test
    void productBooleanAndTypesAreConverted() {
        LoanProduct p = loanProductRepository.findByCode("FXD30").orElseThrow();
        assertThat(p.getActive()).isTrue();                 // ACT -> true
        assertThat(p.getTermMonths()).isEqualTo(360);       // string -> Integer
        assertThat(p.getMaxAmount()).isEqualByComparingTo("1500000"); // "1,500,000" -> decimal
    }

    @Test
    void loanAccountResolvesForeignKeysAndTypes() {
        LoanAccount a = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertThat(a.getBorrower().getExternalId()).isEqualTo("B-10001");  // BORR_ID -> FK
        assertThat(a.getProduct().getCode()).isEqualTo("FXD30");           // PROD_CD -> FK
        assertThat(a.getOriginalAmount()).isEqualByComparingTo("285000");  // "285,000" -> decimal
        assertThat(a.getInterestRate()).isEqualByComparingTo(new BigDecimal("4.750"));
        assertThat(a.getStatus()).isEqualTo("ACTIVE");                     // ACT -> ACTIVE
        assertThat(a.getPropertyType()).isEqualTo("Single Family Residence"); // SFR expanded
        assertThat(a.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
    }

    @Test
    void paymentsAreOrderedAndTyped() {
        List<Payment> payments =
                paymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc("LN-2019-00142");
        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).getExternalId()).isEqualTo("PMT-2025120001"); // legacy id preserved
        assertThat(payments.get(0).getPaymentDate()).isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(payments.get(1).getPaymentDate()).isEqualTo(LocalDate.of(2025, 11, 15));
        assertThat(payments.get(0).getType()).isEqualTo("REGULAR");    // REG -> REGULAR
        assertThat(payments.get(0).getStatus()).isEqualTo("POSTED");   // PST -> POSTED
        assertThat(payments.get(0).getLoanAccount().getAccountNumber()).isEqualTo("LN-2019-00142");
    }
}
