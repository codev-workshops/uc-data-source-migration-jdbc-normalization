package com.workshop.loanservice.contract;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the shared golden fixtures from {@code src/test/resources/golden/}.
 *
 * <p>The golden files ARE the API contract. There is one copy of each, shared
 * across every data-source parameter -- they are never duplicated per data source.
 */
final class GoldenFiles {

    private static final String GOLDEN_DIR = "golden/";

    private GoldenFiles() {
    }

    static String read(String fileName) {
        String resource = GOLDEN_DIR + fileName;
        try (InputStream in = GoldenFiles.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing golden fixture on classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read golden fixture: " + resource, e);
        }
    }
}
