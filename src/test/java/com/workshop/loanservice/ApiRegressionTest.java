package com.workshop.loanservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Compares every API response against {@code golden/api-responses.json} and verifies parity with
 * the pre-migration snapshot in {@code golden/legacy-api-responses.json}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiRegressionTest {

  private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final Pattern LEGACY_DATE = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");

  /** Intentional value changes: legacy display labels to codes expanded per column_mappings.md. */
  private static final Map<String, Map<String, String>> LEGACY_TO_MODERN_CODES =
      Map.of(
          "status",
          Map.of(
              "Active", "ACTIVE",
              "Closed", "CLOSED",
              "Default", "DEFAULT",
              "Forbearance", "FORBEARANCE",
              "Posted", "POSTED",
              "Reversed", "REVERSED",
              "Non-Sufficient Funds", "NSF",
              "Pending", "PENDING"),
          "propertyType",
          Map.of(
              "Single Family Residence", "Single Family",
              "Multi-Family Residence", "Multi-Family"),
          "type",
          Map.of(
              "Regular", "REGULAR",
              "Extra", "EXTRA",
              "Partial", "PARTIAL",
              "Prepayment", "PREPAYMENT"));

  private static final Set<String> DATE_FIELDS = Set.of("originationDate", "paymentDate");

  private static final Set<String> AMOUNT_FIELDS =
      Set.of(
          "originalAmount",
          "currentBalance",
          "interestRate",
          "monthlyPayment",
          "totalAmount",
          "principalAmount",
          "interestAmount",
          "escrowAmount",
          "lateFee");

  private static final Set<String> MANDATORY_FIELDS =
      Set.of(
          "fullName",
          "borrowerName",
          "originalAmount",
          "originationDate",
          "paymentDate",
          "totalAmount");

  @Autowired private MockMvc mockMvc;

  @Test
  void goldenCoversAllEndpoints() {
    Set<String> templates =
        fieldNames(GoldenData.json("api-responses.json")).stream()
            .map(k -> k.replaceAll("LN-\\d{4}-\\d{5}", "{id}").replaceAll("B-\\d{5}", "{id}"))
            .collect(Collectors.toSet());
    assertThat(templates)
        .containsExactlyInAnyOrder(
            "GET /api/loans",
            "GET /api/loans/{id}",
            "GET /api/loans/{id}/payments",
            "GET /api/borrowers",
            "GET /api/borrowers/{id}");
    assertThat(fieldNames(GoldenData.json("legacy-api-responses.json")))
        .containsExactlyElementsOf(fieldNames(GoldenData.json("api-responses.json")));
  }

  @Test
  void responsesMatchGolden() throws Exception {
    for (Map.Entry<String, JsonNode> entry : entries(GoldenData.json("api-responses.json"))) {
      String actual = call(entry.getKey());
      JSONAssert.assertEquals(
          entry.getKey(), entry.getValue().toString(), actual, JSONCompareMode.STRICT);
    }
  }

  @Test
  void responsesMatchLegacySnapshotAfterDocumentedTransforms() throws Exception {
    for (Map.Entry<String, JsonNode> entry :
        entries(GoldenData.json("legacy-api-responses.json"))) {
      JsonNode expected = toModern(entry.getValue().deepCopy());
      JSONAssert.assertEquals(
          entry.getKey(), expected.toString(), call(entry.getKey()), JSONCompareMode.STRICT);
    }
  }

  @Test
  void mandatoryFieldsPresentDatesIsoAndAmountsNumeric() throws Exception {
    Map<String, String> borrowerNames = new LinkedHashMap<>();
    for (Map<String, Object> b : GoldenData.rows("borrowers.json")) {
      borrowerNames.put(
          (String) b.get("external_id"), b.get("first_name") + " " + b.get("last_name"));
    }
    for (Map.Entry<String, JsonNode> entry : entries(GoldenData.json("api-responses.json"))) {
      JsonNode actual = GoldenData.MAPPER.readTree(call(entry.getKey()));
      assertFieldRules(entry.getKey(), actual);
    }
    for (JsonNode loan : GoldenData.MAPPER.readTree(call("GET /api/loans"))) {
      assertThat(borrowerNames).containsValue(loan.get("borrowerName").asText());
    }
    for (JsonNode borrower : GoldenData.MAPPER.readTree(call("GET /api/borrowers"))) {
      String[] firstLast = borrowerNames.get(borrower.get("id").asText()).split(" ");
      assertThat(borrower.get("fullName").asText()).startsWith(firstLast[0]).endsWith(firstLast[1]);
    }
  }

  private void assertFieldRules(String path, JsonNode node) {
    if (node.isArray()) {
      node.forEach(child -> assertFieldRules(path, child));
      return;
    }
    if (!node.isObject()) {
      return;
    }
    for (Map.Entry<String, JsonNode> field : entries(node)) {
      String name = field.getKey();
      JsonNode value = field.getValue();
      String where = path + " ." + name;
      if (MANDATORY_FIELDS.contains(name)) {
        assertThat(value.isNull()).as(where + " is null").isFalse();
        assertThat(value.asText()).as(where + " is blank").isNotBlank();
      }
      if (DATE_FIELDS.contains(name) && !value.isNull()) {
        assertThat(value.asText()).as(where).matches(ISO_DATE);
        LocalDate.parse(value.asText());
      }
      if (AMOUNT_FIELDS.contains(name) && !value.isNull()) {
        assertThat(value.isNumber()).as(where + " is numeric").isTrue();
      }
      assertFieldRules(path, value);
    }
  }

  private String call(String key) throws Exception {
    String uri = key.substring("GET ".length());
    return mockMvc
        .perform(get(uri))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static JsonNode toModern(JsonNode node) {
    if (node.isArray()) {
      node.forEach(ApiRegressionTest::toModern);
    } else if (node instanceof ObjectNode obj) {
      for (Map.Entry<String, JsonNode> field : entries(obj)) {
        String name = field.getKey();
        JsonNode value = field.getValue();
        if (DATE_FIELDS.contains(name) && value.isTextual()) {
          obj.put(name, LEGACY_DATE.matcher(value.asText()).replaceAll("$3-$1-$2"));
        } else if (LEGACY_TO_MODERN_CODES.containsKey(name) && value.isTextual()) {
          obj.put(
              name, LEGACY_TO_MODERN_CODES.get(name).getOrDefault(value.asText(), value.asText()));
        } else {
          toModern(value);
        }
      }
    }
    return node;
  }

  private static List<Map.Entry<String, JsonNode>> entries(JsonNode node) {
    List<Map.Entry<String, JsonNode>> result = new ArrayList<>();
    node.fields().forEachRemaining(result::add);
    return result;
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    Iterator<String> it = node.fieldNames();
    it.forEachRemaining(names::add);
    return names;
  }
}
