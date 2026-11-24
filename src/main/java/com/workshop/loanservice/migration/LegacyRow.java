package com.workshop.loanservice.migration;

import com.workshop.loanservice.migration.LegacyValueParser.DecimalSpec;
import org.springframework.jdbc.core.ColumnMapRowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Typed accessors over one raw legacy row (column name to VARCHAR value).
 */
final class LegacyRow {

    private static final ColumnMapRowMapper COLUMNS = new ColumnMapRowMapper();

    private final Map<String, Object> values;

    LegacyRow(Map<String, Object> values) {
        this.values = values;
    }

    static LegacyRow of(ResultSet rs) throws SQLException {
        return new LegacyRow(COLUMNS.mapRow(rs, rs.getRow()));
    }

    String raw(String column) {
        Object value = values.get(column);
        return value == null ? null : value.toString();
    }

    /** Rejects the row when a column the modern schema declares NOT NULL is blank in the legacy row. */
    <T> T required(String column, T value) {
        if (value == null) {
            throw new MalformedLegacyDataException(column, raw(column), "missing required value");
        }
        return value;
    }

    /** Sanitized text ({@link LegacyValueParser#clean}). */
    String text(String column) {
        return LegacyValueParser.clean(raw(column));
    }

    BigDecimal amount(String column, DecimalSpec spec) {
        return LegacyValueParser.parseAmount(column, raw(column), spec);
    }

    BigDecimal decimal(String column, DecimalSpec spec) {
        return LegacyValueParser.parseDecimal(column, raw(column), spec);
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
