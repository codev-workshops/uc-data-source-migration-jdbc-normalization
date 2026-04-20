package com.workshop.loanservice.service;

import com.workshop.common.dto.LoanSummaryDto;
import com.workshop.common.dto.PaymentDto;
import com.workshop.common.exception.ResourceNotFoundException;
import com.workshop.common.util.LegacyDataParser;
import com.workshop.loanservice.client.PaymentServiceClient;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LoanService {

    private final LegacyLoanAccountRepository loanAccountRepository;
    private final LegacyLoanProductRepository loanProductRepository;
    private final PaymentServiceClient paymentServiceClient;

    public LoanService(LegacyLoanAccountRepository loanAccountRepository,
                       LegacyLoanProductRepository loanProductRepository,
                       PaymentServiceClient paymentServiceClient) {
        this.loanAccountRepository = loanAccountRepository;
        this.loanProductRepository = loanProductRepository;
        this.paymentServiceClient = paymentServiceClient;
    }

    public List<LoanSummaryDto> getAllLoans() {
        Map<String, LegacyLoanProduct> products = loanProductRepository.findAll()
                .stream()
                .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));

        return loanAccountRepository.findAll().stream()
                .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
                .collect(Collectors.toList());
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LegacyLoanAccount acct = loanAccountRepository.findById(loanAccountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanAccountNumber));
        LegacyLoanProduct product = loanProductRepository.findById(acct.getProductCode())
                .orElse(null);
        return toLoanSummary(acct, product);
    }

    public List<LoanSummaryDto> getLoansByBorrowerId(String borrowerId) {
        Map<String, LegacyLoanProduct> products = loanProductRepository.findAll()
                .stream()
                .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));

        return loanAccountRepository.findByBorrowerId(borrowerId).stream()
                .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
                .collect(Collectors.toList());
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return paymentServiceClient.getPaymentsByLoanAccountNumber(loanAccountNumber);
    }

    private LoanSummaryDto toLoanSummary(LegacyLoanAccount acct, LegacyLoanProduct product) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getLoanAccountNumber());
        dto.setBorrowerName(acct.getBorrowerFirstName() + " " + acct.getBorrowerLastName());
        dto.setProductDescription(product != null ? product.getDescription() : acct.getProductCode());
        dto.setOriginalAmount(LegacyDataParser.parseLegacyAmount(acct.getOriginalAmount()));
        dto.setCurrentBalance(LegacyDataParser.parseLegacyAmount(acct.getCurrentBalance()));
        dto.setInterestRate(LegacyDataParser.parseLegacyDecimal(acct.getInterestRate()));
        dto.setMonthlyPayment(LegacyDataParser.parseLegacyAmount(acct.getMonthlyPayment()));
        dto.setStatus(LegacyDataParser.expandStatusCode(acct.getStatusCode()));
        dto.setOriginationDate(acct.getOriginationDate());
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(LegacyDataParser.expandPropertyType(acct.getPropertyType()));
        return dto;
    }
}
