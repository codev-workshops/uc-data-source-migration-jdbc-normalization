package com.workshop.loanservice.e2e.legacy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Shared setup for the legacy-mode end-to-end tests: a real servlet container on a random port, a
 * Flyway-migrated H2 database and an HTTP client.
 *
 * <p>These tests document the behavior of the legacy data path, including the failures caused by
 * the absence of exception handling.
 *
 * @deprecated Exercises the deprecated legacy CDW_* data path; superseded by the normalized test
 *     suites and {@link com.workshop.loanservice.service.NormalizedLoanService}.
 */
@Deprecated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-test")
@TestPropertySource(properties = "application.data-mode=legacy")
abstract class BaseLegacyE2ETest {

  static final String TRUNCATE_ALL = "classpath:test-data/e2e/legacy/truncate-all.sql";
  static final String RESTORE_SEED = "classpath:test-data/e2e/legacy/truncate-all-cleanup.sql";

  @Autowired TestRestTemplate restTemplate;
}
