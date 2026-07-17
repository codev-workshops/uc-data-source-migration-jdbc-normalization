package com.workshop.loanservice.contract;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ONE datasource-parameterized contract suite (Task 4 Step 1 safety net).
 *
 * <p>There is a single set of assertions and a single set of shared golden
 * fixtures. Every test is a {@link ParameterizedTest} driven by the
 * {@code {"legacy", "modern"}} data-source parameter, and for each value the
 * identical assertions run against that data source's own per-profile Spring
 * context (see {@link DataSourceContexts}). "The same suite runs against both
 * data sources" is therefore literal, not by convention.
 *
 * <p>Each contract is checked at BOTH levels against the same golden file:
 * the MockMvc HTTP endpoint response and the {@code LoanService} output
 * (re-serialized with the application's ObjectMapper). Comparison is STRICT
 * (see {@link ContractDifferences}).
 *
 * <p>The {@code modern} parameter is DISABLED until the data-source migration
 * (Tasks 1-3) is done, so the build stays green now while this suite doubles as
 * the migration acceptance gate (Task 4 success criteria). To enable it, remove
 * {@code "modern"} from {@link #DISABLED_DATASOURCES}.
 */
class ApiContractTest {

    /**
     * Data-source parameters that are switched off for now. Equivalent to marking
     * the {@code modern} parameter {@code @Disabled}: its cases are skipped with the
     * reason below rather than failing the build.
     */
    private static final Set<String> DISABLED_DATASOURCES = Set.of("modern");
    private static final String DISABLED_REASON = "enable after data-source migration Tasks 1-3";

    // ---- Contract cases (identical assertions, one per endpoint/aspect) --------

    @ParameterizedTest(name = "GET /api/loans [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void allLoans(String dataSource) throws Exception {
        DataSourceContext ds = context(dataSource);
        assertEndpointMatchesGolden(ds, "/api/loans", "loans.json");
        assertServiceMatchesGolden(ds, ds.loanService().getAllLoans(), "loans.json");
    }

    @ParameterizedTest(name = "GET /api/loans/LN-2019-00142 [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void singleLoan(String dataSource) throws Exception {
        DataSourceContext ds = context(dataSource);
        assertEndpointMatchesGolden(ds, "/api/loans/LN-2019-00142", "loan-LN-2019-00142.json");
        assertServiceMatchesGolden(ds, ds.loanService().getLoanById("LN-2019-00142"), "loan-LN-2019-00142.json");
    }

    @ParameterizedTest(name = "GET /api/loans/LN-2019-00142/payments [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void paymentsForLoanPreserveOrder(String dataSource) throws Exception {
        DataSourceContext ds = context(dataSource);
        // STRICT array comparison pins the observed PMT_DT-DESC order
        // (PMT-2025120001 then PMT-2025110001).
        assertEndpointMatchesGolden(ds, "/api/loans/LN-2019-00142/payments", "payments-LN-2019-00142.json");
        assertServiceMatchesGolden(ds, ds.loanService().getPaymentsByLoan("LN-2019-00142"),
                "payments-LN-2019-00142.json");
    }

    @ParameterizedTest(name = "GET /api/borrowers [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void allBorrowers(String dataSource) throws Exception {
        DataSourceContext ds = context(dataSource);
        assertEndpointMatchesGolden(ds, "/api/borrowers", "borrowers.json");
        assertServiceMatchesGolden(ds, ds.loanService().getAllBorrowers(), "borrowers.json");
    }

    @ParameterizedTest(name = "GET /api/borrowers/B-10001 [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void borrowerByIdWithNestedLoans(String dataSource) throws Exception {
        DataSourceContext ds = context(dataSource);
        assertEndpointMatchesGolden(ds, "/api/borrowers/B-10001", "borrower-B-10001.json");
        assertServiceMatchesGolden(ds, ds.loanService().getBorrowerById("B-10001"), "borrower-B-10001.json");
    }

    @ParameterizedTest(name = "GET /api/borrowers/B-10005 (null middle initial) [{0}]")
    @ValueSource(strings = {"legacy", "modern"})
    void borrowerByIdWithNullMiddleInitial(String dataSource) throws Exception {
        DataSourceContext ds = context(dataSource);
        assertEndpointMatchesGolden(ds, "/api/borrowers/B-10005", "borrower-B-10005.json");
        assertServiceMatchesGolden(ds, ds.loanService().getBorrowerById("B-10005"), "borrower-B-10005.json");
    }

    // ---- Shared, datasource-agnostic helpers -----------------------------------

    private DataSourceContext context(String dataSource) {
        Assumptions.assumeFalse(DISABLED_DATASOURCES.contains(dataSource), DISABLED_REASON);
        return DataSourceContexts.forDataSource(dataSource);
    }

    private void assertEndpointMatchesGolden(DataSourceContext ds, String url, String goldenFile) throws Exception {
        String actual = ds.mockMvc()
                .perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ContractDifferences.assertMatchesGolden(GoldenFiles.read(goldenFile), actual);
    }

    private void assertServiceMatchesGolden(DataSourceContext ds, Object serviceResult, String goldenFile)
            throws Exception {
        String actual = ds.objectMapper().writeValueAsString(serviceResult);
        ContractDifferences.assertMatchesGolden(GoldenFiles.read(goldenFile), actual);
    }
}
