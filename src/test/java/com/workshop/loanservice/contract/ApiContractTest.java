package com.workshop.loanservice.contract;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Schema contract for the public loan and borrower API, asserted against the seeded H2 data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiContractTest {

    private static final String LOAN_ID = "LN-2019-00142";
    private static final String BORROWER_ID = "B-10001";
    private static final Set<String> LOAN_STATUSES =
            Set.of("Active", "Closed", "Default", "Forbearance", "Unknown");
    private static final Set<String> RAW_CODES = Set.of("ACT", "CLO", "DFT", "FRB", "INA",
            "SFR", "CND", "MFR", "TWN", "REG", "EXT", "PRT", "PRE", "PST", "REV", "NSF", "PND");
    private static final Pattern COMMA_NUMBER = Pattern.compile("\"\\d{1,3}(,\\d{3})+(\\.\\d+)?\"");
    private static final List<String> LOAN_AMOUNTS =
            List.of("originalAmount", "currentBalance", "interestRate", "monthlyPayment");
    private static final List<String> PAYMENT_AMOUNTS = List.of("totalAmount",
            "principalAmount", "interestAmount", "escrowAmount", "lateFee");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loanListMatchesLoanSummaryShape() throws Exception {
        List<Map<String, Object>> loans = JsonPath.read(getJson("/api/loans"), "$");

        assertThat(loans).hasSize(5);
        loans.forEach(ApiContractTest::assertLoanSummary);
    }

    @Test
    void loanDetailMatchesLoanSummaryShape() throws Exception {
        String body = getJson("/api/loans/" + LOAN_ID);

        Map<String, Object> loan = JsonPath.read(body, "$");
        assertLoanSummary(loan);
        assertThat(loan).containsEntry("loanAccountNumber", LOAN_ID);
    }

    @Test
    void paymentsMatchPaymentShape() throws Exception {
        mockMvc.perform(get("/api/loans/" + LOAN_ID + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].paymentId", everyItem(instanceOf(String.class))))
                .andExpect(jsonPath("$[*].paymentDate", everyItem(notNullValue())));

        List<Map<String, Object>> payments =
                JsonPath.read(getJson("/api/loans/" + LOAN_ID + "/payments"), "$");
        payments.forEach(payment -> {
            assertThat(payment).containsEntry("loanAccountNumber", LOAN_ID);
            assertThat(payment.get("paymentId")).isInstanceOf(String.class);
            assertThat(payment.get("paymentDate")).isInstanceOf(String.class);
            PAYMENT_AMOUNTS.forEach(field ->
                    assertThat(payment.get(field)).as(field).isInstanceOf(Number.class));
            assertThat(payment.get("type")).isIn("Regular", "Extra", "Partial", "Prepayment");
            assertThat(payment.get("status"))
                    .isIn("Posted", "Reversed", "Non-Sufficient Funds", "Pending");
        });
    }

    @Test
    void borrowerListMatchesBorrowerShape() throws Exception {
        List<Map<String, Object>> borrowers = JsonPath.read(getJson("/api/borrowers"), "$");

        assertThat(borrowers).hasSize(5);
        borrowers.forEach(ApiContractTest::assertBorrower);
    }

    @Test
    void borrowerDetailIncludesLoanSummaries() throws Exception {
        Map<String, Object> borrower =
                JsonPath.read(getJson("/api/borrowers/" + BORROWER_ID), "$");

        assertBorrower(borrower);
        assertThat(borrower).containsEntry("id", BORROWER_ID);
        List<Map<String, Object>> loans = JsonPath.read(borrower, "$.loans");
        assertThat(loans).isNotEmpty();
        loans.forEach(ApiContractTest::assertLoanSummary);
    }

    @Test
    void responsesDoNotLeakLegacyFormatting() throws Exception {
        for (String path : List.of("/api/loans", "/api/loans/" + LOAN_ID + "/payments",
                "/api/borrowers/" + BORROWER_ID)) {
            String body = getJson(path);
            assertThat(COMMA_NUMBER.matcher(body).find())
                    .as("comma-formatted number in %s", path).isFalse();
            RAW_CODES.forEach(code ->
                    assertThat(body).as("raw code %s in %s", code, path)
                            .doesNotContain("\"" + code + "\""));
        }
    }

    private String getJson(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
    }

    private static void assertLoanSummary(Map<String, Object> loan) {
        assertThat(loan.get("loanAccountNumber")).isInstanceOf(String.class);
        List.of("borrowerName", "productDescription", "originationDate", "propertyAddress",
                "propertyType").forEach(field ->
                assertThat(loan.get(field)).as(field).isInstanceOf(String.class));
        LOAN_AMOUNTS.forEach(field ->
                assertThat(loan.get(field)).as(field).isInstanceOf(Number.class));
        assertThat(loan.get("status")).isIn(LOAN_STATUSES.toArray());
        assertThat(RAW_CODES).doesNotContain((String) loan.get("propertyType"));
    }

    private static void assertBorrower(Map<String, Object> borrower) {
        List.of("id", "fullName", "email", "phone", "city", "employmentStatus").forEach(field ->
                assertThat(borrower.get(field)).as(field).isInstanceOf(String.class));
        assertThat((String) borrower.get("state")).hasSize(2);
        Object creditScore = borrower.get("creditScore");
        assertThat(creditScore == null || creditScore instanceof Integer)
                .as("creditScore is integer or null: %s", creditScore).isTrue();
    }
}
