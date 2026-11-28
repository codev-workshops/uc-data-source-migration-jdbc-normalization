package com.workshop.loanservice.golden;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sanity check: the deprecated legacy read path still produces the golden responses.
 */
@SpringBootTest(properties = "loan.datasource.mode=legacy")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegacyGoldenApiTest extends AbstractGoldenApiTest {
}
