package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Converts the loosely-typed legacy CDW values into modern Java types.
 * Every rule here comes from {@code data/mappings/column_mappings.md}.
 * Blank and malformed values yield {@code null} rather than aborting the migration.
 */
public final class LegacyValueParser {

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/uuuu");

    private static final Map<String, String> LOAN_STATUS = Map.of(
            "ACT", "ACTIVE",
            "CLO", "CLOSED",
            "DFT", "DEFAULT",
            "FRB", "FORBEARANCE");

    private static final Map<String, String> BORROWER_STATUS = Map.of(
            "ACT", "ACTIVE",
            "INA", "INACTIVE");

    private static final Map<String, String> PROPERTY_TYPE = Map.of(
            "SFR", "Single Family Residence",
            "CND", "Condominium",
            "MFR", "Multi-Family Residence",
            "TWN", "Townhouse");

    private static final Map<String, String> PAYMENT_TYPE = Map.of(
            "REG", "REGULAR",
            "EXT", "EXTRA",
            "PRT", "PARTIAL",
            "PRE", "PREPAYMENT");

    private static final Map<String, String> PAYMENT_STATUS = Map.of(
            "PST", "POSTED",
            "REV", "REVERSED",
            "NSF", "NSF",
            "PND", "PENDING");

    private LegacyValueParser() {
    }

    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static LocalDate date(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed, LEGACY_DATE);
        } catch (DateTimeParseException e) {
            throw new MigrationDataException("Unparseable legacy date: '" + value + "'", e);
        }
    }

    public static LocalDateTime timestamp(String value) {
        LocalDate parsed = date(value);
        return parsed == null ? null : parsed.atStartOfDay();
    }

    /** Parses amounts such as {@code "285,000"} or {@code "1,487.02"}. */
    public static BigDecimal amount(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return new BigDecimal(trimmed.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new MigrationDataException("Unparseable legacy amount: '" + value + "'", e);
        }
    }

    public static BigDecimal decimal(String value) {
        return amount(value);
    }

    public static Integer integer(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new MigrationDataException("Unparseable legacy integer: '" + value + "'", e);
        }
    }

    public static String loanStatus(String code) {
        return expand(LOAN_STATUS, code);
    }

    public static String borrowerStatus(String code) {
        return expand(BORROWER_STATUS, code);
    }

    public static String propertyType(String code) {
        return expand(PROPERTY_TYPE, code);
    }

    public static String paymentType(String code) {
        return expand(PAYMENT_TYPE, code);
    }

    public static String paymentStatus(String code) {
        return expand(PAYMENT_STATUS, code);
    }

    /** {@code ACT} → {@code true}, anything else → {@code false}; blank → {@code null}. */
    public static Boolean activeFlag(String code) {
        String trimmed = text(code);
        return trimmed == null ? null : "ACT".equalsIgnoreCase(trimmed);
    }

    private static String expand(Map<String, String> mapping, String code) {
        String trimmed = text(code);
        if (trimmed == null) {
            return null;
        }
        return mapping.getOrDefault(trimmed.toUpperCase(), trimmed);
    }
}
