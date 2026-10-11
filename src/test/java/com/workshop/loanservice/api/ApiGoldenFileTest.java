package com.workshop.loanservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * legacy-backed service. Run with {@code -Dgolden.update=true} to (re)capture the baseline.
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

    assertEquals(Files.readString(golden, StandardCharsets.UTF_8), actual,
        () -> uri + " differs from golden file " + golden);
  }
}
