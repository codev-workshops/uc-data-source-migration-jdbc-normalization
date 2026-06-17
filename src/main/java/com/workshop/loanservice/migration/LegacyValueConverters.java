package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Pure transformation helpers that turn legacy CDW string values into the
 * properly-typed/canonical values used by the modern schema.
 *
 * The rules implemented here mirror {@code data/mappings/column_mappings.md}:
 *   - Dates: {@code MM/DD/YYYY} strings → {@link LocalDate} / {@link LocalDateTime}
 *   - Amounts: comma-grouped strings ("285,000") → {@link BigDecimal}
 *   - Codes: cryptic abbreviations → canonical upper-case values
 *            (e.g. {@code ACT → ACTIVE}); product status → boolean
 *
 * Status/type fields are stored in their canonical (upper-case) form. Mapping the
 * canonical values to the human-readable strings the public API returns is the
 * responsibility of the read/translation layer (Task 3), not the migration.
 */
public final class LegacyValueConverters {

    private static final DateTimeFormatter LEGACY_DATE =
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);

    private LegacyValueConverters() {
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Parse a comma-grouped amount string (e.g. "1,487.02") into a BigDecimal. */
    public static BigDecimal parseAmount(String value) {
        if (isBlank(value)) {
            return null;
        }
        return new BigDecimal(value.replace(",", "").trim());
    }

    /** Parse a plain decimal string (e.g. "5.250", "82.5") into a BigDecimal. */
    public static BigDecimal parseDecimal(String value) {
        if (isBlank(value)) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    /** Parse an integer string into an Integer. */
    public static Integer parseInteger(String value) {
        if (isBlank(value)) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    /** Parse an {@code MM/DD/YYYY} string into a LocalDate. */
    public static LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        return LocalDate.parse(value.trim(), LEGACY_DATE);
    }

    /** Parse an {@code MM/DD/YYYY} string into a LocalDateTime (start of day). */
    public static LocalDateTime parseTimestamp(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.atStartOfDay();
    }

    /** Borrower status code: ACT → ACTIVE, INA → INACTIVE. */
    public static String expandBorrowerStatus(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> code;
        };
    }

    /** Product status code: ACT → true, INA → false. */
    public static Boolean parseProductActive(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "ACT" -> Boolean.TRUE;
            case "INA" -> Boolean.FALSE;
            default -> Boolean.FALSE;
        };
    }

    /** Loan account status code: ACT → ACTIVE, CLO → CLOSED, DFT → DEFAULT, FRB → FORBEARANCE. */
    public static String expandLoanStatus(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> code;
        };
    }

    /** Property type code: SFR → Single Family, CND → Condominium, MFR → Multi-Family, TWN → Townhouse. */
    public static String expandPropertyType(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "SFR" -> "Single Family";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }

    /** Payment type code: REG → REGULAR, EXT → EXTRA, PRT → PARTIAL, PRE → PREPAYMENT. */
    public static String expandPaymentType(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> code;
        };
    }

    /** Payment status code: PST → POSTED, REV → REVERSED, NSF → NSF, PND → PENDING. */
    public static String expandPaymentStatus(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> code;
        };
    }
}
