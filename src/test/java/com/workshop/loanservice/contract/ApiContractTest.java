package com.workshop.loanservice.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Response-shape contract for every endpoint in {@code LoanController}, {@code BorrowerController}
 * and {@code DataQualityController}, backed by the seeded legacy H2 data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiContractTest {

  private static final Set<String> LOAN_FIELDS =
      Set.of(
          "loanAccountNumber",
          "borrowerName",
          "productDescription",
          "originalAmount",
          "currentBalance",
          "interestRate",
          "monthlyPayment",
          "status",
          "originationDate",
          "propertyAddress",
          "propertyType",
          "dataQualityViolations");

  private static final Set<String> BORROWER_FIELDS =
      Set.of(
          "id",
          "fullName",
          "email",
          "phone",
          "city",
          "state",
          "creditScore",
          "employmentStatus",
          "loans",
          "dataQualityViolations");

  private static final Set<String> PAYMENT_FIELDS =
      Set.of(
          "paymentId",
          "loanAccountNumber",
          "paymentDate",
          "totalAmount",
          "principalAmount",
          "interestAmount",
          "escrowAmount",
          "lateFee",
          "type",
          "status",
          "dataQualityViolations");

  private static final Set<String> LOAN_STATUSES =
      Set.of("Active", "Closed", "Default", "Forbearance", "Unknown");
  private static final Set<String> PROPERTY_TYPES =
      Set.of(
          "Single Family Residence",
          "Condominium",
          "Multi-Family Residence",
          "Townhouse",
          "Unknown");
  private static final Set<String> PAYMENT_TYPES =
      Set.of("Regular", "Extra", "Partial", "Prepayment", "Unknown");
  private static final Set<String> PAYMENT_STATUSES =
      Set.of("Posted", "Reversed", "Non-Sufficient Funds", "Pending", "Unknown");

  @Autowired private MockMvc mockMvc;

  @Test
  void listLoansMatchesLoanSummarySchema() throws Exception {
    JsonNode body = SchemaSupport.assertConforms(getJson("/api/loans"), "loan-summary");
    assertThat(body.isArray()).isTrue();
    assertThat(body.size()).isGreaterThan(0);
    body.forEach(this::assertLoanShape);
  }

  @Test
  void loanByIdMatchesLoanSummarySchema() throws Exception {
    JsonNode body =
        SchemaSupport.assertConforms(getJson("/api/loans/LN-2019-00142"), "loan-summary");
    assertLoanShape(body);
    assertThat(body.get("loanAccountNumber").asText()).isEqualTo("LN-2019-00142");
    assertThat(body.get("originalAmount").isNumber()).isTrue();
    assertThat(body.get("originalAmount").decimalValue()).isEqualByComparingTo("285000");
    assertThat(body.get("status").asText()).isEqualTo("Active");
    assertThat(body.get("propertyType").asText()).isEqualTo("Single Family Residence");
  }

  @Test
  void listBorrowersMatchesBorrowerSchema() throws Exception {
    JsonNode body = SchemaSupport.assertConforms(getJson("/api/borrowers"), "borrower");
    assertThat(body.size()).isGreaterThan(0);
    body.forEach(this::assertBorrowerShape);
  }

  @Test
  void borrowerByIdMatchesBorrowerSchema() throws Exception {
    JsonNode body = SchemaSupport.assertConforms(getJson("/api/borrowers/B-10001"), "borrower");
    assertBorrowerShape(body);
    assertThat(body.get("id").asText()).isEqualTo("B-10001");
    assertThat(body.get("fullName").asText()).isEqualTo("James R. Mitchell");
    assertThat(body.get("creditScore").isInt()).isTrue();
    assertThat(body.get("creditScore").intValue()).isEqualTo(745);
    assertThat(body.get("loans").isArray()).isTrue();
    body.get("loans").forEach(this::assertLoanShape);
  }

  @Test
  void paymentsByLoanMatchPaymentSchema() throws Exception {
    JsonNode body =
        SchemaSupport.assertConforms(getJson("/api/loans/LN-2019-00142/payments"), "payment");
    assertThat(body.size()).isGreaterThan(0);
    body.forEach(this::assertPaymentShape);
  }

  @Test
  void knownSeedPaymentSumMismatchSurfacesAsViolationNotZero() throws Exception {
    JsonNode payments = SchemaSupport.parse(getJson("/api/loans/LN-2019-00142/payments"));
    JsonNode mismatch = null;
    for (JsonNode p : payments) {
      if ("PMT-2025120001".equals(p.get("paymentId").asText())) {
        mismatch = p;
      }
    }
    assertThat(mismatch).isNotNull();
    assertThat(mismatch.get("totalAmount").decimalValue()).isEqualByComparingTo("1487.02");
    assertThat(mismatch.get("interestAmount").decimalValue()).isEqualByComparingTo("1074.69");
  }

  @Test
  void dataQualityReportMatchesSchema() throws Exception {
    JsonNode body =
        SchemaSupport.assertConforms(getJson("/api/data-quality/report"), "data-quality-report");
    assertThat(fieldNames(body))
        .containsExactlyInAnyOrder(
            "generatedAt", "summary", "tables", "rules", "records", "violations");
    assertThat(fieldNames(body.get("tables")))
        .containsExactlyInAnyOrder("CDW_BORR_MSTR", "CDW_LN_PROD", "CDW_LN_ACCT", "CDW_PMT_HIST");
    JsonNode rules = body.get("rules");
    assertThat(rules.has("PMT_SUM_MISMATCH")).isTrue();
    assertThat(rules.get("PMT_SUM_MISMATCH").get("affectedRecords").toString())
        .contains("CDW_PMT_HIST:PMT-2025120001");
    assertThat(rules.has("LN_ACTIVE_DELINQUENT")).isTrue();
    assertThat(rules.get("LN_ACTIVE_DELINQUENT").get("affectedRecords").toString())
        .contains("CDW_LN_ACCT:LN-2018-00089");
    assertThat(rules.has("BORR_SSN_PLACEHOLDER")).isTrue();
    assertThat(rules.has("PMT_SEQ_NBR_LOSS")).isTrue();
    assertThat(rules.get("PMT_SEQ_NBR_LOSS").get("severity").asText()).isEqualTo("WARN");
  }

  @Test
  void dataQualityMarkdownRenders() throws Exception {
    String md =
        mockMvc
            .perform(get("/api/data-quality/report/markdown"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_MARKDOWN))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(md).startsWith("# Data Quality Report");
    assertThat(md).contains("PMT_SUM_MISMATCH").contains("LN-2018-00089");
  }

  private String getJson(String path) throws Exception {
    return mockMvc
        .perform(get(path))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void assertLoanShape(JsonNode loan) {
    assertThat(fieldNames(loan)).isEqualTo(new TreeSet<>(LOAN_FIELDS));
    assertThat(loan.get("loanAccountNumber").isTextual()).isTrue();
    assertNumberOrNullWithViolation(loan, "originalAmount");
    assertNumberOrNullWithViolation(loan, "currentBalance");
    assertNumberOrNullWithViolation(loan, "interestRate");
    assertNumberOrNullWithViolation(loan, "monthlyPayment");
    assertThat(LOAN_STATUSES).contains(loan.get("status").asText());
    assertThat(PROPERTY_TYPES).contains(loan.get("propertyType").asText());
    assertThat(loan.get("dataQualityViolations").isArray()).isTrue();
  }

  private void assertBorrowerShape(JsonNode b) {
    assertThat(fieldNames(b)).isEqualTo(new TreeSet<>(BORROWER_FIELDS));
    assertThat(b.get("id").isTextual()).isTrue();
    assertThat(b.get("fullName").isTextual()).isTrue();
    JsonNode score = b.get("creditScore");
    assertThat(score.isInt() || score.isNull()).isTrue();
    if (score.isInt()) {
      assertThat(score.intValue()).isBetween(300, 850);
    }
    assertThat(b.get("loans").isArray()).isTrue();
    assertThat(b.get("dataQualityViolations").isArray()).isTrue();
  }

  private void assertPaymentShape(JsonNode p) {
    assertThat(fieldNames(p)).isEqualTo(new TreeSet<>(PAYMENT_FIELDS));
    for (String f :
        Set.of("totalAmount", "principalAmount", "interestAmount", "escrowAmount", "lateFee")) {
      assertNumberOrNullWithViolation(p, f);
    }
    assertThat(PAYMENT_TYPES).contains(p.get("type").asText());
    assertThat(PAYMENT_STATUSES).contains(p.get("status").asText());
  }

  /** A numeric DTO field is a JSON number (never a string) or null with an explaining violation. */
  private void assertNumberOrNullWithViolation(JsonNode record, String field) {
    JsonNode value = record.get(field);
    assertThat(value.isTextual()).as("%s must not be a string", field).isFalse();
    if (value.isNull()) {
      assertThat(record.get("dataQualityViolations").toString())
          .as("null %s must be explained by a violation", field)
          .contains("\"ruleId\"");
    } else {
      assertThat(value.isNumber()).isTrue();
    }
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new TreeSet<>();
    Iterator<String> it = node.fieldNames();
    while (it.hasNext()) {
      names.add(it.next());
    }
    return names;
  }
}
