package com.workshop.loan.service;

import com.workshop.loan.client.BorrowerClient;
import com.workshop.loan.client.PaymentClient;
import com.workshop.loan.client.ProductClient;
import com.workshop.loan.dto.LoanSummaryDto;
import com.workshop.loan.dto.PaymentDto;
import com.workshop.loan.entity.LoanAccount;
import com.workshop.loan.repository.LoanAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class LoanService {

    private final LoanAccountRepository loanAccountRepository;
    private final BorrowerClient borrowerClient;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;

    public LoanService(LoanAccountRepository loanAccountRepository,
                       BorrowerClient borrowerClient,
                       ProductClient productClient,
                       PaymentClient paymentClient) {
        this.loanAccountRepository = loanAccountRepository;
        this.borrowerClient = borrowerClient;
        this.productClient = productClient;
        this.paymentClient = paymentClient;
    }

    public List<LoanSummaryDto> getAllLoans() {
        return enrich(loanAccountRepository.findAll());
    }

    public List<LoanSummaryDto> getLoansByBorrower(String borrowerId) {
        return enrich(loanAccountRepository.findByBorrowerId(borrowerId));
    }

    public LoanSummaryDto getLoanByAccountNumber(String accountNumber) {
        LoanAccount acct = loanAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Loan not found: " + accountNumber));
        return toSummary(acct);
    }

    public List<PaymentDto> getPaymentsByLoan(String accountNumber) {
        getLoanByAccountNumber(accountNumber); // validate loan exists
        return paymentClient.getPaymentsForLoan(accountNumber);
    }

    private List<LoanSummaryDto> enrich(List<LoanAccount> accounts) {
        // Cache resolved names within a single request to limit inter-service calls.
        Map<String, String> borrowerNames = new HashMap<>();
        Map<String, String> productNames = new HashMap<>();
        return accounts.stream()
                .map(acct -> toSummary(acct, borrowerNames, productNames))
                .collect(Collectors.toList());
    }

    private LoanSummaryDto toSummary(LoanAccount acct) {
        return toSummary(acct, new HashMap<>(), new HashMap<>());
    }

    private LoanSummaryDto toSummary(LoanAccount acct,
                                     Map<String, String> borrowerNames,
                                     Map<String, String> productNames) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        dto.setBorrowerId(acct.getBorrowerId());
        dto.setBorrowerName(borrowerNames.computeIfAbsent(acct.getBorrowerId(), this::resolveBorrowerName));
        dto.setProductCode(acct.getProductCode());
        dto.setProductDescription(productNames.computeIfAbsent(acct.getProductCode(), this::resolveProductName));
        dto.setOriginalAmount(acct.getOriginalAmount());
        dto.setCurrentBalance(acct.getCurrentBalance());
        dto.setInterestRate(acct.getInterestRate());
        dto.setMonthlyPayment(acct.getMonthlyPayment());
        dto.setStatus(acct.getStatus());
        dto.setOriginationDate(acct.getOriginationDate() != null ? acct.getOriginationDate().toString() : null);
        dto.setPropertyAddress(composeAddress(acct));
        dto.setPropertyType(acct.getPropertyType());
        return dto;
    }

    private String resolveBorrowerName(String borrowerId) {
        return borrowerClient.findByExternalId(borrowerId)
                .map(b -> b.getFullName())
                .orElse(borrowerId);
    }

    private String resolveProductName(String productCode) {
        return productClient.findByCode(productCode)
                .map(p -> p.getName())
                .orElse(productCode);
    }

    private String composeAddress(LoanAccount acct) {
        return acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip();
    }
}
