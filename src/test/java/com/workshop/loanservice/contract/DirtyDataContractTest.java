package com.workshop.loanservice.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Edge-case contract: dirty legacy rows must surface as controlled {@code null}/{@code "Unknown"}
 * values plus violation metadata, never as silent zeros or raw codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "classpath:dirty-data.sql")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class DirtyDataContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void nullMiddleInitialProducesCleanFullName() throws Exception {
    JsonNode b = SchemaSupport.assertConforms(getJson("/api/borrowers/B-99901"), "borrower");
    assertThat(b.get("fullName").asText()).isEqualTo("Dirty Borrower");
    assertThat(b.get("fullName").asText()).doesNotContain("null");
  }

  @Test
  void outOfRangeCreditScoreIsNullWithViolation() throws Exception {
    JsonNode b = SchemaSupport.assertConforms(getJson("/api/borrowers/B-99901"), "borrower");
    assertThat(b.get("creditScore").isNull()).isTrue();
    assertThat(ruleIds(b)).contains("INT_RANGE");
  }

  @Test
  void malformedAmountIsNullWithViolationNotZero() throws Exception {
    JsonNode loan =
        SchemaSupport.assertConforms(getJson("/api/loans/LN-9999-99901"), "loan-summary");
    assertThat(loan.get("originalAmount").isNull()).isTrue();
    assertThat(loan.get("currentBalance").isNull()).isTrue();
    assertThat(loan.get("interestRate").isNull()).isTrue();
    assertThat(loan.get("monthlyPayment").isNull()).isTrue();
    List<JsonNode> amountViolations = new ArrayList<>();
    loan.get("dataQualityViolations")
        .forEach(
            v -> {
              if ("AMT_FORMAT".equals(v.get("ruleId").asText())) {
                amountViolations.add(v);
              }
            });
    assertThat(amountViolations)
        .extracting(v -> v.get("rawValue").asText())
        .contains("12,34,567", "1,000.000");
    assertThat(amountViolations).allMatch(v -> "ERROR".equals(v.get("severity").asText()));
  }

  @Test
  void unmappedCodesBecomeUnknownWithViolationNotRawPassthrough() throws Exception {
    JsonNode loan =
        SchemaSupport.assertConforms(getJson("/api/loans/LN-9999-99901"), "loan-summary");
    assertThat(loan.get("status").asText()).isEqualTo("Unknown");
    assertThat(loan.get("propertyType").asText()).isEqualTo("Unknown");
    List<String> rawCodes = new ArrayList<>();
    loan.get("dataQualityViolations")
        .forEach(
            v -> {
              if ("CODE_UNMAPPED".equals(v.get("ruleId").asText())) {
                rawCodes.add(v.get("rawValue").asText());
              }
            });
    assertThat(rawCodes).containsExactlyInAnyOrder("ZZZ", "MOB");
  }

  @Test
  void dirtyPaymentSurfacesNullAmountsAndUnknownCodes() throws Exception {
    JsonNode payments =
        SchemaSupport.assertConforms(getJson("/api/loans/LN-9999-99901/payments"), "payment");
    assertThat(payments.size()).isEqualTo(1);
    JsonNode p = payments.get(0);
    assertThat(p.get("totalAmount").isNull()).isTrue();
    assertThat(p.get("principalAmount").isNull()).isTrue();
    assertThat(p.get("interestAmount").isNull()).isTrue();
    assertThat(p.get("escrowAmount").decimalValue()).isEqualByComparingTo("355.55");
    assertThat(p.get("type").asText()).isEqualTo("Unknown");
    assertThat(p.get("status").asText()).isEqualTo("Unknown");
    assertThat(ruleIds(p)).contains("AMT_FORMAT", "AMT_MISSING", "CODE_UNMAPPED");
  }

  @Test
  void reportFlagsDirtyRowsAcrossRules() throws Exception {
    JsonNode report =
        SchemaSupport.assertConforms(getJson("/api/data-quality/report"), "data-quality-report");
    JsonNode rules = report.get("rules");
    assertThat(rules.get("REF_ORPHAN").get("affectedRecords").toString())
        .contains("CDW_LN_ACCT:LN-9999-99901");
    assertThat(rules.get("DATE_CALENDAR").get("affectedRecords").toString())
        .contains("CDW_BORR_MSTR:B-99901");
    assertThat(rules.get("DATE_CALENDAR").get("affectedRecords").toString())
        .contains("CDW_LN_ACCT:LN-9999-99901");
    assertThat(rules.get("INT_RANGE").get("affectedRecords").toString())
        .contains("CDW_BORR_MSTR:B-99901");
    assertThat(rules.get("CODE_UNMAPPED").get("count").asInt()).isGreaterThanOrEqualTo(5);
    JsonNode dirtyLoan = null;
    for (JsonNode r : report.get("records")) {
      if ("LN-9999-99901".equals(r.get("recordKey").asText())) {
        dirtyLoan = r;
      }
    }
    assertThat(dirtyLoan).isNotNull();
    assertThat(dirtyLoan.get("score").asInt()).isEqualTo(0);
  }

  private String getJson(String path) throws Exception {
    return mockMvc
        .perform(get(path))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static List<String> ruleIds(JsonNode record) {
    List<String> ids = new ArrayList<>();
    record.get("dataQualityViolations").forEach(v -> ids.add(v.get("ruleId").asText()));
    return ids;
  }
}
