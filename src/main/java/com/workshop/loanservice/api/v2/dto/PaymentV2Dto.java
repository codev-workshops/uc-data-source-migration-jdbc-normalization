package com.workshop.loanservice.api.v2.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** v2 payment representation: ISO-8601 dates, canonical type/status codes. */
public record PaymentV2Dto(Long id,
                           String legacyId,
                           String loanAccountNumber,
                           LocalDate paymentDate,
                           BigDecimal totalAmount,
                           BigDecimal principalAmount,
                           BigDecimal interestAmount,
                           BigDecimal escrowAmount,
                           BigDecimal lateFee,
                           String type,
                           String status,
                           LocalDate receivedDate,
                           LocalDate processedDate) {
}
