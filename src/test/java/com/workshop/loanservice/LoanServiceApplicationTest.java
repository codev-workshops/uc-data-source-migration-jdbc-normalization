package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test to verify that the Spring application context loads successfully.
 * This confirms the test infrastructure (H2 database, legacy schema initialization,
 * JPA entity mapping) is working correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoanServiceApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the application context starts without errors.
        // If this test fails, it indicates a configuration or wiring issue.
    }
}
