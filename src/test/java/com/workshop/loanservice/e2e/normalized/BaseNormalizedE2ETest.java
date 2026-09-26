package com.workshop.loanservice.e2e.normalized;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared setup for the normalized-mode end-to-end tests: a real servlet container on a random port,
 * a Flyway-migrated H2 database and an HTTP client.
 *
 * <p>These tests assert the DTO contract originally baselined against the legacy CDW_* tables,
 * now served from the normalized tables.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-test")
abstract class BaseNormalizedE2ETest {

  static final String TRUNCATE_ALL = "classpath:test-data/e2e/normalized/truncate-all.sql";
  static final String RESTORE_SEED = "classpath:test-data/e2e/normalized/truncate-all-cleanup.sql";

  @Autowired TestRestTemplate restTemplate;
}
