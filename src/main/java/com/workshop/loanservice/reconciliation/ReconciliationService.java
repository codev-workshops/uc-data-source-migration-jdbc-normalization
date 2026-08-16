package com.workshop.loanservice.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.provider.LegacyLoanDataProvider;
import com.workshop.loanservice.provider.LoanDataProvider;
import com.workshop.loanservice.provider.ModernLoanDataProvider;
import com.workshop.loanservice.repository.legacy.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.legacy.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.legacy.LegacyPaymentRepository;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares the legacy and modern data sources after a migration.
 *
 * <p>The two data sources are separate databases, so there is no cross-database
 * {@code JOIN} to lean on: reconciliation happens in the application. Row counts
 * come straight from the repositories, and equivalence of the actual data is
 * checked by serialising both providers' DTOs for every endpoint and comparing the
 * JSON — which is also exactly the API-compatibility guarantee we care about.
 */
@Service
public class ReconciliationService {

    private final LegacyBorrowerRepository legacyBorrowers;
    private final LegacyLoanProductRepository legacyProducts;
    private final LegacyLoanAccountRepository legacyLoans;
    private final LegacyPaymentRepository legacyPayments;

    private final BorrowerRepository borrowers;
    private final LoanProductRepository products;
    private final LoanAccountRepository loans;
    private final PaymentRepository payments;

    private final LoanDataProvider legacyProvider;
    private final LoanDataProvider modernProvider;
    private final ObjectMapper objectMapper;

    public ReconciliationService(LegacyBorrowerRepository legacyBorrowers,
                                 LegacyLoanProductRepository legacyProducts,
                                 LegacyLoanAccountRepository legacyLoans,
                                 LegacyPaymentRepository legacyPayments,
                                 BorrowerRepository borrowers,
                                 LoanProductRepository products,
                                 LoanAccountRepository loans,
                                 PaymentRepository payments,
                                 LegacyLoanDataProvider legacyProvider,
                                 ModernLoanDataProvider modernProvider,
                                 ObjectMapper objectMapper) {
        this.legacyBorrowers = legacyBorrowers;
        this.legacyProducts = legacyProducts;
        this.legacyLoans = legacyLoans;
        this.legacyPayments = legacyPayments;
        this.borrowers = borrowers;
        this.products = products;
        this.loans = loans;
        this.payments = payments;
        this.legacyProvider = legacyProvider;
        this.modernProvider = modernProvider;
        this.objectMapper = objectMapper;
    }

    public ReconciliationReport reconcile() {
        ReconciliationReport report = new ReconciliationReport();

        report.add("borrowers.count", legacyBorrowers.count(), borrowers.count());
        report.add("loan_products.count", legacyProducts.count(), products.count());
        report.add("loan_accounts.count", legacyLoans.count(), loans.count());
        report.add("payments.count", legacyPayments.count(), payments.count());

        compare(report, "GET /api/loans", legacyProvider.getAllLoans(), modernProvider.getAllLoans());
        compare(report, "GET /api/borrowers", legacyProvider.getAllBorrowers(),
                modernProvider.getAllBorrowers());

        for (LoanSummaryDto loan : legacyProvider.getAllLoans()) {
            String id = loan.getLoanAccountNumber();
            compare(report, "GET /api/loans/" + id,
                    legacyProvider.getLoanById(id), modernProvider.getLoanById(id));
            compare(report, "GET /api/loans/" + id + "/payments",
                    legacyProvider.getPaymentsByLoan(id), modernProvider.getPaymentsByLoan(id));
        }

        for (String borrowerId : legacyBorrowers.findAll().stream()
                .map(b -> b.getBorrowerId()).toList()) {
            compare(report, "GET /api/borrowers/" + borrowerId,
                    legacyProvider.getBorrowerById(borrowerId),
                    modernProvider.getBorrowerById(borrowerId));
        }

        return report;
    }

    private void compare(ReconciliationReport report, String name, Object legacy, Object modern) {
        String legacyJson = toJson(legacy);
        String modernJson = toJson(modern);
        List<String> differences = new ArrayList<>();
        if (!legacyJson.equals(modernJson)) {
            differences.add(name + ": legacy " + legacyJson + " != modern " + modernJson);
        }
        report.add(name, legacyJson, modernJson, differences);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise reconciliation subject", e);
        }
    }
}
