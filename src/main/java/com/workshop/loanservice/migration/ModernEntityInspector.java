package com.workshop.loanservice.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reflection helpers over the modern entities' JPA mapping metadata, so column constraints and
 * row comparisons stay in sync with the entity definitions instead of being duplicated per table.
 */
final class ModernEntityInspector {

    /** Audit columns are set at migration time and legitimately differ between runs. */
    private static final Set<String> IGNORED_FIELDS = Set.of("createdAt", "updatedAt");

    /** Values never echoed into reports/logs; free text is also omitted since it may hold PII. */
    private static final Set<String> SENSITIVE_FIELDS = Set.of("ssnHash", "dateOfBirth");

    private ModernEntityInspector() {
    }

    /**
     * Rejects values that would not fit the target column ({@code @Column} length, precision, scale),
     * so the row is reported instead of failing the whole transaction at flush time.
     */
    static void validateColumnConstraints(Object entity) {
        for (Field field : entity.getClass().getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column == null) {
                continue;
            }
            Object value = read(field, entity);
            if (value instanceof String text && text.length() > column.length()) {
                throw new LegacyDataException(column.name() + " exceeds " + column.length()
                        + " characters (length " + text.length() + ")");
            }
            if (value instanceof BigDecimal amount && column.precision() > 0) {
                int maxIntegerDigits = column.precision() - column.scale();
                BigDecimal normalized = amount.stripTrailingZeros();
                int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
                if (integerDigits > maxIntegerDigits || normalized.scale() > column.scale()) {
                    throw new LegacyDataException(column.name() + " value " + amount.toPlainString()
                            + " does not fit DECIMAL(" + column.precision() + "," + column.scale() + ")");
                }
            }
        }
    }

    /**
     * Lists the mapped fields whose values differ between two rows of the same entity type.
     * Amounts compare by numeric value; {@code @ManyToOne} references compare by target id.
     */
    static List<String> differingFields(Object expected, Object actual) {
        List<String> differences = new ArrayList<>();
        for (Field field : expected.getClass().getDeclaredFields()) {
            boolean mapped = field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(ManyToOne.class);
            if (!mapped || field.isAnnotationPresent(Id.class) || IGNORED_FIELDS.contains(field.getName())) {
                continue;
            }
            Object left = comparable(field, read(field, expected));
            Object right = comparable(field, read(field, actual));
            if (!valuesEqual(left, right)) {
                differences.add(describeDifference(field, left, right));
            }
        }
        return differences;
    }

    private static String describeDifference(Field field, Object legacy, Object modern) {
        boolean redact = SENSITIVE_FIELDS.contains(field.getName())
                || legacy instanceof String || modern instanceof String;
        return redact
                ? field.getName()
                : field.getName() + " (legacy=" + legacy + ", modern=" + modern + ")";
    }

    private static boolean valuesEqual(Object left, Object right) {
        if (left instanceof BigDecimal a && right instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return Objects.equals(left, right);
    }

    private static Object comparable(Field field, Object value) {
        if (value == null || !field.isAnnotationPresent(ManyToOne.class)) {
            return value;
        }
        try {
            // Via the getter so Hibernate proxies resolve their id without initialization.
            return value.getClass().getMethod("getId").invoke(value);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot read id of " + field.getName(), e);
        }
    }

    private static Object read(Field field, Object entity) {
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read " + field.getName(), e);
        }
    }
}
