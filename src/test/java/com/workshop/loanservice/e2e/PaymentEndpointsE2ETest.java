package com.workshop.loanservice.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import com.workshop.loanservice.dto.PaymentDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

/** Baseline coverage of {@code /api/loans/{loanId}/payments}. */
class PaymentEndpointsE2ETest extends BaseLegacyBaselineE2ETest {

  private static final ParameterizedTypeReference<List<PaymentDto>> PAYMENT_LIST =
      new ParameterizedTypeReference<>() {};

  @Test
  void listPaymentsReturnsPaymentsNewestFirstWithTranslatedCodes() {
    ResponseEntity<List<PaymentDto>> response =
        restTemplate.exchange(
            "/api/loans/LN-2019-00142/payments", HttpMethod.GET, null, PAYMENT_LIST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<PaymentDto> payments = response.getBody();
    assertThat(payments)
        .extracting(PaymentDto::getPaymentId)
        .containsExactly("PMT-2025120001", "PMT-2025110001");

    PaymentDto latest = payments.get(0);
    assertThat(latest.getLoanAccountNumber()).isEqualTo("LN-2019-00142");
    assertThat(latest.getPaymentDate()).isEqualTo("12/15/2025");
    assertThat(latest.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1487.02"));
    assertThat(latest.getPrincipalAmount()).isEqualByComparingTo(new BigDecimal("456.78"));
    assertThat(latest.getInterestAmount()).isEqualByComparingTo(new BigDecimal("1074.69"));
    assertThat(latest.getEscrowAmount()).isEqualByComparingTo(new BigDecimal("355.55"));
    assertThat(latest.getLateFee()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(latest.getType()).isEqualTo("Regular");
    assertThat(latest.getStatus()).isEqualTo("Posted");
  }

  @Test
  void listPaymentsReturnsEmptyListForUnknownLoan() {
    ResponseEntity<List<PaymentDto>> response =
        restTemplate.exchange(
            "/api/loans/LN-DOES-NOT-EXIST/payments", HttpMethod.GET, null, PAYMENT_LIST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }

  @Test
  @Sql(scripts = TRUNCATE_ALL, executionPhase = BEFORE_TEST_METHOD)
  @Sql(scripts = RESTORE_SEED, executionPhase = AFTER_TEST_METHOD)
  void listPaymentsReturnsEmptyListWhenNoPaymentsExist() {
    ResponseEntity<List<PaymentDto>> response =
        restTemplate.exchange(
            "/api/loans/LN-2019-00142/payments", HttpMethod.GET, null, PAYMENT_LIST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }

  @Test
  @Sql(
      scripts = "classpath:test-data/e2e/malformed-payment.sql",
      executionPhase = BEFORE_TEST_METHOD)
  @Sql(
      scripts = "classpath:test-data/e2e/malformed-payment-cleanup.sql",
      executionPhase = AFTER_TEST_METHOD)
  void listPaymentsReturnsServerErrorWhenAmountIsNotNumeric() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/loans/LN-2019-00142/payments", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
