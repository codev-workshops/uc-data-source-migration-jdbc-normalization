package com.workshop.loanservice.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.repository.InvalidInputRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * API validation mode: {@code ?validate=true} validates inputs and returned records and writes
 * every violation to {@code DQ_INVALID_INPUT}; without it nothing is written.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "classpath:dirty-data.sql")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class ValidationModeContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private InvalidInputRepository repository;

  @BeforeEach
  void clearQuarantine() {
    repository.deleteAllInBatch();
  }

  @Test
  void defaultModeWritesNothing() throws Exception {
    mockMvc.perform(get("/api/loans/LN-9999-99901")).andExpect(status().isOk());
    mockMvc.perform(get("/api/loans")).andExpect(status().isOk());
    assertThat(repository.count()).isZero();
  }

  @Test
  void explicitFalseOverridesEvenWhenParamPresent() throws Exception {
    mockMvc.perform(get("/api/loans/LN-9999-99901?validate=false")).andExpect(status().isOk());
    assertThat(repository.count()).isZero();
  }

  @Test
  void loanByIdQuarantinesFieldAndReferenceViolations() throws Exception {
    JsonNode loan = json(mockMvc.perform(get("/api/loans/LN-9999-99901?validate=true")));
    SchemaSupport.assertConforms(loan.toString(), "loan-summary");

    JsonNode rows = json(mockMvc.perform(get("/api/data-quality/invalid-inputs")));
    assertThat(rows.size()).isGreaterThan(0);
    List<String> rules = rules(rows);
    assertThat(rules).contains("AMT_FORMAT", "CODE_UNMAPPED", "DATE_CALENDAR", "REF_ORPHAN");
    for (JsonNode r : rows) {
      assertThat(r.get("endpoint").asText()).isEqualTo("GET /api/loans/{id}");
      assertThat(r.get("inputName").asText()).isEqualTo("id");
      assertThat(r.get("inputValue").asText()).isEqualTo("LN-9999-99901");
      assertThat(r.get("entityType").asText()).isEqualTo("CDW_LN_ACCT");
      assertThat(r.get("recordKey").asText()).isEqualTo("LN-9999-99901");
      assertThat(r.get("severity").asText()).isIn("ERROR", "WARN");
    }
  }

  @Test
  void malformedInputIsRejectedWith400AndQuarantined() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/loans/DROP TABLE;--?validate=true"))
            .andExpect(status().isBadRequest())
            .andReturn();
    JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("dataQualityViolations").get(0).get("ruleId").asText())
        .isEqualTo("INPUT_UNSAFE");

    mockMvc.perform(get("/api/borrowers/10001?validate=true")).andExpect(status().isBadRequest());

    JsonNode rows = json(mockMvc.perform(get("/api/data-quality/invalid-inputs")));
    assertThat(rules(rows)).containsExactlyInAnyOrder("INPUT_UNSAFE", "INPUT_FORMAT");
    for (JsonNode r : rows) {
      assertThat(r.get("entityType").asText()).isEqualTo("API_INPUT");
    }
  }

  @Test
  void wellFormedButUnknownKeyIsQuarantinedAsNotFound() throws Exception {
    mockMvc.perform(get("/api/borrowers/B-00000?validate=true")).andExpect(status().isNotFound());
    JsonNode payments =
        json(mockMvc.perform(get("/api/loans/LN-0000-00000/payments?validate=true")));
    assertThat(payments.isArray()).isTrue();
    assertThat(payments.size()).isZero();

    JsonNode rows =
        json(mockMvc.perform(get("/api/data-quality/invalid-inputs?ruleId=INPUT_NOT_FOUND")));
    assertThat(rows.size()).isEqualTo(2);
    assertThat(rows.get(0).get("inputName").asText()).isEqualTo("loanId");
    assertThat(rows.get(1).get("inputName").asText()).isEqualTo("id");
  }

  @Test
  void listEndpointsQuarantineEveryReturnedRecordOnce() throws Exception {
    JsonNode borrowers = json(mockMvc.perform(get("/api/borrowers?validate=true")));
    JsonNode rows =
        json(
            mockMvc.perform(
                get(
                    "/api/data-quality/invalid-inputs?entityType=CDW_BORR_MSTR&recordKey=B-10001")));
    assertThat(borrowers.size()).isGreaterThanOrEqualTo(5);
    assertThat(rules(rows)).containsExactly("BORR_SSN_PLACEHOLDER");
    assertThat(rows.get(0).get("endpoint").asText()).isEqualTo("GET /api/borrowers");
    assertThat(rows.get(0).get("inputName").isNull()).isTrue();
  }

  @Test
  void reportInValidationModeQuarantinesAllTablesAndDeleteClears() throws Exception {
    json(mockMvc.perform(get("/api/data-quality/report?validate=true")));
    JsonNode rows = json(mockMvc.perform(get("/api/data-quality/invalid-inputs")));
    assertThat(rows.findValuesAsText("entityType"))
        .contains("CDW_BORR_MSTR", "CDW_LN_ACCT", "CDW_PMT_HIST");
    assertThat(rules(rows))
        .contains("PMT_SUM_MISMATCH", "PMT_SEQ_NBR_LOSS", "LN_ACTIVE_DELINQUENT");

    JsonNode deleted = json(mockMvc.perform(delete("/api/data-quality/invalid-inputs")));
    assertThat(deleted.get("deleted").asLong()).isEqualTo(rows.size());
    assertThat(repository.count()).isZero();
  }

  private static JsonNode json(org.springframework.test.web.servlet.ResultActions actions)
      throws Exception {
    MvcResult result = actions.andExpect(status().isOk()).andReturn();
    return MAPPER.readTree(result.getResponse().getContentAsString());
  }

  private static List<String> rules(JsonNode rows) {
    return rows.findValuesAsText("ruleId").stream().distinct().toList();
  }
}
