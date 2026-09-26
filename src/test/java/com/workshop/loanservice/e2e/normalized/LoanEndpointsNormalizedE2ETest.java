package com.workshop.loanservice.e2e.normalized;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import com.workshop.loanservice.dto.LoanSummaryDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

/** Normalized-mode coverage of {@code /api/loans} and {@code /api/loans/{id}}. */
class LoanEndpointsNormalizedE2ETest extends BaseNormalizedE2ETest {

  private static final ParameterizedTypeReference<List<LoanSummaryDto>> LOAN_LIST =
      new ParameterizedTypeReference<>() {};

  @Test
  void listLoansReturnsMigratedLoansWithTranslatedFields() {
    ResponseEntity<List<LoanSummaryDto>> response =
        restTemplate.exchange("/api/loans", HttpMethod.GET, null, LOAN_LIST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<LoanSummaryDto> loans = response.getBody();
    assertThat(loans).hasSize(5);
    assertThat(loans)
        .extracting(LoanSummaryDto::getLoanAccountNumber)
        .containsExactlyInAnyOrder(
            "LN-2019-00142",
            "LN-2020-00398",
            "LN-2018-00089",
            "LN-2021-00567",
            "LN-2017-00034");

    LoanSummaryDto loan = findLoan(loans, "LN-2019-00142");
    assertThat(loan.getBorrowerName()).isEqualTo("James Mitchell");
    assertThat(loan.getProductDescription()).isEqualTo("30-Year Fixed Rate Mortgage");
    assertThat(loan.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("285000"));
    assertThat(loan.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("271432.56"));
    assertThat(loan.getInterestRate()).isEqualByComparingTo(new BigDecimal("4.750"));
    assertThat(loan.getMonthlyPayment()).isEqualByComparingTo(new BigDecimal("1487.02"));
    assertThat(loan.getStatus()).isEqualTo("Active");
    assertThat(loan.getOriginationDate()).isEqualTo("02/15/2019");
    assertThat(loan.getPropertyAddress()).isEqualTo("742 Elm Street, Springfield, IL 62701");
    assertThat(loan.getPropertyType()).isEqualTo("Single Family Residence");
  }

  @Test
  @Sql(scripts = TRUNCATE_ALL, executionPhase = BEFORE_TEST_METHOD)
  @Sql(scripts = RESTORE_SEED, executionPhase = AFTER_TEST_METHOD)
  void listLoansReturnsEmptyListWhenNoLoansExist() {
    ResponseEntity<List<LoanSummaryDto>> response =
        restTemplate.exchange("/api/loans", HttpMethod.GET, null, LOAN_LIST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }

  @Test
  void getLoanByIdReturnsTranslatedLoan() {
    ResponseEntity<LoanSummaryDto> response =
        restTemplate.getForEntity("/api/loans/LN-2018-00089", LoanSummaryDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    LoanSummaryDto loan = response.getBody();
    assertThat(loan).isNotNull();
    assertThat(loan.getBorrowerName()).isEqualTo("Michael Torres");
    assertThat(loan.getProductDescription()).isEqualTo("5/1 Adjustable Rate Mortgage");
    assertThat(loan.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("178234.12"));
    assertThat(loan.getStatus()).isEqualTo("Active");
    assertThat(loan.getOriginationDate()).isEqualTo("07/01/2018");
    assertThat(loan.getPropertyAddress()).isEqualTo("305 Pine Road, Austin, TX 78701");
    assertThat(loan.getPropertyType()).isEqualTo("Single Family Residence");
  }

  @Test
  void getLoanByIdReturnsServerErrorForUnknownId() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/loans/LN-DOES-NOT-EXIST", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private static LoanSummaryDto findLoan(List<LoanSummaryDto> loans, String loanAccountNumber) {
    assertThat(loans).isNotNull();
    return loans.stream()
        .filter(loan -> loanAccountNumber.equals(loan.getLoanAccountNumber()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Loan not present in response: " + loanAccountNumber));
  }
}
