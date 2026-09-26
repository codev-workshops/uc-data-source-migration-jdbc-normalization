package com.workshop.loanservice.e2e.normalized;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.workshop.loanservice.dto.ErrorResponse;
import com.workshop.loanservice.repository.normalized.LoanAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Hybrid test: real HTTP stack with a mocked repository, documenting that a persistence failure
 * surfaces as HTTP 500 with a {@code DATA_ACCESS_ERROR} body. It runs in normalized mode against
 * its own database so it cannot disturb the pure end-to-end classes.
 *
 * <p>Mocking a concrete repository keeps this class separate from the interface-based e2e suites.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-test")
@TestPropertySource(
    properties =
        "spring.datasource.url="
            + "jdbc:h2:mem:normalizedhybrid;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class LoanEndpointsDataAccessFailureNormalizedHybridTest {

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private LoanAccountRepository loanAccountRepository;

  @Test
  void listLoansReturnsServerErrorWhenRepositoryThrowsDataAccessException() {
    given(loanAccountRepository.findAll())
        .willThrow(new DataAccessResourceFailureException("normalized database unavailable"));

    ResponseEntity<ErrorResponse> response =
        restTemplate.getForEntity("/api/loans", ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(500);
    assertThat(body.getError()).isEqualTo("Internal Server Error");
    assertThat(body.getCode()).isEqualTo("DATA_ACCESS_ERROR");
    assertThat(body.getMessage()).doesNotContain("normalized database unavailable");
    assertThat(body.getPath()).isEqualTo("/api/loans");
  }
}
