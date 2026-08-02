package com.workshop.loanservice.api.v2.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * v2 borrower representation.
 *
 * <p>The SSN hash is absent by design. It exists in the modern schema because downstream systems
 * match on it, but nothing in an API response needs it, and a field that is never serialised cannot
 * leak through a log, a cache or a browser devtools panel.
 */
public record BorrowerV2Dto(Long id,
                            String externalId,
                            String firstName,
                            String middleInitial,
                            String lastName,
                            LocalDate dateOfBirth,
                            String email,
                            String phone,
                            String addressLine1,
                            String addressLine2,
                            String city,
                            String state,
                            String zipCode,
                            Integer creditScore,
                            String employmentStatus,
                            BigDecimal annualIncome,
                            String status) {
}
