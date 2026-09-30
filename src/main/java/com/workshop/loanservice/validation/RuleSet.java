package com.workshop.loanservice.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Ordered collection of {@link ValidationRule}s for one legacy table, with builder helpers
 * for the recurring column checks (required, date, amount, integer, code list).
 *
 * <p>Format checks skip null values; nullness is reported only by {@link #required} or
 * {@link #warnIfNull}, so a missing value yields exactly one finding.
 *
 * @param <T> legacy entity type
 */
public final class RuleSet<T> {

    private final String table;
    private final Function<T, String> idExtractor;
    private final List<ValidationRule<T>> rules = new ArrayList<>();

    private RuleSet(String table, Function<T, String> idExtractor) {
        this.table = table;
        this.idExtractor = idExtractor;
    }

    public static <T> RuleSet<T> forTable(String table, Function<T, String> idExtractor) {
        return new RuleSet<>(table, idExtractor);
    }

    public String table() {
        return table;
    }

    public String recordId(T record) {
        return idExtractor.apply(record);
    }

    public RuleSet<T> rule(ValidationRule<T> rule) {
        rules.add(rule);
        return this;
    }

    public RuleSet<T> check(Severity severity, String column, Predicate<T> violated,
                            Function<T, String> message) {
        return rule(record -> violated.test(record)
                ? Stream.of(finding(severity, record, column, message.apply(record)))
                : Stream.empty());
    }

    public RuleSet<T> required(String column, Function<T, String> getter) {
        return check(Severity.ERROR, column, r -> LegacyValues.isBlank(getter.apply(r)),
                r -> "required value is missing");
    }

    public RuleSet<T> warnIfNull(String column, Function<T, String> getter) {
        return check(Severity.WARNING, column, r -> getter.apply(r) == null,
                r -> "value is null");
    }

    public RuleSet<T> date(String column, Function<T, String> getter) {
        return check(Severity.ERROR, column,
                r -> getter.apply(r) != null && LegacyValues.parseDate(getter.apply(r)).isEmpty(),
                r -> quote(getter.apply(r)) + " is not a valid MM/DD/YYYY date");
    }

    public RuleSet<T> amount(String column, Function<T, String> getter) {
        return check(Severity.ERROR, column,
                r -> getter.apply(r) != null && LegacyValues.parseAmount(getter.apply(r)).isEmpty(),
                r -> quote(getter.apply(r)) + " is not a parseable amount");
    }

    public RuleSet<T> amountAtLeast(String column, Function<T, String> getter, BigDecimal min) {
        amount(column, getter);
        return check(Severity.ERROR, column,
                r -> LegacyValues.parseAmount(getter.apply(r))
                        .filter(v -> v.compareTo(min) < 0).isPresent(),
                r -> quote(getter.apply(r)) + " is below minimum " + min.toPlainString());
    }

    public RuleSet<T> decimalInRange(String column, Function<T, String> getter,
                                     BigDecimal min, BigDecimal max) {
        return check(Severity.ERROR, column,
                r -> getter.apply(r) != null && LegacyValues.parseDecimal(getter.apply(r))
                        .filter(v -> v.compareTo(min) >= 0 && v.compareTo(max) <= 0).isEmpty(),
                r -> quote(getter.apply(r)) + " is not a decimal in range "
                        + min.toPlainString() + "-" + max.toPlainString());
    }

    public RuleSet<T> integerInRange(String column, Function<T, String> getter, int min, int max) {
        return check(Severity.ERROR, column,
                r -> getter.apply(r) != null && LegacyValues.parseInteger(getter.apply(r))
                        .filter(v -> v >= min && v <= max).isEmpty(),
                r -> quote(getter.apply(r)) + " is not an integer in range " + min + "-" + max);
    }

    public RuleSet<T> oneOf(String column, Function<T, String> getter, Set<String> allowed) {
        return check(Severity.ERROR, column, r -> !allowed.contains(getter.apply(r)),
                r -> quote(getter.apply(r)) + " is not one of " + allowed.stream().sorted().toList());
    }

    public List<ValidationFinding> validate(Collection<T> records) {
        return records.stream()
                .flatMap(record -> rules.stream().flatMap(rule -> rule.validate(record)))
                .toList();
    }

    public ValidationFinding finding(Severity severity, T record, String column, String message) {
        return new ValidationFinding(severity, table, recordId(record), column, message);
    }

    private static String quote(String value) {
        return value == null ? "null" : "'" + value + "'";
    }
}
