package com.workshop.loanservice.e2e.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.ErrorResponse;
import com.workshop.loanservice.dto.LoanSummaryDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

/** Baseline coverage of {@code /api/borrowers} and {@code /api/borrowers/{id}}. */
class BorrowerEndpointsLegacyE2ETest extends BaseLegacyE2ETest {

  private static final ParameterizedTypeReference<List<BorrowerDto>> BORROWER_LIST =
      new ParameterizedTypeReference<>() {};

  @Test
  void listBorrowersReturnsSeededBorrowersWithTranslatedFields() {
    ResponseEntity<List<BorrowerDto>> response =
        restTemplate.exchange("/api/borrowers", HttpMethod.GET, null, BORROWER_LIST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<BorrowerDto> borrowers = response.getBody();
    assertThat(borrowers).hasSize(5);
    assertThat(borrowers)
        .extracting(BorrowerDto::getFullName)
        .containsExactlyInAnyOrder(
            "James R. Mitchell",
            "Sarah L. Chen",
            "Michael A. Torres",
            "Emily M. Johnson",
            "Robert Williams");

    BorrowerDto mitchell = findBorrower(borrowers, "B-10001");
    assertThat(mitchell.getFullName()).isEqualTo("James R. Mitchell");
    assertThat(mitchell.getEmail()).isEqualTo("j.mitchell@email.com");
    assertThat(mitchell.getPhone()).isEqualTo("217-555-0142");
    assertThat(mitchell.getCity()).isEqualTo("Springfield");
    assertThat(mitchell.getState()).isEqualTo("IL");
    assertThat(mitchell.getCreditScore()).isEqualTo(745);
    assertThat(mitchell.getEmploymentStatus()).isEqualTo("EMPLOYED");
    assertThat(mitchell.getLoans()).isNull();

    BorrowerDto williams = findBorrower(borrowers, "B-10005");
    assertThat(williams.getFullName()).isEqualTo("Robert Williams");
    assertThat(williams.getCreditScore()).isEqualTo(658);
  }

  @Test
  @Sql(scripts = TRUNCATE_ALL, executionPhase = BEFORE_TEST_METHOD)
  @Sql(scripts = RESTORE_SEED, executionPhase = AFTER_TEST_METHOD)
  void listBorrowersReturnsEmptyListWhenNoBorrowersExist() {
    ResponseEntity<List<BorrowerDto>> response =
        restTemplate.exchange("/api/borrowers", HttpMethod.GET, null, BORROWER_LIST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }

  @Test
  @Sql(
      scripts = "classpath:test-data/e2e/legacy/malformed-borrower.sql",
      executionPhase = BEFORE_TEST_METHOD)
  @Sql(
      scripts = "classpath:test-data/e2e/legacy/malformed-borrower-cleanup.sql",
      executionPhase = AFTER_TEST_METHOD)
  void listBorrowersReturnsServerErrorWhenCreditScoreIsNotNumeric() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/borrowers", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void getBorrowerByIdReturnsBorrowerWithLoans() {
    ResponseEntity<BorrowerDto> response =
        restTemplate.getForEntity("/api/borrowers/B-10001", BorrowerDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BorrowerDto borrower = response.getBody();
    assertThat(borrower).isNotNull();
    assertThat(borrower.getFullName()).isEqualTo("James R. Mitchell");
    assertThat(borrower.getCreditScore()).isEqualTo(745);
    assertThat(borrower.getLoans())
        .extracting(LoanSummaryDto::getLoanAccountNumber)
        .containsExactly("LN-2019-00142");
    assertThat(borrower.getLoans().get(0).getProductDescription())
        .isEqualTo("30-Year Fixed Rate Mortgage");
  }

  @Test
  @Sql(scripts = "classpath:test-data/e2e/legacy/borrower-no-loans.sql", executionPhase = BEFORE_TEST_METHOD)
  @Sql(
      scripts = "classpath:test-data/e2e/legacy/borrower-no-loans-cleanup.sql",
      executionPhase = AFTER_TEST_METHOD)
  void getBorrowerByIdReturnsEmptyLoansWhenBorrowerHasNoLoans() {
    ResponseEntity<BorrowerDto> response =
        restTemplate.getForEntity("/api/borrowers/B-90001", BorrowerDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BorrowerDto borrower = response.getBody();
    assertThat(borrower).isNotNull();
    assertThat(borrower.getFullName()).isEqualTo("Olivia T. Grant");
    assertThat(borrower.getLoans()).isEmpty();
  }

  @Test
  void getBorrowerByIdReturnsNotFoundForUnknownId() {
    ResponseEntity<ErrorResponse> response =
        restTemplate.getForEntity("/api/borrowers/B-DOES-NOT-EXIST", ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    ErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(404);
    assertThat(body.getError()).isEqualTo("Not Found");
    assertThat(body.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
    assertThat(body.getMessage()).isEqualTo("Borrower not found: B-DOES-NOT-EXIST");
    assertThat(body.getPath()).isEqualTo("/api/borrowers/B-DOES-NOT-EXIST");
    assertThat(body.getTimestamp()).isNotBlank();
  }

  @Test
  @Sql(scripts = "classpath:test-data/e2e/legacy/malformed-loan.sql", executionPhase = BEFORE_TEST_METHOD)
  @Sql(
      scripts = "classpath:test-data/e2e/legacy/malformed-loan-cleanup.sql",
      executionPhase = AFTER_TEST_METHOD)
  void getBorrowerByIdReturnsServerErrorWhenOneOfTheirLoansHasANonNumericAmount() {
    ResponseEntity<ErrorResponse> response =
        restTemplate.getForEntity("/api/borrowers/B-10001", ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().getPath()).isEqualTo("/api/borrowers/B-10001");
  }

  private static BorrowerDto findBorrower(List<BorrowerDto> borrowers, String borrowerId) {
    assertThat(borrowers).isNotNull();
    return borrowers.stream()
        .filter(borrower -> borrowerId.equals(borrower.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Borrower not present in response: " + borrowerId));
  }
}
