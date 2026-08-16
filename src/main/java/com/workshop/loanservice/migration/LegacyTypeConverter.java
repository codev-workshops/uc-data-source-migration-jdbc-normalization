package com.workshop.loanservice.migration;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Single home for every legacy CDW parsing and code-expansion rule: string dates
 * in {@code MM/DD/YYYY}, amounts with thousands separators, and cryptic status
 * codes. Both the migration and the (deprecated) legacy read path use it, so the
 * two data sources cannot drift in how they interpret legacy values.
 *
 * <p>Expansion produces <em>canonical</em> values as stored in the modern schema
 * ({@code ACTIVE}, {@code REGULAR}); turning those into the strings the API
 * returns is {@link com.workshop.loanservice.provider.PresentationFormat}'s job.
 */
@Component
public class LegacyTypeConverter {

    public static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    public LocalDate parseDate(String value) {
        if (isBlank(value)) return null;
        return LocalDate.parse(value.trim(), LEGACY_DATE);
    }

    public LocalDateTime parseTimestamp(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.atStartOfDay();
    }

    /** Parses amounts such as {@code "285,000"} or {@code "1,487.02"}. */
    public BigDecimal parseAmount(String value) {
        if (isBlank(value)) return BigDecimal.ZERO;
        return new BigDecimal(value.trim().replace(",", ""));
    }

    public BigDecimal parseDecimal(String value) {
        if (isBlank(value)) return BigDecimal.ZERO;
        return new BigDecimal(value.trim());
    }

    public Integer parseInteger(String value) {
        if (isBlank(value)) return null;
        return Integer.parseInt(value.trim());
    }

    public String canonicalBorrowerStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> code;
        };
    }

    public String canonicalLoanStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> code;
        };
    }

    public String canonicalPaymentType(String code) {
        if (code == null) return null;
        return switch (code) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> code;
        };
    }

    public String canonicalPaymentStatus(String code) {
        if (code == null) return null;
        return switch (code) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> code;
        };
    }

    /**
     * Property types are stored in their descriptive form, which is also what the
     * API returns. {@code column_mappings.md} abbreviates these (SFR &rarr;
     * "Single Family"); the longer form is used because it is the value the API
     * has always emitted.
     */
    public String canonicalPropertyType(String code) {
        if (code == null) return null;
        return switch (code) {
            case "SFR" -> "Single Family Residence";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family Residence";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }

    public Boolean parseActiveFlag(String code) {
        if (code == null) return null;
        return "ACT".equals(code);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
