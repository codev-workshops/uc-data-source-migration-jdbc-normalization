package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Shares the cached application context with {@link ApiParityTest}: the legacy
 * and modern H2 databases live for the whole JVM ({@code DB_CLOSE_DELAY=-1}),
 * so a second context would re-run {@code schema-legacy.sql} against tables
 * that already exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoanServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
