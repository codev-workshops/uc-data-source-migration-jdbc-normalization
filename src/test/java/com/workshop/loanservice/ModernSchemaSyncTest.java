package com.workshop.loanservice;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Keeps the runtime {@code schema-modern.sql} in sync with the reference DDL in {@code data/modern-schema}. */
class ModernSchemaSyncTest {

    @Test
    void runtimeSchemaMatchesReferenceDdl() throws IOException {
        String reference = Files.readString(Path.of("data/modern-schema/modern_tables.sql"));
        String runtime = new ClassPathResource("schema-modern.sql").getContentAsString(StandardCharsets.UTF_8);

        assertThat(normalize(runtime)).isEqualTo(normalize(reference));
    }

    /** Ignores comments, whitespace and the {@code IF NOT EXISTS} guards that make the runtime copy re-runnable. */
    private static String normalize(String ddl) {
        return ddl.lines()
                .map(line -> line.replaceFirst("--.*$", ""))
                .collect(joining(" "))
                .replace("IF NOT EXISTS ", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
