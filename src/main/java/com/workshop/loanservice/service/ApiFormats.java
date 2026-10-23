package com.workshop.loanservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Value formats of the REST contract, shared by every {@link LoanDataSource}. */
final class ApiFormats {

    static final String UNKNOWN = "Unknown";

    /** The API has always exposed dates in the legacy MM/DD/YYYY format. */
    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private ApiFormats() {
    }

    static String formatDate(LocalDate date) {
        return date != null ? date.format(API_DATE_FORMAT) : null;
    }

    static BigDecimal zeroIfNull(BigDecimal amount) {
        return Objects.requireNonNullElse(amount, BigDecimal.ZERO);
    }

    static String unknownIfNull(String value) {
        return Objects.requireNonNullElse(value, UNKNOWN);
    }

    /** {@code First M. Last}, or {@code First Last} without a middle initial. */
    static String fullName(String firstName, String middleInitial, String lastName) {
        String middle = middleInitial != null ? " " + middleInitial + "." : "";
        return firstName + middle + " " + lastName;
    }

    static String propertyAddress(String address, String city, String state, String zip) {
        return address + ", " + city + ", " + state + " " + zip;
    }
}
