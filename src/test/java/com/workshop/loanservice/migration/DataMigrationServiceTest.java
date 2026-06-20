package com.workshop.loanservice.migration;

import com.workshop.loanservice.legacy.entity.LegacyBorrower;
import com.workshop.loanservice.legacy.entity.LegacyLoanAccount;
import com.workshop.loanservice.legacy.entity.LegacyPayment;
import com.workshop.loanservice.legacy.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.legacy.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.legacy.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.legacy.repository.LegacyPaymentRepository;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the migration logic: idempotency, malformed data and missing
 * references. Repositories are mocked so each phase can be exercised in
 * isolation; the end-to-end happy path is validated against the real schema in
 * {@link com.workshop.loanservice.ModernSchemaIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataMigrationServiceTest {

    @Mock LegacyBorrowerRepository legacyBorrowers;
    @Mock LegacyLoanProductRepository legacyProducts;
    @Mock LegacyLoanAccountRepository legacyLoanAccounts;
    @Mock LegacyPaymentRepository legacyPayments;
    @Mock BorrowerRepository borrowers;
    @Mock LoanProductRepository products;
    @Mock LoanAccountRepository loanAccounts;
    @Mock PaymentRepository payments;

    private DataMigrationService service() {
        return new DataMigrationService(legacyBorrowers, legacyProducts, legacyLoanAccounts,
                legacyPayments, borrowers, products, loanAccounts, payments, new TypeConverter());
    }

    @Test
    void insertsNewBorrower() {
        when(legacyBorrowers.findAll()).thenReturn(List.of(validBorrower("B-1")));
        when(borrowers.findByExternalId("B-1")).thenReturn(Optional.empty());

        MigrationReport report = service().migrate();

        assertThat(report.getTable("borrowers").getInserted()).isEqualTo(1);
        assertThat(report.getFailed()).isZero();
        verify(borrowers).save(any(Borrower.class));
    }

    @Test
    void skipsBorrowerThatAlreadyExists() {
        when(legacyBorrowers.findAll()).thenReturn(List.of(validBorrower("B-1")));
        when(borrowers.findByExternalId("B-1")).thenReturn(Optional.of(new Borrower()));

        MigrationReport report = service().migrate();

        assertThat(report.getTable("borrowers").getSkipped()).isEqualTo(1);
        assertThat(report.getTable("borrowers").getInserted()).isZero();
        verify(borrowers, never()).save(any());
    }

    @Test
    void malformedDateProducesContextualFailureAndSkipsInsert() {
        LegacyBorrower bad = validBorrower("B-9");
        bad.setDateOfBirth("99/99/9999");
        when(legacyBorrowers.findAll()).thenReturn(List.of(bad));
        when(borrowers.findByExternalId("B-9")).thenReturn(Optional.empty());

        MigrationReport report = service().migrate();

        assertThat(report.getTable("borrowers").getInserted()).isZero();
        assertThat(report.getTable("borrowers").getFailed()).isEqualTo(1);
        MigrationReport.Failure failure = report.getAllFailures().get(0);
        assertThat(failure.getTable()).isEqualTo("borrowers");
        assertThat(failure.getBusinessKey()).isEqualTo("B-9");
        assertThat(failure.getField()).isEqualTo("date_of_birth");
        assertThat(failure.getInvalidValue()).isEqualTo("99/99/9999");
        verify(borrowers, never()).save(any());
    }

    @Test
    void loanAccountWithMissingBorrowerProducesContextualFailure() {
        LegacyLoanAccount acct = new LegacyLoanAccount();
        acct.setLoanAccountNumber("LN-1");
        acct.setBorrowerId("B-NOPE");
        acct.setProductCode("FXD30");
        when(legacyLoanAccounts.findAll()).thenReturn(List.of(acct));
        when(loanAccounts.findByAccountNumber("LN-1")).thenReturn(Optional.empty());
        when(borrowers.findByExternalId("B-NOPE")).thenReturn(Optional.empty());

        MigrationReport report = service().migrate();

        MigrationReport.Failure failure = report.getAllFailures().get(0);
        assertThat(failure.getTable()).isEqualTo("loan_accounts");
        assertThat(failure.getBusinessKey()).isEqualTo("LN-1");
        assertThat(failure.getField()).isEqualTo("borrower_id");
        assertThat(failure.getInvalidValue()).isEqualTo("B-NOPE");
        verify(loanAccounts, never()).save(any());
    }

    @Test
    void loanAccountWithMissingProductProducesContextualFailure() {
        LegacyLoanAccount acct = new LegacyLoanAccount();
        acct.setLoanAccountNumber("LN-2");
        acct.setBorrowerId("B-1");
        acct.setProductCode("NOPE");
        when(legacyLoanAccounts.findAll()).thenReturn(List.of(acct));
        when(loanAccounts.findByAccountNumber("LN-2")).thenReturn(Optional.empty());
        when(borrowers.findByExternalId("B-1")).thenReturn(Optional.of(new Borrower()));
        when(products.findByCode("NOPE")).thenReturn(Optional.empty());

        MigrationReport report = service().migrate();

        MigrationReport.Failure failure = report.getAllFailures().get(0);
        assertThat(failure.getField()).isEqualTo("product_id");
        assertThat(failure.getInvalidValue()).isEqualTo("NOPE");
        verify(loanAccounts, never()).save(any());
    }

    @Test
    void paymentWithMissingLoanAccountProducesContextualFailure() {
        LegacyPayment pmt = new LegacyPayment();
        pmt.setPaymentSequenceNumber("PMT-1");
        pmt.setLoanAccountNumber("LN-NOPE");
        when(legacyPayments.findAll()).thenReturn(List.of(pmt));
        when(payments.findByExternalId("PMT-1")).thenReturn(Optional.empty());
        when(loanAccounts.findByAccountNumber("LN-NOPE")).thenReturn(Optional.empty());

        MigrationReport report = service().migrate();

        MigrationReport.Failure failure = report.getAllFailures().get(0);
        assertThat(failure.getTable()).isEqualTo("payments");
        assertThat(failure.getBusinessKey()).isEqualTo("PMT-1");
        assertThat(failure.getField()).isEqualTo("loan_account_id");
        assertThat(failure.getInvalidValue()).isEqualTo("LN-NOPE");
        verify(payments, never()).save(any());
    }

    @Test
    void insertsLoanAccountWhenReferencesResolve() {
        LegacyLoanAccount acct = new LegacyLoanAccount();
        acct.setLoanAccountNumber("LN-3");
        acct.setBorrowerId("B-1");
        acct.setProductCode("FXD30");
        acct.setOriginalAmount("285,000");
        acct.setStatusCode("ACT");
        acct.setPropertyType("SFR");
        when(legacyLoanAccounts.findAll()).thenReturn(List.of(acct));
        when(loanAccounts.findByAccountNumber("LN-3")).thenReturn(Optional.empty());
        when(borrowers.findByExternalId("B-1")).thenReturn(Optional.of(new Borrower()));
        when(products.findByCode("FXD30")).thenReturn(Optional.of(new LoanProduct()));

        MigrationReport report = service().migrate();

        assertThat(report.getTable("loan_accounts").getInserted()).isEqualTo(1);
        assertThat(report.getFailed()).isZero();
        verify(loanAccounts).save(any(LoanAccount.class));
    }

    private LegacyBorrower validBorrower(String id) {
        LegacyBorrower b = new LegacyBorrower();
        b.setBorrowerId(id);
        b.setFirstName("Test");
        b.setLastName("Person");
        b.setStatusCode("ACT");
        return b;
    }
}
