package com.workshop.loanservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Compares every REST endpoint response against the golden JSON captured from the
 * legacy-backed service (before LoanService was rewired). Responses must be byte-for-byte
 * identical apart from the documented differences below.
 *
 * <p>Intentional difference: {@code loan_accounts.original_amount} is DECIMAL(12,2), so
 * whole-dollar amounts the legacy string stored as {@code "285,000"} now serialize as
 * {@code 285000.00} instead of {@code 285000}. The values are numerically identical.
 *
 * <p>Run with {@code -Dgolden.update=true} only to re-capture the baseline from a legacy build.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiGoldenFileTest {

  private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden");
  private static final boolean UPDATE_GOLDEN = Boolean.getBoolean("golden.update");

  private static final List<String> LOAN_ACCOUNT_NUMBERS = List.of(
      "LN-2019-00142", "LN-2020-00398", "LN-2018-00089", "LN-2021-00567", "LN-2017-00034");
  private static final List<String> BORROWER_IDS = List.of(
      "B-10001", "B-10002", "B-10003", "B-10004", "B-10005");

  /** Whole-dollar originalAmount gained a two-decimal scale (see class comment). */
  private static final Pattern ORIGINAL_AMOUNT_SCALE =
      Pattern.compile("(\"originalAmount\":-?\\d+)\\.00(?=[,}])");

  @Autowired
  private MockMvc mockMvc;

  static Stream<Arguments> endpoints() {
    Stream<Arguments> lists = Stream.of(
        Arguments.of("loans.json", "/api/loans"),
        Arguments.of("borrowers.json", "/api/borrowers"));
    Stream<Arguments> loans = LOAN_ACCOUNT_NUMBERS.stream()
        .map(id -> Arguments.of("loan_" + id + ".json", "/api/loans/" + id));
    Stream<Arguments> payments = LOAN_ACCOUNT_NUMBERS.stream()
        .map(id -> Arguments.of("payments_" + id + ".json", "/api/loans/" + id + "/payments"));
    Stream<Arguments> borrowers = BORROWER_IDS.stream()
        .map(id -> Arguments.of("borrower_" + id + ".json", "/api/borrowers/" + id));
    return Stream.of(lists, loans, payments, borrowers).flatMap(s -> s);
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("endpoints")
  void responseMatchesLegacyBaseline(String goldenFile, String uri) throws Exception {
    String actual = mockMvc.perform(get(uri))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
    Path golden = GOLDEN_DIR.resolve(goldenFile);

    if (UPDATE_GOLDEN) {
      Files.createDirectories(GOLDEN_DIR);
      Files.writeString(golden, actual, StandardCharsets.UTF_8);
      return;
    }

    assertEquals(Files.readString(golden, StandardCharsets.UTF_8),
        withoutDocumentedDifferences(actual),
        () -> uri + " differs from golden file " + golden);
  }

  static String withoutDocumentedDifferences(String json) {
    return ORIGINAL_AMOUNT_SCALE.matcher(json).replaceAll("$1");
  }
}
