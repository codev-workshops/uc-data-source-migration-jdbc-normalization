package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.migration.ReconciliationReport.Check;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares legacy and modern tables after migration: row counts, per-column amount totals and
 * per-row FK / key-amount agreement for loan accounts and payments.
 */
@Service
@SuppressWarnings("deprecation")
public class MigrationReconciliationService {

  private final LegacyBorrowerRepository legacyBorrowerRepository;
  private final LegacyLoanProductRepository legacyLoanProductRepository;
  private final LegacyLoanAccountRepository legacyLoanAccountRepository;
  private final LegacyPaymentRepository legacyPaymentRepository;
  private final BorrowerRepository borrowerRepository;
  private final LoanProductRepository loanProductRepository;
  private final LoanAccountRepository loanAccountRepository;
  private final PaymentRepository paymentRepository;

  public MigrationReconciliationService(LegacyBorrowerRepository legacyBorrowerRepository,
      LegacyLoanProductRepository legacyLoanProductRepository,
      LegacyLoanAccountRepository legacyLoanAccountRepository,
      LegacyPaymentRepository legacyPaymentRepository,
      BorrowerRepository borrowerRepository,
      LoanProductRepository loanProductRepository,
      LoanAccountRepository loanAccountRepository,
      PaymentRepository paymentRepository) {
    this.legacyBorrowerRepository = legacyBorrowerRepository;
    this.legacyLoanProductRepository = legacyLoanProductRepository;
    this.legacyLoanAccountRepository = legacyLoanAccountRepository;
    this.legacyPaymentRepository = legacyPaymentRepository;
    this.borrowerRepository = borrowerRepository;
    this.loanProductRepository = loanProductRepository;
    this.loanAccountRepository = loanAccountRepository;
    this.paymentRepository = paymentRepository;
  }

  @Transactional(readOnly = true)
  public ReconciliationReport reconcile() {
    List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
    List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
    List<LegacyLoanAccount> legacyLoans = legacyLoanAccountRepository.findAll();
    List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();
    List<Borrower> borrowers = borrowerRepository.findAll();
    List<LoanProduct> products = loanProductRepository.findAll();
    List<LoanAccount> loans = loanAccountRepository.findAll();
    List<Payment> payments = paymentRepository.findAll();

    List<Check> checks = new ArrayList<>();
    checks.add(countCheck("borrowers", legacyBorrowers, borrowers));
    checks.add(countCheck("loan_products", legacyProducts, products));
    checks.add(countCheck("loan_accounts", legacyLoans, loans));
    checks.add(countCheck("payments", legacyPayments, payments));

    checks.add(sumCheck("borrowers.annual_income", legacyBorrowers,
        LegacyBorrower::getAnnualIncome, borrowers, Borrower::getAnnualIncome));
    checks.add(sumCheck("loan_products.min_amount", legacyProducts,
        LegacyLoanProduct::getMinAmount, products, LoanProduct::getMinAmount));
    checks.add(sumCheck("loan_products.max_amount", legacyProducts,
        LegacyLoanProduct::getMaxAmount, products, LoanProduct::getMaxAmount));
    checks.add(sumCheck("loan_accounts.original_amount", legacyLoans,
        LegacyLoanAccount::getOriginalAmount, loans, LoanAccount::getOriginalAmount));
    checks.add(sumCheck("loan_accounts.current_balance", legacyLoans,
        LegacyLoanAccount::getCurrentBalance, loans, LoanAccount::getCurrentBalance));
    checks.add(sumCheck("loan_accounts.monthly_payment", legacyLoans,
        LegacyLoanAccount::getMonthlyPayment, loans, LoanAccount::getMonthlyPayment));
    checks.add(sumCheck("loan_accounts.escrow_balance", legacyLoans,
        LegacyLoanAccount::getEscrowBalance, loans, LoanAccount::getEscrowBalance));
    checks.add(sumCheck("loan_accounts.appraised_value", legacyLoans,
        LegacyLoanAccount::getAppraisedValue, loans, LoanAccount::getAppraisedValue));
    checks.add(sumCheck("payments.total_amount", legacyPayments,
        LegacyPayment::getTotalAmount, payments, Payment::getTotalAmount));
    checks.add(sumCheck("payments.principal_amount", legacyPayments,
        LegacyPayment::getPrincipalAmount, payments, Payment::getPrincipalAmount));
    checks.add(sumCheck("payments.interest_amount", legacyPayments,
        LegacyPayment::getInterestAmount, payments, Payment::getInterestAmount));
    checks.add(sumCheck("payments.escrow_amount", legacyPayments,
        LegacyPayment::getEscrowAmount, payments, Payment::getEscrowAmount));
    checks.add(sumCheck("payments.late_fee", legacyPayments,
        LegacyPayment::getLateFee, payments, Payment::getLateFee));

    checks.add(rowCheck("loan_accounts rows match legacy (FKs, amounts, rate)", legacyLoans,
        this::loanAccountMatches));
    checks.add(rowCheck("payments rows match legacy (FK, total)", legacyPayments,
        this::paymentMatches));
    return new ReconciliationReport(checks);
  }

  private boolean loanAccountMatches(LegacyLoanAccount legacy) {
    return loanAccountRepository.findByAccountNumber(legacy.getLoanAccountNumber())
        .filter(loan -> loan.getBorrower().getExternalId().equals(legacy.getBorrowerId()))
        .filter(loan -> loan.getProduct().getCode().equals(legacy.getProductCode()))
        .filter(loan -> sameAmount(legacy.getOriginalAmount(), loan.getOriginalAmount()))
        .filter(loan -> sameAmount(legacy.getCurrentBalance(), loan.getCurrentBalance()))
        .filter(loan -> sameAmount(legacy.getMonthlyPayment(), loan.getMonthlyPayment()))
        .filter(loan -> sameAmount(legacy.getInterestRate(), loan.getInterestRate()))
        .isPresent();
  }

  private boolean paymentMatches(LegacyPayment legacy) {
    return paymentRepository.findByExternalId(legacy.getPaymentSequenceNumber())
        .filter(payment -> payment.getLoanAccount().getAccountNumber()
            .equals(legacy.getLoanAccountNumber()))
        .filter(payment -> sameAmount(legacy.getTotalAmount(), payment.getTotalAmount()))
        .isPresent();
  }

  private static Check countCheck(String table, List<?> legacy, List<?> modern) {
    return new Check(table + " row count", legacy.size(), modern.size(),
        legacy.size() == modern.size());
  }

  private static <L, M> Check sumCheck(String column, List<L> legacyRows,
      Function<L, String> legacyValue, List<M> modernRows, Function<M, BigDecimal> modernValue) {
    BigDecimal legacyTotal = legacyRows.stream()
        .map(row -> parseOrNull(legacyValue.apply(row)))
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal modernTotal = modernRows.stream()
        .map(modernValue)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new Check(column + " total", legacyTotal, modernTotal,
        legacyTotal.compareTo(modernTotal) == 0);
  }

  private static <L> Check rowCheck(String name, List<L> legacyRows, Predicate<L> matches) {
    long matching = legacyRows.stream().filter(matches).count();
    return new Check(name, legacyRows.size(), matching, matching == legacyRows.size());
  }

  private static boolean sameAmount(String legacy, BigDecimal modern) {
    BigDecimal parsed = parseOrNull(legacy);
    return parsed == null ? modern == null : modern != null && parsed.compareTo(modern) == 0;
  }

  /** Malformed legacy values were rejected by the migration and surface as count mismatches. */
  private static BigDecimal parseOrNull(String legacyAmount) {
    try {
      return LegacyValueParser.parseAmount("reconciliation", legacyAmount);
    } catch (LegacyDataException e) {
      return null;
    }
  }
}
