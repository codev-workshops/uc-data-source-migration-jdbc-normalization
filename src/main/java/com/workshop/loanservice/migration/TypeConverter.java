package com.workshop.loanservice.migration;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Strict conversion utility from loose legacy strings to modern types.
 *
 * Every method takes the logical field name so that any failure can be reported
 * with full context (field + invalid value). Blank/null inputs convert to null;
 * any non-blank value that does not strictly match the expected format raises a
 * {@link ConversionException} rather than silently coercing or dropping data.
 */
@Component
public class TypeConverter {

    /** Legacy dates are stored as MM/DD/YYYY. STRICT rejects impossible dates (e.g. 02/30). */
    private static final DateTimeFormatter LEGACY_DATE =
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public LocalDate parseDate(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), LEGACY_DATE);
        } catch (DateTimeParseException e) {
            throw new ConversionException(field, value, "Invalid date, expected MM/DD/YYYY");
        }
    }

    public LocalDateTime parseTimestamp(String field, String value) {
        LocalDate date = parseDate(field, value);
        return date == null ? null : date.atStartOfDay();
    }

    public BigDecimal parseDecimal(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        String cleaned = value.replace(",", "").trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new ConversionException(field, value, "Invalid decimal");
        }
    }

    public Integer parseInteger(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConversionException(field, value, "Invalid integer");
        }
    }

    public String borrowerStatus(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        return switch (value.trim()) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> throw new ConversionException(field, value, "Unknown borrower status code");
        };
    }

    public String loanStatus(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        return switch (value.trim()) {
            case "ACT" -> "ACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> throw new ConversionException(field, value, "Unknown loan status code");
        };
    }

    public Boolean productActive(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        return switch (value.trim()) {
            case "ACT" -> Boolean.TRUE;
            case "INA" -> Boolean.FALSE;
            default -> throw new ConversionException(field, value, "Unknown product status code");
        };
    }

    public String paymentType(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        return switch (value.trim()) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> throw new ConversionException(field, value, "Unknown payment type code");
        };
    }

    public String paymentStatus(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        return switch (value.trim()) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> throw new ConversionException(field, value, "Unknown payment status code");
        };
    }

    public String propertyType(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        return switch (value.trim()) {
            case "SFR" -> "Single Family Residence";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family Residence";
            case "TWN" -> "Townhouse";
            default -> throw new ConversionException(field, value, "Unknown property type code");
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
