package com.workshop.loanservice.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A static guard against the way SQL injection would actually get into this codebase.
 *
 * <p>Every query in the application is either a Spring Data derived method or a constant JPQL string
 * with named parameters. That property is easy to lose in a later change and impossible to notice in
 * review, so it is asserted here instead of documented.
 */
class NoDynamicSqlSourceGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    /** A line that looks like it contains SQL or JPQL inside a string literal. */
    private static final Pattern QUERY_LITERAL =
        Pattern.compile("(?i)\"[^\"]*\\b(select|insert|update|delete|where|order by)\\b");

    /** A string literal glued to an expression, i.e. a value about to become query syntax. */
    private static final Pattern LITERAL_PLUS_EXPRESSION = Pattern.compile("\"\\s*\\+\\s*[A-Za-z_(]");

    private static final List<String> FORBIDDEN_APIS = List.of(
        "createNativeQuery",
        "nativeQuery = true",
        "JdbcTemplate",
        "Statement.execute",
        "createStatement");

    @Test
    void noSourceFileConcatenatesUserInputIntoAQuery() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            List<String> offenders = files
                .filter(p -> p.toString().endsWith(".java"))
                .filter(NoDynamicSqlSourceGuardTest::concatenatesQuery)
                .map(Path::toString)
                .toList();
            assertThat(offenders).isEmpty();
        }
    }

    @Test
    void noSourceFileUsesARawSqlEscapeHatch() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            List<String> offenders = files
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> FORBIDDEN_APIS.stream().anyMatch(api -> read(p).contains(api)))
                .map(Path::toString)
                .toList();
            assertThat(offenders).isEmpty();
        }
    }

    /** PII must not be interpolated into a log line, where it outlives every access control. */
    @Test
    void noSourceFileLogsSensitiveFields() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            List<String> offenders = files
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> read(p).lines().anyMatch(line ->
                    line.contains("log.") && (line.contains("Ssn") || line.contains("ssn")
                        || line.contains("dateOfBirth") || line.contains("annualIncome"))))
                .map(Path::toString)
                .toList();
            assertThat(offenders).isEmpty();
        }
    }

    private static boolean concatenatesQuery(Path path) {
        // Splitting a long @Query across several string literals is safe, so only a literal joined to
        // an expression counts - that is the shape where a caller's value becomes query syntax.
        return read(path).lines().anyMatch(line ->
            QUERY_LITERAL.matcher(line).find() && LITERAL_PLUS_EXPRESSION.matcher(line).find());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
