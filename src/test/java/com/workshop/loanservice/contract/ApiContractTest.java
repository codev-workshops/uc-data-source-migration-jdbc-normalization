package com.workshop.loanservice.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.service.DataSourceSelector;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ONE datasource-parameterized contract suite (Task 4 safety net + acceptance gate).
 *
 * <p>There is a single set of assertions and a single set of shared golden
 * fixtures. Every test is a {@link ParameterizedTest} driven by the
 * {@code {"legacy", "modern"}} data-source parameter; for each value the runtime
 * {@link DataSourceSelector} (the dual-read feature flag) is flipped and the
 * identical assertions run against that data source. "The same suite runs
 * against both data sources" is therefore literal, not by convention.
 *
 * <p>Each contract is checked at BOTH levels against the same golden file: the
 * MockMvc HTTP endpoint response and the {@code LoanService} output
 * (re-serialized with the application's ObjectMapper). Comparison is STRICT
 * (see {@link ContractDifferences}).
 *
 * <p>The {@code modern} parameter is now ENABLED: with the migration in place it
 * must reproduce the pre-migration legacy contract byte-for-byte.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoanService loanService;

    @Autowired
    private DataSourceSelector selector;

    // ---- Contract cases (identical assertions, one per endpoint/aspect) --------

    @ParameterizedTest(name = "GET /api/loans [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void allLoans(String dataSource) throws Exception {
        select(dataSource);
        assertEndpointMatchesGolden("/api/loans", "loans.json");
        assertServiceMatchesGolden(loanService.getAllLoans(), "loans.json");
    }

    @ParameterizedTest(name = "GET /api/loans/LN-2019-00142 [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void singleLoan(String dataSource) throws Exception {
        select(dataSource);
        assertEndpointMatchesGolden("/api/loans/LN-2019-00142", "loan-LN-2019-00142.json");
        assertServiceMatchesGolden(loanService.getLoanById("LN-2019-00142"), "loan-LN-2019-00142.json");
    }

    @ParameterizedTest(name = "GET /api/loans/LN-2019-00142/payments [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void paymentsForLoanPreserveOrder(String dataSource) throws Exception {
        select(dataSource);
        // STRICT array comparison pins the observed PMT_DT-DESC order
        // (PMT-2025120001 then PMT-2025110001).
        assertEndpointMatchesGolden("/api/loans/LN-2019-00142/payments", "payments-LN-2019-00142.json");
        assertServiceMatchesGolden(loanService.getPaymentsByLoan("LN-2019-00142"),
                "payments-LN-2019-00142.json");
    }

    @ParameterizedTest(name = "GET /api/borrowers [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void allBorrowers(String dataSource) throws Exception {
        select(dataSource);
        assertEndpointMatchesGolden("/api/borrowers", "borrowers.json");
        assertServiceMatchesGolden(loanService.getAllBorrowers(), "borrowers.json");
    }

    @ParameterizedTest(name = "GET /api/borrowers/B-10001 [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void borrowerByIdWithNestedLoans(String dataSource) throws Exception {
        select(dataSource);
        assertEndpointMatchesGolden("/api/borrowers/B-10001", "borrower-B-10001.json");
        assertServiceMatchesGolden(loanService.getBorrowerById("B-10001"), "borrower-B-10001.json");
    }

    @ParameterizedTest(name = "GET /api/borrowers/B-10005 (null middle initial) [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void borrowerByIdWithNullMiddleInitial(String dataSource) throws Exception {
        select(dataSource);
        assertEndpointMatchesGolden("/api/borrowers/B-10005", "borrower-B-10005.json");
        assertServiceMatchesGolden(loanService.getBorrowerById("B-10005"), "borrower-B-10005.json");
    }

    // ---- Shared, datasource-agnostic helpers -----------------------------------

    private void select(String dataSource) {
        selector.setActive(dataSource);
    }

    private void assertEndpointMatchesGolden(String url, String goldenFile) throws Exception {
        String actual = mockMvc
                .perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ContractDifferences.assertMatchesGolden(GoldenFiles.read(goldenFile), actual);
    }

    private void assertServiceMatchesGolden(Object serviceResult, String goldenFile) throws Exception {
        String actual = objectMapper.writeValueAsString(serviceResult);
        ContractDifferences.assertMatchesGolden(GoldenFiles.read(goldenFile), actual);
    }
}
