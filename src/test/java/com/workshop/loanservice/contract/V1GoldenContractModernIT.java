package com.workshop.loanservice.contract;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** The cutover path: v1 served entirely from the migrated schema must be byte-identical to legacy. */
@SpringBootTest(properties = "loanservice.read-source=modern")
@AutoConfigureMockMvc
class V1GoldenContractModernIT extends V1GoldenContractTestBase {
}
