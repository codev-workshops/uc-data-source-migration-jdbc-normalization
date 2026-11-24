package com.workshop.loanservice.golden;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The modern schema (populated by the startup migration) must produce the same
 * responses as the legacy application did.
 */
@SpringBootTest(properties = "loan.datasource.mode=modern")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModernGoldenApiTest extends AbstractGoldenApiTest {
}
