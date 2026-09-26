package com.workshop.loanservice.e2e.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.routing.ServiceSelectionInterceptor;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Task 4 validation: every read endpoint is requested twice against one running instance, once
 * forced to the legacy {@code CDW_*} path and once to the normalized path via the {@code
 * serviceImpl} query parameter. Both responses must match each other and the golden file under
 * {@code test-data/e2e/golden/}, which was captured from the normalized (canonical) path.
 *
 * <p>Documented, intentional differences (see docs/MIGRATION_LOG.md, "Task 4 validation"):
 *
 * <ul>
 *   <li>Numeric scale: legacy strings yield {@code 285000} / {@code 4.75}, the typed normalized
 *       columns yield {@code 285000.00} / {@code 4.750}. Amounts are compared by value.
 *   <li>Code expansion style: the legacy translator produces title-case labels ({@code Active},
 *       {@code Regular}, {@code Posted}) while the V4 migration stored upper-case constants ({@code
 *       ACTIVE}, {@code REGULAR}, {@code POSTED}). {@code propertyType} additionally differs in
 *       wording ({@code Single Family Residence} vs {@code Single Family}, {@code Multi-Family
 *       Residence} vs {@code Multi-Family}). These fields are compared through {@link
 *       #sameCode(String, String)}.
 * </ul>
 *
 * All other fields (identifiers, names, dates, addresses, ordering, list sizes) must be identical.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-test")
class MigrationValidationE2ETest {

  private static final String GOLDEN_DIR = "test-data/e2e/golden/";
  private static final String LOAN_ID = "LN-2019-00142";
  private static final String BORROWER_ID = "B-10001";

  private static final ParameterizedTypeReference<List<LoanSummaryDto>> LOAN_LIST =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<BorrowerDto>> BORROWER_LIST =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<PaymentDto>> PAYMENT_LIST =
      new ParameterizedTypeReference<>() {};

  /** Legacy label to normalized constant for values whose wording differs beyond case. */
  private static final Map<String, String> LEGACY_TO_NORMALIZED_CODES =
      Map.of(
          "SINGLE FAMILY RESIDENCE", "SINGLE FAMILY",
          "MULTI-FAMILY RESIDENCE", "MULTI-FAMILY");

  private static final String[] CODE_FIELDS = {
    "status", "propertyType", "type", "loans.status", "loans.propertyType"
  };

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void listLoansIsIdenticalAcrossDataSourcesAndMatchesGoldenFile() {
    verify("/api/loans", LOAN_LIST, "loans.json", new TypeReference<List<LoanSummaryDto>>() {});
  }

  @Test
  void getLoanIsIdenticalAcrossDataSourcesAndMatchesGoldenFile() {
    verify(
        "/api/loans/" + LOAN_ID,
        new ParameterizedTypeReference<LoanSummaryDto>() {},
        "loan-" + LOAN_ID + ".json",
        new TypeReference<LoanSummaryDto>() {});
  }

  @Test
  void listBorrowersIsIdenticalAcrossDataSourcesAndMatchesGoldenFile() {
    verify(
        "/api/borrowers",
        BORROWER_LIST,
        "borrowers.json",
        new TypeReference<List<BorrowerDto>>() {});
  }

  @Test
  void getBorrowerIsIdenticalAcrossDataSourcesAndMatchesGoldenFile() {
    verify(
        "/api/borrowers/" + BORROWER_ID,
        new ParameterizedTypeReference<BorrowerDto>() {},
        "borrower-" + BORROWER_ID + ".json",
        new TypeReference<BorrowerDto>() {});
  }

  @Test
  void listLoanPaymentsIsIdenticalAcrossDataSourcesAndMatchesGoldenFile() {
    verify(
        "/api/loans/" + LOAN_ID + "/payments",
        PAYMENT_LIST,
        "payments-" + LOAN_ID + ".json",
        new TypeReference<List<PaymentDto>>() {});
  }

  @Test
  void paymentHistoryAliasIsIdenticalAcrossDataSourcesAndMatchesGoldenFile() {
    verify(
        "/api/payments/loan/" + LOAN_ID,
        PAYMENT_LIST,
        "payments-" + LOAN_ID + ".json",
        new TypeReference<List<PaymentDto>>() {});
  }

  private <T> void verify(
      String path,
      ParameterizedTypeReference<T> responseType,
      String goldenFile,
      TypeReference<T> goldenType) {
    T legacy = fetch(path, "legacy", responseType);
    T normalized = fetch(path, "normalized", responseType);
    T golden = loadGolden(goldenFile, goldenType);

    assertThat(normalized)
        .usingRecursiveComparison(strictConfiguration())
        .as("normalized response for %s vs golden file %s", path, goldenFile)
        .isEqualTo(golden);
    assertThat(legacy)
        .usingRecursiveComparison(legacyTolerantConfiguration())
        .as("legacy response for %s vs golden file %s", path, goldenFile)
        .isEqualTo(golden);
    assertThat(legacy)
        .usingRecursiveComparison(legacyTolerantConfiguration())
        .as("legacy vs normalized response for %s", path)
        .isEqualTo(normalized);
  }

  private <T> T fetch(String path, String implementation, ParameterizedTypeReference<T> type) {
    String url = path + "?" + ServiceSelectionInterceptor.PARAM + "=" + implementation;
    ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, null, type);
    assertThat(response.getStatusCode()).as("status of %s", url).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).as("body of %s", url).isNotNull();
    return response.getBody();
  }

  private static <T> T loadGolden(String fileName, TypeReference<T> type) {
    try (InputStream in = new ClassPathResource(GOLDEN_DIR + fileName).getInputStream()) {
      return objectMapper.readValue(in, type);
    } catch (IOException e) {
      throw new IllegalStateException("cannot read golden file " + fileName, e);
    }
  }

  /** Field-by-field equality; only BigDecimal scale is ignored (285000 equals 285000.00). */
  private static RecursiveComparisonConfiguration strictConfiguration() {
    RecursiveComparisonConfiguration configuration = new RecursiveComparisonConfiguration();
    configuration.registerComparatorForType(BigDecimal::compareTo, BigDecimal.class);
    return configuration;
  }

  /** Strict configuration plus the documented code-expansion tolerance on the code fields. */
  private static RecursiveComparisonConfiguration legacyTolerantConfiguration() {
    RecursiveComparisonConfiguration configuration = strictConfiguration();
    BiPredicate<String, String> sameCode = MigrationValidationE2ETest::sameCode;
    configuration.registerEqualsForFields(sameCode, CODE_FIELDS);
    return configuration;
  }

  static boolean sameCode(String left, String right) {
    if (left == null || right == null) {
      return left == null && right == null;
    }
    return canonicalCode(left).equals(canonicalCode(right));
  }

  private static String canonicalCode(String value) {
    String upper = value.trim().toUpperCase(Locale.ROOT);
    return LEGACY_TO_NORMALIZED_CODES.getOrDefault(upper, upper);
  }
}
