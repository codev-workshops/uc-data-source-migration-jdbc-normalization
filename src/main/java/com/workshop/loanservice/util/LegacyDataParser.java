package com.workshop.loanservice.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @deprecated Legacy parsing utilities no longer needed. Modern schema uses proper types. Scheduled for removal in Phase 6.
 */
@Deprecated
public class LegacyDataParser {

    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private LegacyDataParser() {
    }

    public static LocalDate parseLegacyDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr, LEGACY_DATE_FORMAT);
    }

    public static LocalDateTime parseLegacyTimestamp(String dateStr) {
        LocalDate date = parseLegacyDate(dateStr);
        return date != null ? date.atStartOfDay() : null;
    }

    public static BigDecimal parseLegacyAmount(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(amount.replace(",", ""));
    }

    public static BigDecimal parseLegacyDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim());
    }

    public static Integer parseLegacyInteger(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value.trim());
    }

    public static String expandBorrowerStatus(String code) {
        if (code == null) return code;
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> code;
        };
    }

    public static String expandLoanStatus(String code) {
        if (code == null) return code;
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> code;
        };
    }

    public static String expandPropertyType(String code) {
        if (code == null) return code;
        return switch (code) {
            case "SFR" -> "Single Family Residence";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family Residence";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }

    public static String expandPaymentType(String code) {
        if (code == null) return code;
        return switch (code) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> code;
        };
    }

    public static String expandPaymentStatus(String code) {
        if (code == null) return code;
        return switch (code) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> code;
        };
    }
}
