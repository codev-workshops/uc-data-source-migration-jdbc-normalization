package com.workshop.loanservice.e2e.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.workshop.loanservice.dto.ErrorResponse;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
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
 * surfaces as HTTP 500 with a {@code DATA_ACCESS_ERROR} body. It runs in legacy mode against its
 * own database so it cannot disturb the pure end-to-end classes. The legacy path is selected per
 * request with {@code serviceImpl=legacy}.
 *
 * <p>Mocking a concrete repository keeps this class separate from the interface-based e2e suites.
 *
 * @deprecated Exercises the deprecated legacy CDW_* data path; superseded by the normalized test
 *     suites and {@link com.workshop.loanservice.service.NormalizedLoanService}.
 */
@Deprecated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-test")
@TestPropertySource(
    properties = {
      "spring.datasource.url="
          + "jdbc:h2:mem:legacyhybrid;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
class LoanEndpointsDataAccessFailureLegacyHybridTest {

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private LegacyLoanProductRepository loanProductRepository;

  @Test
  void listLoansReturnsServerErrorWhenRepositoryThrowsDataAccessException() {
    given(loanProductRepository.findAll())
        .willThrow(new DataAccessResourceFailureException("legacy warehouse unavailable"));

    ResponseEntity<ErrorResponse> response =
        restTemplate.getForEntity("/api/loans?serviceImpl=legacy", ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(500);
    assertThat(body.getError()).isEqualTo("Internal Server Error");
    assertThat(body.getCode()).isEqualTo("DATA_ACCESS_ERROR");
    assertThat(body.getMessage()).doesNotContain("legacy warehouse unavailable");
    assertThat(body.getPath()).isEqualTo("/api/loans");
  }
}
