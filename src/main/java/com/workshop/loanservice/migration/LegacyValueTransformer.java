package com.workshop.loanservice.migration;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class LegacyValueTransformer {

    private static final DateTimeFormatter LEGACY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy");

    public LocalDate requiredDate(
            String table,
            String sourceId,
            String column,
            String value
    ) {
        String normalized = requiredString(table, sourceId, column, value);
        try {
            return LocalDate.parse(normalized, LEGACY_DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            throw invalidValue(table, sourceId, column, value, ex);
        }
    }

    public LocalDate optionalDate(
            String table,
            String sourceId,
            String column,
            String value
    ) {
        if (isBlank(value)) {
            return null;
        }
        return requiredDate(table, sourceId, column, value);
    }

    public LocalDateTime optionalTimestamp(
            String table,
            String sourceId,
            String column,
            String value
    ) {
        LocalDate date = optionalDate(table, sourceId, column, value);
        return date == null ? null : date.atStartOfDay();
    }

    public BigDecimal requiredDecimal(
            String table,
            String sourceId,
            String column,
            String value
    ) {
        String normalized = requiredString(table, sourceId, column, value);
        try {
            return new BigDecimal(normalized.replace(",", ""));
        } catch (NumberFormatException ex) {
            throw invalidValue(table, sourceId, column, value, ex);
        }
    }

    public BigDecimal optionalDecimal(
            String table,
            String sourceId,
            String column,
            String value
    ) {
        if (isBlank(value)) {
            return null;
        }
        return requiredDecimal(table, sourceId, column, value);
    }

    public Integer requiredInteger(
            String table,
            String sourceId,
            String column,
            String value
    ) {
        String normalized = requiredString(table, sourceId, column, value);
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw invalidValue(table, sourceId, column, value, ex);
        }
    }

    public Integer optionalInteger(
            String table,
            String sourceId,
            String column,
            String value
    ) {
        if (isBlank(value)) {
            return null;
        }
        return requiredInteger(table, sourceId, column, value);
    }

    public String requiredString(
            String table,
            String sourceId,
            String column,
            String value
    ) {
        if (isBlank(value)) {
            throw new LegacyDataMigrationException(
                    "Missing required value in " + table + " sourceId=" + sourceId
                            + " column=" + column
            );
        }
        return value.trim();
    }

    public String expandBorrowerStatus(String table, String sourceId, String column, String code) {
        return switch (requiredString(table, sourceId, column, code)) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> throw unknownCode(table, sourceId, column, code);
        };
    }

    public boolean expandActiveFlag(String table, String sourceId, String column, String code) {
        return switch (requiredString(table, sourceId, column, code)) {
            case "ACT" -> true;
            case "INA" -> false;
            default -> throw unknownCode(table, sourceId, column, code);
        };
    }

    public String expandLoanStatus(String table, String sourceId, String column, String code) {
        return switch (requiredString(table, sourceId, column, code)) {
            case "ACT" -> "ACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> throw unknownCode(table, sourceId, column, code);
        };
    }

    public String expandPropertyType(String table, String sourceId, String column, String code) {
        return switch (requiredString(table, sourceId, column, code)) {
            case "SFR" -> "Single Family Residence";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family Residence";
            case "TWN" -> "Townhouse";
            default -> throw unknownCode(table, sourceId, column, code);
        };
    }

    public String expandPaymentType(String table, String sourceId, String column, String code) {
        return switch (requiredString(table, sourceId, column, code)) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> throw unknownCode(table, sourceId, column, code);
        };
    }

    public String expandPaymentStatus(String table, String sourceId, String column, String code) {
        return switch (requiredString(table, sourceId, column, code)) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> throw unknownCode(table, sourceId, column, code);
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LegacyDataMigrationException unknownCode(
            String table,
            String sourceId,
            String column,
            String value
    ) {
        return new LegacyDataMigrationException(
                "Unknown code in " + table + " sourceId=" + sourceId + " column="
                        + column + " value=" + value
        );
    }

    private LegacyDataMigrationException invalidValue(
            String table,
            String sourceId,
            String column,
            String value,
            RuntimeException cause
    ) {
        return new LegacyDataMigrationException(
                "Invalid value in " + table + " sourceId=" + sourceId + " column="
                        + column + " value=" + value,
                cause
        );
    }
}
