package com.workshop.loanservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.service.LoanService;
import com.workshop.loanservice.validation.LegacyDtoAssembler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Reconciliation endpoint: compares the legacy warehouse against the modern schema,
 * both on row counts and field-by-field on the DTOs the public API derives from each.
 */
@RestController
@RequestMapping("/api/validation")
public class ValidationController {

    private final LegacyDtoAssembler legacyAssembler;
    private final LoanService loanService;
    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    public ValidationController(LegacyDtoAssembler legacyAssembler,
                                LoanService loanService,
                                BorrowerRepository borrowerRepository,
                                LoanProductRepository loanProductRepository,
                                LoanAccountRepository loanAccountRepository,
                                PaymentRepository paymentRepository,
                                ObjectMapper objectMapper) {
        this.legacyAssembler = legacyAssembler;
        this.loanService = loanService;
        this.borrowerRepository = borrowerRepository;
        this.loanProductRepository = loanProductRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Map<String, Object> validate() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("rowCounts", rowCounts());

        List<Map<String, Object>> mismatches = new ArrayList<>();
        mismatches.addAll(compare("loans",
                legacyAssembler.getAllLoans(), loanService.getAllLoans(),
                LoanSummaryDto::getLoanAccountNumber));
        mismatches.addAll(compare("borrowers",
                legacyAssembler.getAllBorrowers(), loanService.getAllBorrowers(),
                BorrowerDto::getId));
        for (LoanSummaryDto loan : legacyAssembler.getAllLoans()) {
            String accountNumber = loan.getLoanAccountNumber();
            mismatches.addAll(compare("payments[" + accountNumber + "]",
                    legacyAssembler.getPaymentsByLoan(accountNumber),
                    loanService.getPaymentsByLoan(accountNumber),
                    PaymentDto::getPaymentId));
        }

        report.put("mismatches", mismatches);
        report.put("inParity", mismatches.isEmpty() && allCountsMatch());
        return report;
    }

    private Map<String, Object> rowCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("borrowers", count(legacyAssembler.countBorrowers(), borrowerRepository.count()));
        counts.put("loanProducts", count(legacyAssembler.countLoanProducts(), loanProductRepository.count()));
        counts.put("loanAccounts", count(legacyAssembler.countLoanAccounts(), loanAccountRepository.count()));
        counts.put("payments", count(legacyAssembler.countPayments(), paymentRepository.count()));
        return counts;
    }

    private boolean allCountsMatch() {
        return legacyAssembler.countBorrowers() == borrowerRepository.count()
                && legacyAssembler.countLoanProducts() == loanProductRepository.count()
                && legacyAssembler.countLoanAccounts() == loanAccountRepository.count()
                && legacyAssembler.countPayments() == paymentRepository.count();
    }

    private Map<String, Object> count(long legacy, long modern) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("legacy", legacy);
        entry.put("modern", modern);
        entry.put("match", legacy == modern);
        return entry;
    }

    /** Compares two DTO collections keyed by their business key, field by field. */
    private <T> List<Map<String, Object>> compare(String dataset,
                                                  List<T> legacyRecords,
                                                  List<T> modernRecords,
                                                  Function<T, String> keyFn) {
        Map<String, T> modernByKey = new LinkedHashMap<>();
        modernRecords.forEach(record -> modernByKey.put(keyFn.apply(record), record));

        List<Map<String, Object>> mismatches = new ArrayList<>();
        for (T legacyRecord : legacyRecords) {
            String key = keyFn.apply(legacyRecord);
            T modernRecord = modernByKey.remove(key);
            if (modernRecord == null) {
                mismatches.add(mismatch(dataset, key, "record", "present", "missing"));
                continue;
            }
            Map<String, Object> legacyFields = asMap(legacyRecord);
            Map<String, Object> modernFields = asMap(modernRecord);
            for (Map.Entry<String, Object> field : legacyFields.entrySet()) {
                Object modernValue = modernFields.get(field.getKey());
                if (!Objects.equals(field.getValue(), modernValue)) {
                    mismatches.add(mismatch(dataset, key, field.getKey(), field.getValue(), modernValue));
                }
            }
        }
        modernByKey.keySet().forEach(key ->
                mismatches.add(mismatch(dataset, key, "record", "missing", "present")));
        return mismatches;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object dto) {
        return objectMapper.convertValue(dto, Map.class);
    }

    private Map<String, Object> mismatch(String dataset, String key, String field,
                                         Object legacyValue, Object modernValue) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("dataset", dataset);
        entry.put("key", key);
        entry.put("field", field);
        entry.put("legacy", legacyValue);
        entry.put("modern", modernValue);
        return entry;
    }
}
