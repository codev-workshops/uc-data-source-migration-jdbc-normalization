package com.workshop.loanservice.migration;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Converts loosely typed legacy CDW values (everything is VARCHAR) into the
 * strongly typed values used by the modern schema.
 *
 * <p>Conversions follow {@code data/mappings/column_mappings.md}. Malformed
 * non-null input fails loudly rather than being silently defaulted.
 */
@Component
public class LegacyValueParser {

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    /**
     * Parses a legacy {@code MM/DD/YYYY} date. Null/blank input yields null.
     */
    public LocalDate parseDate(String value) {
        if (isBlank(value)) return null;
        try {
            return LocalDate.parse(value.trim(), LEGACY_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Malformed legacy date (expected MM/DD/YYYY): " + value, e);
        }
    }

    /**
     * Parses a legacy {@code MM/DD/YYYY} date into a timestamp at start of day.
     */
    public LocalDateTime parseTimestamp(String value) {
        LocalDate date = parseDate(value);
        return date != null ? date.atStartOfDay() : null;
    }

    /**
     * Parses a legacy amount such as {@code "285,000"} or {@code "1,487.02"}.
     * Null/blank input yields null so nullable modern columns stay null.
     */
    public BigDecimal parseAmount(String value) {
        if (isBlank(value)) return null;
        String normalized = value.replace(",", "").trim();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed legacy amount: " + value, e);
        }
    }

    /**
     * Parses a legacy integer string. Null/blank input yields null.
     */
    public Integer parseInteger(String value) {
        if (isBlank(value)) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed legacy integer: " + value, e);
        }
    }

    public String expandBorrowerStatus(String code) {
        if (isBlank(code)) return null;
        return switch (code.trim()) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> throw new IllegalArgumentException("Unknown borrower status code: " + code);
        };
    }

    public Boolean parseProductActive(String code) {
        if (isBlank(code)) return null;
        return switch (code.trim()) {
            case "ACT" -> Boolean.TRUE;
            case "INA" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("Unknown product status code: " + code);
        };
    }

    public String expandLoanStatus(String code) {
        if (isBlank(code)) return null;
        return switch (code.trim()) {
            case "ACT" -> "ACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> throw new IllegalArgumentException("Unknown loan status code: " + code);
        };
    }

    public String expandPropertyType(String code) {
        if (isBlank(code)) return null;
        return switch (code.trim()) {
            case "SFR" -> "SINGLE_FAMILY";
            case "CND" -> "CONDOMINIUM";
            case "MFR" -> "MULTI_FAMILY";
            case "TWN" -> "TOWNHOUSE";
            default -> throw new IllegalArgumentException("Unknown property type code: " + code);
        };
    }

    public String expandPaymentType(String code) {
        if (isBlank(code)) return null;
        return switch (code.trim()) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> throw new IllegalArgumentException("Unknown payment type code: " + code);
        };
    }

    public String expandPaymentStatus(String code) {
        if (isBlank(code)) return null;
        return switch (code.trim()) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> throw new IllegalArgumentException("Unknown payment status code: " + code);
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
