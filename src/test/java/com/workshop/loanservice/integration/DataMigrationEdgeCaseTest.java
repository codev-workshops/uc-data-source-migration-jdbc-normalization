package com.workshop.loanservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.service.LegacyLoanDataProvider;
import com.workshop.loanservice.service.ModernLoanDataProvider;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the real ETL over an isolated edge-case data set (see
 * {@code data-legacy-edge.sql}) whose rows carry the status/type/property codes
 * the production 5/5/5/10 seed never uses — CLO/DFT/FRB, INA, MFR, EXT/PRT/PRE,
 * REV/NSF/PND — plus a few unknown codes that must pass through unchanged. No
 * mocks: the startup migration transforms this data into the modern tables and
 * both providers read it back, so every code-expansion branch is validated end
 * to end and legacy/modern parity is proven for the alternate codes too.
 *
 * <p>The datasource URLs and legacy seed are overridden so this runs in its own
 * Spring context/H2 databases, independent of the golden 5/5/5/10 suite.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.datasource.legacy.url=jdbc:h2:mem:legacydw_edge;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.datasource.modern.url=jdbc:h2:mem:moderndw_edge;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.datasource.legacy.data=data-legacy-edge.sql",
        "loanservice.datasource=legacy"
})
class DataMigrationEdgeCaseTest {

    @Autowired private LegacyLoanDataProvider legacyProvider;
    @Autowired private ModernLoanDataProvider modernProvider;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private BorrowerRepository modernBorrowers;
    @Autowired private LoanProductRepository modernProducts;
    @Autowired private LoanAccountRepository modernAccounts;
    @Autowired private PaymentRepository modernPayments;

    @Test
    void migrationLoadedTheEdgeDataSet() {
        assertEquals(2, modernBorrowers.count());
        assertEquals(1, modernProducts.count());
        assertEquals(4, modernAccounts.count());
        assertEquals(4, modernPayments.count());
    }

    @Test
    void loanStatusCodesExpandAndDisplay() {
        assertEquals("Closed", modernProvider.getLoanById("EA-1").getStatus());
        assertEquals("Default", modernProvider.getLoanById("EA-2").getStatus());
        assertEquals("Forbearance", modernProvider.getLoanById("EA-3").getStatus());
        // Unknown code passes through unchanged.
        assertEquals("ZZZ", modernProvider.getLoanById("EA-4").getStatus());
    }

    @Test
    void propertyTypeCodesExpand() {
        assertEquals("Multi-Family Residence", modernProvider.getLoanById("EA-1").getPropertyType());
        assertEquals("QQQ", modernProvider.getLoanById("EA-2").getPropertyType());
    }

    @Test
    void paymentTypeAndStatusCodesExpandAndDisplay() {
        // EA-1 payments, newest first: EPMT-1 (12/15) then EPMT-2 (11/15).
        List<PaymentDto> ea1 = modernProvider.getPaymentsByLoan("EA-1");
        assertEquals(List.of("Extra", "Partial"), ea1.stream().map(PaymentDto::getType).toList());
        assertEquals(List.of("Reversed", "Non-Sufficient Funds"),
                ea1.stream().map(PaymentDto::getStatus).toList());

        // EA-2 payments: EPMT-3 (12/15) then EPMT-4 (11/15, unknown codes).
        List<PaymentDto> ea2 = modernProvider.getPaymentsByLoan("EA-2");
        assertEquals(List.of("Prepayment", "ZZ"), ea2.stream().map(PaymentDto::getType).toList());
        assertEquals(List.of("Pending", "ZZ"), ea2.stream().map(PaymentDto::getStatus).toList());
    }

    @Test
    void legacyAndModernAgreeOnEdgeData() throws Exception {
        assertJsonEquals(legacyProvider.getAllLoans(), modernProvider.getAllLoans());
        assertJsonEquals(legacyProvider.getAllBorrowers(), modernProvider.getAllBorrowers());
        for (String account : List.of("EA-1", "EA-2", "EA-3", "EA-4")) {
            assertJsonEquals(legacyProvider.getLoanById(account), modernProvider.getLoanById(account));
            assertJsonEquals(legacyProvider.getPaymentsByLoan(account),
                    modernProvider.getPaymentsByLoan(account));
        }
        for (String borrower : List.of("EB-1", "EB-2")) {
            assertJsonEquals(legacyProvider.getBorrowerById(borrower),
                    modernProvider.getBorrowerById(borrower));
        }
    }

    /** STRICT but numeric-aware JSON equality (same policy as the golden suite). */
    private void assertJsonEquals(Object expected, Object actual) throws Exception {
        JSONAssert.assertEquals(
                objectMapper.writeValueAsString(expected),
                objectMapper.writeValueAsString(actual),
                JSONCompareMode.STRICT);
    }
}
