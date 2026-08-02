package com.workshop.loanservice.api.v2.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * v2 loan representation: ISO-8601 dates and raw canonical codes instead of v1's MM/DD/YYYY strings
 * and English labels. Clients localise and format; the API stops guessing how the value will be
 * displayed. The typed fields also mean a client no longer has to parse "285,000" out of a string.
 */
public record LoanV2Dto(Long id,
                        String accountNumber,
                        String borrowerExternalId,
                        String borrowerFirstName,
                        String borrowerLastName,
                        String productCode,
                        String productName,
                        BigDecimal originalAmount,
                        BigDecimal currentBalance,
                        BigDecimal interestRate,
                        Integer termMonths,
                        BigDecimal monthlyPayment,
                        LocalDate originationDate,
                        LocalDate maturityDate,
                        LocalDate nextPaymentDate,
                        String status,
                        Integer delinquencyDays,
                        BigDecimal escrowBalance,
                        BigDecimal ltvPercent,
                        String propertyLine1,
                        String propertyCity,
                        String propertyState,
                        String propertyZip,
                        String propertyType) {
}
