package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Typed accessors over one raw legacy row (column name to VARCHAR value).
 */
final class LegacyRow {

    private final Map<String, Object> values;

    LegacyRow(Map<String, Object> values) {
        this.values = values;
    }

    String raw(String column) {
        Object value = values.get(column);
        return value == null ? null : value.toString();
    }

    String text(String column) {
        return LegacyValueParser.clean(raw(column));
    }

    BigDecimal amount(String column) {
        return LegacyValueParser.parseAmount(column, raw(column));
    }

    BigDecimal decimal(String column) {
        return LegacyValueParser.parseDecimal(column, raw(column));
    }

    Integer integer(String column) {
        return LegacyValueParser.parseInteger(column, raw(column));
    }

    LocalDate date(String column) {
        return LegacyValueParser.parseDate(column, raw(column));
    }

    LocalDateTime timestamp(String column) {
        return LegacyValueParser.parseTimestamp(column, raw(column));
    }
}
