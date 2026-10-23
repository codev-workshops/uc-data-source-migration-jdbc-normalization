package com.workshop.loanservice;

import com.workshop.loanservice.service.LoanDataSource;
import com.workshop.loanservice.service.ModernLoanDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies every endpoint, served from the modern schema (the default {@code loan-service.read-source}),
 * returns the same JSON that the legacy implementation produced.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModernApiGoldenFileTest extends ApiGoldenFileTestSupport {

    @Override
    Class<? extends LoanDataSource> expectedDataSource() {
        return ModernLoanDataSource.class;
    }
}
