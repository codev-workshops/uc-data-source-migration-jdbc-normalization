package com.workshop.loanservice;

import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the application's data actually lives in — and is queried from —
 * the modern normalized tables.
 */
@SpringBootTest
@Transactional(readOnly = true)
class ModernSchemaPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private BorrowerRepository borrowerRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private LoanAccountRepository loanAccountRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void modernTablesAreInitialisedAndLegacyTablesAreAbsent() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'PUBLIC'", String.class);

        assertThat(tables).contains("BORROWERS", "LOAN_PRODUCTS", "LOAN_ACCOUNTS", "PAYMENTS");
        assertThat(tables).doesNotContain("CDW_BORR_MSTR", "CDW_LN_PROD", "CDW_LN_ACCT", "CDW_PMT_HIST");
    }

    @Test
    void rowCountsMatchTheLegacyDataSet() {
        assertThat(borrowerRepository.count()).isEqualTo(5);
        assertThat(loanProductRepository.count()).isEqualTo(5);
        assertThat(loanAccountRepository.count()).isEqualTo(5);
        assertThat(paymentRepository.count()).isEqualTo(10);
    }

    @Test
    void loanAccountIsTypedAndLinkedToBorrowerAndProduct() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();

        assertThat(account.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(account.getBorrower().getLastName()).isEqualTo("Mitchell");
        assertThat(account.getProduct().getCode()).isEqualTo("FXD30");
        assertThat(account.getProduct().getName()).isEqualTo("30-Year Fixed Rate Mortgage");
        assertThat(account.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("271432.56");
        assertThat(account.getInterestRate()).isEqualByComparingTo("4.750");
        assertThat(account.getTermMonths()).isEqualTo(360);
        assertThat(account.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(account.getMaturityDate()).isEqualTo(LocalDate.of(2049, 2, 15));
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getDelinquencyDays()).isZero();
        assertThat(account.getAppraisedValue()).isEqualByComparingTo("345000");
    }

    @Test
    void borrowerOwnsItsLoanAccounts() {
        List<LoanAccount> accounts = loanAccountRepository.findByBorrowerExternalIdWithProduct("B-10003");

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getAccountNumber()).isEqualTo("LN-2018-00089");
        assertThat(accounts.get(0).getProduct().getCode()).isEqualTo("ARM51");
        assertThat(borrowerRepository.findByExternalId("B-10003").orElseThrow().getCreditScore()).isEqualTo(692);
    }

    @Test
    void nullableLegacyFieldsRemainNull() {
        assertThat(borrowerRepository.findByExternalId("B-10005").orElseThrow().getMiddleInitial()).isNull();
        assertThat(borrowerRepository.findByExternalId("B-10002").orElseThrow().getAddressLine2()).isNull();
    }

    @Test
    void paymentsAreLinkedToTheirLoanAndOrderedNewestFirst() {
        List<Payment> payments =
                paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN-2019-00142");

        assertThat(payments).extracting(Payment::getExternalId)
                .containsExactly("PMT-2025120001", "PMT-2025110001");
        assertThat(payments).allSatisfy(p ->
                assertThat(p.getLoanAccount().getAccountNumber()).isEqualTo("LN-2019-00142"));
        assertThat(payments.get(0).getPaymentDate()).isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(payments.get(0).getTotalAmount()).isEqualByComparingTo("1487.02");
        assertThat(payments.get(0).getType()).isEqualTo("REGULAR");
        assertThat(payments.get(0).getStatus()).isEqualTo("POSTED");
        assertThat(paymentRepository.findByLoanAccountAccountNumber("LN-2018-00089"))
                .filteredOn(p -> p.getExternalId().equals("PMT-2025110003"))
                .singleElement()
                .satisfies(p -> assertThat(p.getLateFee()).isEqualByComparingTo(new BigDecimal("47.50")));
    }
}
