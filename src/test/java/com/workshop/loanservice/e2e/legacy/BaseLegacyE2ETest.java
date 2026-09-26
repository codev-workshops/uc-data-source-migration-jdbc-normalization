package com.workshop.loanservice.e2e.legacy;

import com.workshop.loanservice.routing.ServiceSelectionInterceptor;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Shared setup for the legacy-mode end-to-end tests: a real servlet container on a random port, a
 * Flyway-migrated H2 database and an HTTP client that appends {@code serviceImpl=legacy} to every
 * request so the router selects {@link com.workshop.loanservice.service.LegacyLoanService}.
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
@Import(BaseLegacyE2ETest.LegacySelectionConfig.class)
abstract class BaseLegacyE2ETest {

  static final String TRUNCATE_ALL = "classpath:test-data/e2e/legacy/truncate-all.sql";
  static final String RESTORE_SEED = "classpath:test-data/e2e/legacy/truncate-all-cleanup.sql";

  @Autowired TestRestTemplate restTemplate;

  @TestConfiguration
  static class LegacySelectionConfig {

    @Bean
    RestTemplateBuilder legacySelectingRestTemplateBuilder() {
      return new RestTemplateBuilder().additionalInterceptors(new LegacySelectionInterceptor());
    }
  }

  static final class LegacySelectionInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
      URI uri =
          UriComponentsBuilder.fromUri(request.getURI())
              .queryParam(ServiceSelectionInterceptor.PARAM, "legacy")
              .build(true)
              .toUri();
      return execution.execute(
          new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
              return uri;
            }
          },
          body);
    }
  }
}
