package com.workshop.loanservice.e2e;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared setup for the end-to-end baseline tests: a real servlet container on a random port,
 * the {@code legacy-baseline-test} profile (its own H2 database seeded by the app's own
 * {@code schema-legacy.sql} / {@code data-legacy.sql}) and an HTTP client.
 *
 * <p>These tests document the CURRENT behavior of the service, including the failures caused by
 * the absence of exception handling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("legacy-baseline-test")
abstract class BaseLegacyBaselineE2ETest {

  static final String TRUNCATE_ALL = "classpath:test-data/e2e/truncate-all.sql";
  static final String RESTORE_SEED = "classpath:test-data/e2e/truncate-all-cleanup.sql";

  @Autowired TestRestTemplate restTemplate;
}
