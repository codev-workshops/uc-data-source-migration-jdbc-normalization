package com.workshop.loanservice;

import com.workshop.loanservice.service.LegacyLoanDataSource;
import com.workshop.loanservice.service.LoanDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies every endpoint, served from the legacy CDW tables ({@code loan-service.read-source=legacy}),
 * returns the same JSON as the pre-migration golden files, so either read source can back the API.
 */
@SpringBootTest(properties = LoanDataSource.READ_SOURCE_PROPERTY + "=legacy")
@AutoConfigureMockMvc
class LegacyApiGoldenFileTest extends ApiGoldenFileTestSupport {

    @Override
    Class<? extends LoanDataSource> expectedDataSource() {
        return LegacyLoanDataSource.class;
    }
}
