package com.workshop.loanservice.contract;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** The cutover window: modern answers the request while legacy is shadow-read and compared. */
@SpringBootTest(properties = "loanservice.read-source=dual_read")
@AutoConfigureMockMvc
class V1GoldenContractDualReadIT extends V1GoldenContractTestBase {
}
