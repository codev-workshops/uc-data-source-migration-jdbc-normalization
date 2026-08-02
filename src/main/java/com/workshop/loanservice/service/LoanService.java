package com.workshop.loanservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.config.ReadSourceProperties;
import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

/**
 * v1 read API. Its contract is frozen; what changed underneath is where the data comes from.
 *
 * <p>{@code loanservice.read-source} selects the store at runtime:
 * <ul>
 *   <li>{@code LEGACY} — the original behaviour, and the rollback position.</li>
 *   <li>{@code MODERN} — the migrated schema.</li>
 *   <li>{@code DUAL_READ} — serve modern, shadow-read legacy, and count every divergence. The
 *       shadow read is best-effort: it can never fail or slow down the response beyond its own
 *       cost, and its result is never returned.</li>
 * </ul>
 */
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LegacyLoanDataProvider legacy;
    private final ModernLoanDataProvider modern;
    private final ReadSourceProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Counter shadowMatches;
    private final Counter shadowMismatches;
    private final Counter shadowFailures;

    public LoanService(LegacyLoanDataProvider legacy,
                       ModernLoanDataProvider modern,
                       ReadSourceProperties properties,
                       MeterRegistry meterRegistry,
                       ObjectMapper objectMapper) {
        this.legacy = legacy;
        this.modern = modern;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.shadowMatches = meterRegistry.counter("loanservice.shadow.read", "result", "match");
        this.shadowMismatches = meterRegistry.counter("loanservice.shadow.read", "result", "mismatch");
        this.shadowFailures = meterRegistry.counter("loanservice.shadow.read", "result", "error");
    }

    public List<LoanSummaryDto> getAllLoans() {
        List<LoanSummaryDto> loans = read("getAllLoans", LoanDataProvider::getAllLoans);
        warnIfLarge("loans", loans.size());
        return loans;
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        return read("getLoanById", provider -> provider.getLoanById(loanAccountNumber));
    }

    public List<BorrowerDto> getAllBorrowers() {
        List<BorrowerDto> borrowers = read("getAllBorrowers", LoanDataProvider::getAllBorrowers);
        warnIfLarge("borrowers", borrowers.size());
        return borrowers;
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        return read("getBorrowerById", provider -> provider.getBorrowerById(borrowerId));
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
        return read("getPaymentsByLoan", provider -> provider.getPaymentsByLoan(loanAccountNumber));
    }

    private <T> T read(String operation, Function<LoanDataProvider, T> call) {
        return switch (properties.getReadSource()) {
            case LEGACY -> call.apply(legacy);
            case MODERN -> call.apply(modern);
            case DUAL_READ -> {
                T result = call.apply(modern);
                shadowRead(operation, call, result);
                yield result;
            }
        };
    }

    /**
     * Compares the two stores on their serialised form rather than on {@code equals}. The contract
     * is the JSON, so this catches what object equality would miss - a {@code BigDecimal} scale
     * change, a reordered list, a date rendered differently - which is precisely the class of
     * regression the reconciliation window exists to find.
     */
    private <T> void shadowRead(String operation, Function<LoanDataProvider, T> call, T modernResult) {
        try {
            T legacyResult = call.apply(legacy);
            if (serialize(modernResult).equals(serialize(legacyResult))) {
                shadowMatches.increment();
            } else {
                shadowMismatches.increment();
                // No identifiers and no row content: a mismatch report must not leak PII into logs.
                // The reconciliation job is what produces the detailed, access-controlled diff.
                log.warn("Shadow read mismatch operation={}", operation);
            }
        } catch (RuntimeException | JsonProcessingException e) {
            shadowFailures.increment();
            log.warn("Shadow read failed operation={} type={}", operation, e.getClass().getSimpleName());
        }
    }

    private String serialize(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * v1 list endpoints are unbounded by contract, and stay that way. This only makes the risk
     * visible: a counter and one WARN per oversized response, so the growth is observable before it
     * becomes an out-of-memory incident. Nothing is truncated. v2 is the bounded alternative.
     */
    private void warnIfLarge(String resource, int rowCount) {
        int threshold = properties.getV1().getLargeResponseWarnThreshold();
        if (rowCount > threshold) {
            meterRegistry.counter("loanservice.v1.large_response", "resource", resource).increment();
            log.warn("Unbounded v1 response resource={} rows={} threshold={} - consider /api/v2/{}",
                resource, rowCount, threshold, resource);
        }
    }
}
