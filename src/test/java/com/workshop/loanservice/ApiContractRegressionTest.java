package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Golden-file regression tests that pin the EXACT current REST API contract
 * against the seeded legacy data, captured BEFORE the data-source migration.
 *
 * These tests assert the raw, compact JSON response body byte-for-byte. This is
 * intentional: it locks down details that a parsed/lenient JSON comparison would
 * silently lose, in particular:
 *   - BigDecimal scale / trailing zeros (e.g. "142567.90", "4.750", "0.00")
 *   - integer-vs-decimal rendering (originalAmount renders as 285000, not 285000.00)
 *   - legacy date passthrough format "MM/DD/YYYY"
 *   - field order, null fields, and list ordering
 *
 * After the migration to the modern schema, every assertion here must still pass
 * unchanged (or any intentional difference must be explicitly justified).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiContractRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String LOANS_GOLDEN =
        "[{\"loanAccountNumber\":\"LN-2019-00142\",\"borrowerName\":\"James Mitchell\",\"productDescription\":\"30-Year Fixed Rate Mortgage\",\"originalAmount\":285000,\"currentBalance\":271432.56,\"interestRate\":4.750,\"monthlyPayment\":1487.02,\"status\":\"Active\",\"originationDate\":\"02/15/2019\",\"propertyAddress\":\"742 Elm Street, Springfield, IL 62701\",\"propertyType\":\"Single Family Residence\"},"
        + "{\"loanAccountNumber\":\"LN-2020-00398\",\"borrowerName\":\"Sarah Chen\",\"productDescription\":\"15-Year Fixed Rate Mortgage\",\"originalAmount\":420000,\"currentBalance\":312876.43,\"interestRate\":3.125,\"monthlyPayment\":2924.18,\"status\":\"Active\",\"originationDate\":\"04/01/2020\",\"propertyAddress\":\"1100 Oak Avenue, Portland, OR 97201\",\"propertyType\":\"Condominium\"},"
        + "{\"loanAccountNumber\":\"LN-2018-00089\",\"borrowerName\":\"Michael Torres\",\"productDescription\":\"5/1 Adjustable Rate Mortgage\",\"originalAmount\":195000,\"currentBalance\":178234.12,\"interestRate\":5.250,\"monthlyPayment\":1077.05,\"status\":\"Active\",\"originationDate\":\"07/01/2018\",\"propertyAddress\":\"305 Pine Road, Austin, TX 78701\",\"propertyType\":\"Single Family Residence\"},"
        + "{\"loanAccountNumber\":\"LN-2021-00567\",\"borrowerName\":\"Emily Johnson\",\"productDescription\":\"30-Year Fixed Rate Mortgage\",\"originalAmount\":525000,\"currentBalance\":498123.78,\"interestRate\":3.875,\"monthlyPayment\":2468.35,\"status\":\"Active\",\"originationDate\":\"10/01/2021\",\"propertyAddress\":\"89 Maple Drive, Denver, CO 80202\",\"propertyType\":\"Townhouse\"},"
        + "{\"loanAccountNumber\":\"LN-2017-00034\",\"borrowerName\":\"Robert Williams\",\"productDescription\":\"FHA 30-Year Fixed\",\"originalAmount\":165000,\"currentBalance\":142567.90,\"interestRate\":4.250,\"monthlyPayment\":811.61,\"status\":\"Active\",\"originationDate\":\"03/01/2017\",\"propertyAddress\":\"2200 Cedar Lane, Phoenix, AZ 85001\",\"propertyType\":\"Single Family Residence\"}]";

    private static final String LOAN_ONE_GOLDEN =
        "{\"loanAccountNumber\":\"LN-2017-00034\",\"borrowerName\":\"Robert Williams\",\"productDescription\":\"FHA 30-Year Fixed\",\"originalAmount\":165000,\"currentBalance\":142567.90,\"interestRate\":4.250,\"monthlyPayment\":811.61,\"status\":\"Active\",\"originationDate\":\"03/01/2017\",\"propertyAddress\":\"2200 Cedar Lane, Phoenix, AZ 85001\",\"propertyType\":\"Single Family Residence\"}";

    private static final String BORROWERS_GOLDEN =
        "[{\"id\":\"B-10001\",\"fullName\":\"James R. Mitchell\",\"email\":\"j.mitchell@email.com\",\"phone\":\"217-555-0142\",\"city\":\"Springfield\",\"state\":\"IL\",\"creditScore\":745,\"employmentStatus\":\"EMPLOYED\",\"loans\":null},"
        + "{\"id\":\"B-10002\",\"fullName\":\"Sarah L. Chen\",\"email\":\"s.chen@email.com\",\"phone\":\"503-555-0198\",\"city\":\"Portland\",\"state\":\"OR\",\"creditScore\":780,\"employmentStatus\":\"EMPLOYED\",\"loans\":null},"
        + "{\"id\":\"B-10003\",\"fullName\":\"Michael A. Torres\",\"email\":\"m.torres@email.com\",\"phone\":\"512-555-0167\",\"city\":\"Austin\",\"state\":\"TX\",\"creditScore\":692,\"employmentStatus\":\"SELF-EMP\",\"loans\":null},"
        + "{\"id\":\"B-10004\",\"fullName\":\"Emily M. Johnson\",\"email\":\"e.johnson@email.com\",\"phone\":\"303-555-0134\",\"city\":\"Denver\",\"state\":\"CO\",\"creditScore\":810,\"employmentStatus\":\"EMPLOYED\",\"loans\":null},"
        + "{\"id\":\"B-10005\",\"fullName\":\"Robert Williams\",\"email\":\"r.williams@email.com\",\"phone\":\"602-555-0156\",\"city\":\"Phoenix\",\"state\":\"AZ\",\"creditScore\":658,\"employmentStatus\":\"RETIRED\",\"loans\":null}]";

    private static final String BORROWER_WITH_LOANS_GOLDEN =
        "{\"id\":\"B-10005\",\"fullName\":\"Robert Williams\",\"email\":\"r.williams@email.com\",\"phone\":\"602-555-0156\",\"city\":\"Phoenix\",\"state\":\"AZ\",\"creditScore\":658,\"employmentStatus\":\"RETIRED\",\"loans\":[{\"loanAccountNumber\":\"LN-2017-00034\",\"borrowerName\":\"Robert Williams\",\"productDescription\":\"FHA 30-Year Fixed\",\"originalAmount\":165000,\"currentBalance\":142567.90,\"interestRate\":4.250,\"monthlyPayment\":811.61,\"status\":\"Active\",\"originationDate\":\"03/01/2017\",\"propertyAddress\":\"2200 Cedar Lane, Phoenix, AZ 85001\",\"propertyType\":\"Single Family Residence\"}]}";

    private static final String PAYMENTS_GOLDEN =
        "[{\"paymentId\":\"PMT-2025120003\",\"loanAccountNumber\":\"LN-2018-00089\",\"paymentDate\":\"12/01/2025\",\"totalAmount\":1077.05,\"principalAmount\":297.12,\"interestAmount\":779.93,\"escrowAmount\":0.00,\"lateFee\":0.00,\"type\":\"Regular\",\"status\":\"Posted\"},"
        + "{\"paymentId\":\"PMT-2025110003\",\"loanAccountNumber\":\"LN-2018-00089\",\"paymentDate\":\"11/01/2025\",\"totalAmount\":1077.05,\"principalAmount\":295.82,\"interestAmount\":781.23,\"escrowAmount\":0.00,\"lateFee\":47.50,\"type\":\"Regular\",\"status\":\"Posted\"}]";

    private String body(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void getAllLoans_matchesGolden() throws Exception {
        assertThat(body("/api/loans")).isEqualTo(LOANS_GOLDEN);
    }

    @Test
    void getLoanById_matchesGolden() throws Exception {
        assertThat(body("/api/loans/LN-2017-00034")).isEqualTo(LOAN_ONE_GOLDEN);
    }

    @Test
    void getLoanById_unknown_throwsUnhandledRuntimeException_rendersAs500() throws Exception {
        // The controller/service throws a raw RuntimeException with no @ExceptionHandler,
        // which the servlet container renders as HTTP 500 (verified against the running app).
        // MockMvc has no error dispatch, so it rethrows the exception from perform().
        assertThatThrownBy(() -> mockMvc.perform(get("/api/loans/NOPE")))
                .hasRootCauseInstanceOf(RuntimeException.class)
                .rootCause().hasMessage("Loan not found: NOPE");
    }

    @Test
    void getAllBorrowers_matchesGolden_loansFieldIsNull() throws Exception {
        assertThat(body("/api/borrowers")).isEqualTo(BORROWERS_GOLDEN);
    }

    @Test
    void getBorrowerById_matchesGolden_includesLoansAndNullMiddleInitial() throws Exception {
        assertThat(body("/api/borrowers/B-10005")).isEqualTo(BORROWER_WITH_LOANS_GOLDEN);
    }

    @Test
    void getBorrowerById_unknown_throwsUnhandledRuntimeException_rendersAs500() throws Exception {
        assertThatThrownBy(() -> mockMvc.perform(get("/api/borrowers/NOPE")))
                .hasRootCauseInstanceOf(RuntimeException.class)
                .rootCause().hasMessage("Borrower not found: NOPE");
    }

    @Test
    void getPaymentsByLoan_matchesGolden_orderedByDateDescending() throws Exception {
        assertThat(body("/api/loans/LN-2018-00089/payments")).isEqualTo(PAYMENTS_GOLDEN);
    }

    @Test
    void getPaymentsByLoan_unknownLoan_returnsEmptyListNot404() throws Exception {
        assertThat(body("/api/loans/NOPE/payments")).isEqualTo("[]");
    }

    @Test
    void documentedPaymentsRoute_doesNotExist() throws Exception {
        // README/MIGRATION_TASKS document "GET /api/payments/loan/{loanId}";
        // the real route is "GET /api/loans/{loanId}/payments". Pin the mismatch.
        mockMvc.perform(get("/api/payments/loan/LN-2018-00089")).andExpect(status().isNotFound());
    }

    @Test
    void contractRules_areExplicit() throws Exception {
        // Loan summary borrowerName comes from the DENORMALIZED loan columns (no middle
        // initial), while the borrower master fullName DOES include it.
        mockMvc.perform(get("/api/loans/LN-2019-00142"))
                .andExpect(jsonPath("$.borrowerName").value("James Mitchell"))
                .andExpect(jsonPath("$.status").value("Active"))                 // ACT -> Active
                .andExpect(jsonPath("$.propertyType").value("Single Family Residence")) // SFR -> ...
                .andExpect(jsonPath("$.originationDate").value("02/15/2019"));   // MM/DD/YYYY passthrough
        mockMvc.perform(get("/api/borrowers/B-10001"))
                .andExpect(jsonPath("$.fullName").value("James R. Mitchell"))    // includes middle initial
                .andExpect(jsonPath("$.employmentStatus").value("EMPLOYED"))     // raw, NOT expanded
                .andExpect(jsonPath("$.creditScore").value(745));               // parsed to Integer
    }
}
