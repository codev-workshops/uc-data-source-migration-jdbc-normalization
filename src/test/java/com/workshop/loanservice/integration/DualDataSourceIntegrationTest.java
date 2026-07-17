package com.workshop.loanservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.migration.DataMigrationService;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.service.DataSourceSelector;
import com.workshop.loanservice.service.LegacyLoanDataProvider;
import com.workshop.loanservice.service.LoanService;
import com.workshop.loanservice.service.ModernLoanDataProvider;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests over the real dual-data-source wiring — no mocks. The Spring
 * context runs the actual startup ETL into the modern H2 database, so these
 * tests verify the migration, both providers, the {@link LoanService} routing,
 * and the admin feature-flag endpoint against real data. Comparing legacy vs
 * modern output directly (rather than against stubs) is what gives confidence
 * the migration reproduces the contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DualDataSourceIntegrationTest {

    @Autowired private DataMigrationService migrationService;
    @Autowired private LegacyLoanDataProvider legacyProvider;
    @Autowired private ModernLoanDataProvider modernProvider;
    @Autowired private LoanService loanService;
    @Autowired private DataSourceSelector selector;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mockMvc;

    @Autowired private BorrowerRepository modernBorrowers;
    @Autowired private LoanProductRepository modernProducts;
    @Autowired private LoanAccountRepository modernAccounts;
    @Autowired private PaymentRepository modernPayments;
    @Autowired private LegacyBorrowerRepository legacyBorrowers;
    @Autowired private LegacyLoanProductRepository legacyProducts;
    @Autowired private LegacyLoanAccountRepository legacyAccounts;
    @Autowired private LegacyPaymentRepository legacyPayments;

    // ---- Task 2: migration produced the expected, FK-correct modern dataset ----

    @Test
    void migrationProducedExpectedRowCounts() {
        assertEquals(5, modernBorrowers.count());
        assertEquals(5, modernProducts.count());
        assertEquals(5, modernAccounts.count());
        assertEquals(10, modernPayments.count());
    }

    @Test
    void modernRowCountsMatchLegacy() {
        assertEquals(legacyBorrowers.count(), modernBorrowers.count());
        assertEquals(legacyProducts.count(), modernProducts.count());
        assertEquals(legacyAccounts.count(), modernAccounts.count());
        assertEquals(legacyPayments.count(), modernPayments.count());
    }

    @Test
    void migrationIsIdempotentWhenRerun() {
        long borrowers = modernBorrowers.count();
        long payments = modernPayments.count();
        // Re-running must detect the populated tables and skip (no duplicate rows).
        migrationService.migrate();
        assertEquals(borrowers, modernBorrowers.count());
        assertEquals(payments, modernPayments.count());
    }

    @Test
    void migrationResolvedForeignKeysAndTypedValues() {
        LoanAccount account = modernAccounts.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertNotNull(account.getBorrower());
        assertNotNull(account.getProduct());
        assertEquals("B-10001", account.getBorrower().getExternalId());
        // Legacy VARCHAR fields became proper types during the ETL.
        assertEquals(0, new BigDecimal("285000").compareTo(account.getOriginalAmount()));
        assertEquals(LocalDate.of(2019, 2, 15), account.getOriginationDate());
        assertEquals("Single Family Residence", account.getPropertyType());
        assertEquals("ACTIVE", account.getStatus());
    }

    @Test
    void migrationPreservedLegacyPaymentSequenceAsBusinessKey() {
        List<PaymentDto> payments = modernProvider.getPaymentsByLoan("LN-2019-00142");
        // paymentId is the preserved legacy PMT_SEQ_NBR, ordered PMT_DT DESC.
        assertEquals(List.of("PMT-2025120001", "PMT-2025110001"),
                payments.stream().map(PaymentDto::getPaymentId).toList());
    }

    // ---- Task 3: the two providers produce byte-for-byte identical DTOs --------

    @Test
    void providersProduceIdenticalLoanOutput() throws Exception {
        assertJsonEquals(legacyProvider.getAllLoans(), modernProvider.getAllLoans());
        assertJsonEquals(legacyProvider.getLoanById("LN-2019-00142"),
                modernProvider.getLoanById("LN-2019-00142"));
    }

    @Test
    void providersProduceIdenticalBorrowerOutput() throws Exception {
        assertJsonEquals(legacyProvider.getAllBorrowers(), modernProvider.getAllBorrowers());
        // B-10001 has a middle initial; B-10005 does not — cover both name paths.
        assertJsonEquals(legacyProvider.getBorrowerById("B-10001"),
                modernProvider.getBorrowerById("B-10001"));
        assertJsonEquals(legacyProvider.getBorrowerById("B-10005"),
                modernProvider.getBorrowerById("B-10005"));
    }

    @Test
    void providersProduceIdenticalPaymentOutput() throws Exception {
        assertJsonEquals(legacyProvider.getPaymentsByLoan("LN-2019-00142"),
                modernProvider.getPaymentsByLoan("LN-2019-00142"));
    }

    @Test
    void providersReportTheirDataSource() {
        assertEquals(DataSourceSelector.DataSource.LEGACY, legacyProvider.dataSource());
        assertEquals(DataSourceSelector.DataSource.MODERN, modernProvider.dataSource());
    }

    @Test
    void unknownIdsThrowFromBothProviders() {
        assertThrows(RuntimeException.class, () -> legacyProvider.getLoanById("NOPE"));
        assertThrows(RuntimeException.class, () -> legacyProvider.getBorrowerById("NOPE"));
        assertThrows(RuntimeException.class, () -> modernProvider.getLoanById("NOPE"));
        assertThrows(RuntimeException.class, () -> modernProvider.getBorrowerById("NOPE"));
    }

    // ---- LoanService routes to whichever provider the flag selects -------------

    @Test
    void loanServiceRoutesToSelectedDataSource() throws Exception {
        DataSourceSelector.DataSource original = selector.getActive();
        try {
            selector.setActive(DataSourceSelector.DataSource.LEGACY);
            assertJsonEquals(legacyProvider.getAllLoans(), loanService.getAllLoans());
            assertJsonEquals(legacyProvider.getBorrowerById("B-10001"),
                    loanService.getBorrowerById("B-10001"));

            selector.setActive(DataSourceSelector.DataSource.MODERN);
            assertJsonEquals(modernProvider.getAllLoans(), loanService.getAllLoans());
            assertJsonEquals(modernProvider.getPaymentsByLoan("LN-2019-00142"),
                    loanService.getPaymentsByLoan("LN-2019-00142"));
        } finally {
            selector.setActive(original);
        }
    }

    // ---- Dual-read feature flag exposed over HTTP ------------------------------

    @Test
    void adminEndpointReportsAndSwitchesDataSource() throws Exception {
        DataSourceSelector.DataSource original = selector.getActive();
        try {
            mockMvc.perform(put("/api/admin/datasource/modern"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value("modern"));
            mockMvc.perform(get("/api/admin/datasource"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value("modern"));

            mockMvc.perform(put("/api/admin/datasource/legacy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value("legacy"));

            mockMvc.perform(put("/api/admin/datasource/bogus"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Unknown data source: bogus"));
        } finally {
            selector.setActive(original);
        }
    }

    // ---------------------------------------------------------------------------

    /**
     * STRICT but numeric-aware JSON equality (same policy as the golden contract
     * suite): legacy serializes whole-dollar amounts as {@code 285000} while the
     * modern DECIMAL column yields {@code 285000.00} — the same value, equal under
     * the contract, so a raw string compare would be stricter than the contract.
     */
    private void assertJsonEquals(Object expected, Object actual) throws Exception {
        JSONAssert.assertEquals(
                objectMapper.writeValueAsString(expected),
                objectMapper.writeValueAsString(actual),
                JSONCompareMode.STRICT);
    }
}
