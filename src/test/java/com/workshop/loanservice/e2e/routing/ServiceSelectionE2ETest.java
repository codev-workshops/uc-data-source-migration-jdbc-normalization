package com.workshop.loanservice.e2e.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.routing.LoanServiceRegistry;
import com.workshop.loanservice.routing.LoanServiceRouter;
import com.workshop.loanservice.routing.ServiceImplementation;
import com.workshop.loanservice.service.LegacyLoanService;
import com.workshop.loanservice.service.LoanQueryService;
import com.workshop.loanservice.service.NormalizedLoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the {@code serviceImpl} query parameter selects the data path per request. The two
 * paths are distinguishable by the loan status: the legacy translator expands {@code ACT} to
 * {@code Active}, while the normalized schema stores {@code ACTIVE}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-test")
class ServiceSelectionE2ETest {

  private static final String LOAN_PATH = "/api/loans/LN-2019-00142";

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private LoanServiceRegistry registry;
  @Autowired private LoanQueryService primaryLoanQueryService;
  @Autowired private LegacyLoanService legacyLoanService;
  @Autowired private NormalizedLoanService normalizedLoanService;

  @Test
  void bothImplementationsAreRegisteredAndRouterIsPrimary() {
    assertThat(primaryLoanQueryService).isInstanceOf(LoanServiceRouter.class);
    assertThat(registry.get(ServiceImplementation.LEGACY)).isSameAs(legacyLoanService);
    assertThat(registry.get(ServiceImplementation.NORMALIZED)).isSameAs(normalizedLoanService);
  }

  @Test
  void noParameterRoutesToNormalized() {
    assertThat(fetchStatus(LOAN_PATH)).isEqualTo("ACTIVE");
  }

  @ParameterizedTest
  @ValueSource(strings = {"legacy", "LEGACY", "Legacy"})
  void legacyParameterRoutesToLegacy(String value) {
    assertThat(fetchStatus(LOAN_PATH + "?serviceImpl=" + value)).isEqualTo("Active");
  }

  @ParameterizedTest
  @ValueSource(strings = {"normalized", "modern", "", "unknown"})
  void normalizedOrUnknownParameterRoutesToNormalized(String value) {
    assertThat(fetchStatus(LOAN_PATH + "?serviceImpl=" + value)).isEqualTo("ACTIVE");
  }

  @Test
  void selectionIsPerRequest() {
    assertThat(fetchStatus(LOAN_PATH + "?serviceImpl=legacy")).isEqualTo("Active");
    assertThat(fetchStatus(LOAN_PATH)).isEqualTo("ACTIVE");
    assertThat(fetchStatus(LOAN_PATH + "?serviceImpl=legacy")).isEqualTo("Active");
  }

  private String fetchStatus(String url) {
    ResponseEntity<LoanSummaryDto> response = restTemplate.getForEntity(url, LoanSummaryDto.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    return response.getBody().getStatus();
  }
}
