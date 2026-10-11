package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies the legacy CDW tables into the modern schema.
 *
 * <p>Tables are migrated in FK order (borrowers, loan_products, loan_accounts, payments). Rows
 * whose natural key (external_id / code / account_number / legacy payment id) already exists are
 * skipped, so the migration is idempotent. Rows that cannot be transformed are rejected and
 * reported; database errors roll back the whole run.
 */
@Service
@SuppressWarnings("deprecation")
public class DataMigrationService {

  private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);

  private final LegacyBorrowerRepository legacyBorrowerRepository;
  private final LegacyLoanProductRepository legacyLoanProductRepository;
  private final LegacyLoanAccountRepository legacyLoanAccountRepository;
  private final LegacyPaymentRepository legacyPaymentRepository;
  private final BorrowerRepository borrowerRepository;
  private final LoanProductRepository loanProductRepository;
  private final LoanAccountRepository loanAccountRepository;
  private final PaymentRepository paymentRepository;
  private final LegacyRecordMapper mapper;

  public DataMigrationService(LegacyBorrowerRepository legacyBorrowerRepository,
      LegacyLoanProductRepository legacyLoanProductRepository,
      LegacyLoanAccountRepository legacyLoanAccountRepository,
      LegacyPaymentRepository legacyPaymentRepository,
      BorrowerRepository borrowerRepository,
      LoanProductRepository loanProductRepository,
      LoanAccountRepository loanAccountRepository,
      PaymentRepository paymentRepository,
      LegacyRecordMapper mapper) {
    this.legacyBorrowerRepository = legacyBorrowerRepository;
    this.legacyLoanProductRepository = legacyLoanProductRepository;
    this.legacyLoanAccountRepository = legacyLoanAccountRepository;
    this.legacyPaymentRepository = legacyPaymentRepository;
    this.borrowerRepository = borrowerRepository;
    this.loanProductRepository = loanProductRepository;
    this.loanAccountRepository = loanAccountRepository;
    this.paymentRepository = paymentRepository;
    this.mapper = mapper;
  }

  @Transactional
  public MigrationReport migrate() {
    MigrationReport report = new MigrationReport(List.of(
        migrateTable("borrowers", legacyBorrowerRepository.findAll(),
            legacy -> legacy.getBorrowerId(),
            borrowerRepository::existsByExternalId,
            legacy -> borrowerRepository.save(mapper.toBorrower(legacy))),
        migrateTable("loan_products", legacyLoanProductRepository.findAll(),
            legacy -> legacy.getProductCode(),
            loanProductRepository::existsByCode,
            legacy -> loanProductRepository.save(mapper.toLoanProduct(legacy))),
        migrateTable("loan_accounts", legacyLoanAccountRepository.findAll(),
            LegacyLoanAccount::getLoanAccountNumber,
            loanAccountRepository::existsByAccountNumber,
            this::migrateLoanAccount),
        migrateTable("payments", legacyPaymentRepository.findAll(),
            LegacyPayment::getPaymentSequenceNumber,
            paymentRepository::existsByExternalId,
            this::migratePayment)));
    log.info(report.summary());
    return report;
  }

  private void migrateLoanAccount(LegacyLoanAccount legacy) {
    Borrower borrower = borrowerRepository.findByExternalId(legacy.getBorrowerId())
        .orElseThrow(() -> unresolved("BORR_ID", legacy.getBorrowerId(), "borrowers"));
    LoanProduct product = loanProductRepository.findByCode(legacy.getProductCode())
        .orElseThrow(() -> unresolved("PROD_CD", legacy.getProductCode(), "loan_products"));
    warnOnDenormalizedNameDrift(legacy, borrower);
    loanAccountRepository.save(mapper.toLoanAccount(legacy, borrower, product));
  }

  private void migratePayment(LegacyPayment legacy) {
    LoanAccount loanAccount = loanAccountRepository
        .findByAccountNumber(legacy.getLoanAccountNumber())
        .orElseThrow(() -> unresolved("LN_ACCT_NBR", legacy.getLoanAccountNumber(),
            "loan_accounts"));
    paymentRepository.save(mapper.toPayment(legacy, loanAccount));
  }

  private static <L> TableMigrationResult migrateTable(String table, List<L> legacyRows,
      Function<L, String> legacyKey, Predicate<String> alreadyMigrated, Consumer<L> migrateRow) {
    TableMigrationResult result = new TableMigrationResult(table, legacyRows.size());
    for (L row : legacyRows) {
      String key = legacyKey.apply(row);
      if (key != null && alreadyMigrated.test(key)) {
        log.debug("{} {} already migrated; skipping", table, key);
        result.recordSkippedDuplicate();
        continue;
      }
      try {
        migrateRow.accept(row);
        result.recordMigrated();
      } catch (LegacyDataException e) {
        log.warn("Rejected {} row {}: {}", table, key, e.getMessage());
        result.recordRejected(key, e.getMessage());
      }
    }
    return result;
  }

  /** The legacy loan row carries a copy of the borrower name; the modern FK is authoritative. */
  private static void warnOnDenormalizedNameDrift(LegacyLoanAccount legacy, Borrower borrower) {
    if (!Objects.equals(legacy.getBorrowerFirstName(), borrower.getFirstName())
        || !Objects.equals(legacy.getBorrowerLastName(), borrower.getLastName())) {
      log.warn("Loan {} denormalized borrower name '{} {}' differs from borrower {} '{} {}'; "
              + "using borrower record", legacy.getLoanAccountNumber(),
          legacy.getBorrowerFirstName(), legacy.getBorrowerLastName(), borrower.getExternalId(),
          borrower.getFirstName(), borrower.getLastName());
    }
  }

  private static LegacyDataException unresolved(String column, String value, String table) {
    return new LegacyDataException(
        column + " '" + value + "' does not resolve to a migrated " + table + " row");
  }
}
