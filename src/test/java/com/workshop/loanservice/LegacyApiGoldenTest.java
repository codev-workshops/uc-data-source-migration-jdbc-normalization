package com.workshop.loanservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Golden-file regression for the public API reading from the legacy data source
 * ({@code loanservice.datasource=legacy}). This is the baseline that captured the
 * golden files in the first place.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "loanservice.datasource=legacy")
class LegacyApiGoldenTest extends AbstractApiGoldenTest {
}
