package com.workshop.loanservice.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
 * surfaces as HTTP 500 because the application declares no exception handling. It runs against its
 * own database so it cannot disturb the pure end-to-end classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("legacy-baseline-test")
@TestPropertySource(
    properties =
        "spring.datasource.url="
            + "jdbc:h2:mem:legacybaselinehybrid;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class LoanEndpointsDataAccessFailureHybridTest {

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private LegacyLoanProductRepository loanProductRepository;

  @Test
  void listLoansReturnsServerErrorWhenRepositoryThrowsDataAccessException() {
    given(loanProductRepository.findAll())
        .willThrow(new DataAccessResourceFailureException("legacy warehouse unavailable"));

    ResponseEntity<String> response = restTemplate.getForEntity("/api/loans", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
