package com.workshop.loanservice.provider;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Turns canonical values into the exact strings and numeric scales the existing
 * API emits. Legacy CDW rows carried presentation baked into storage (dates as
 * {@code MM/DD/YYYY} text, {@code "285,000"} for whole-dollar amounts, title-case
 * status expansion in the service); the modern schema stores canonical typed
 * values instead, so that presentation is reapplied here.
 *
 * <p>Both providers format through this class, which is what keeps the legacy and
 * modern responses byte-identical.
 */
@Component
public class PresentationFormat {

    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String UNKNOWN = "Unknown";

    public String date(LocalDate date) {
        return date == null ? null : date.format(API_DATE);
    }

    /** Amounts the API renders with cents (balances, payments, fees). */
    public BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Whole-dollar amounts: CDW stored loan origination amounts without cents, so
     * the API renders {@code 285000} rather than {@code 285000.00}.
     */
    public BigDecimal wholeDollars(BigDecimal amount) {
        if (amount == null) return null;
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.setScale(0, RoundingMode.HALF_UP) : stripped;
    }

    /** Interest rates are rendered with three decimals, e.g. {@code 4.750}. */
    public BigDecimal rate(BigDecimal rate) {
        return rate == null ? null : rate.setScale(3, RoundingMode.HALF_UP);
    }

    public String loanStatus(String canonical) {
        if (canonical == null) return UNKNOWN;
        return switch (canonical) {
            case "ACTIVE" -> "Active";
            case "CLOSED" -> "Closed";
            case "DEFAULT" -> "Default";
            case "FORBEARANCE" -> "Forbearance";
            default -> canonical;
        };
    }

    public String paymentType(String canonical) {
        if (canonical == null) return UNKNOWN;
        return switch (canonical) {
            case "REGULAR" -> "Regular";
            case "EXTRA" -> "Extra";
            case "PARTIAL" -> "Partial";
            case "PREPAYMENT" -> "Prepayment";
            default -> canonical;
        };
    }

    public String paymentStatus(String canonical) {
        if (canonical == null) return UNKNOWN;
        return switch (canonical) {
            case "POSTED" -> "Posted";
            case "REVERSED" -> "Reversed";
            case "NSF" -> "Non-Sufficient Funds";
            case "PENDING" -> "Pending";
            default -> canonical;
        };
    }

    /** Property types are already stored in descriptive form. */
    public String propertyType(String canonical) {
        return canonical == null ? UNKNOWN : canonical;
    }

    public String fullAddress(String line1, String city, String state, String zip) {
        return line1 + ", " + city + ", " + state + " " + zip;
    }

    public String borrowerFullName(String firstName, String middleInitial, String lastName) {
        String middle = middleInitial != null ? " " + middleInitial + "." : "";
        return firstName + middle + " " + lastName;
    }
}
