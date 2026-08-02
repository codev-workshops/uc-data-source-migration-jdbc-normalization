package com.workshop.loanservice.contract;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** The rollback path: v1 served from the untouched legacy tables must still match the golden files. */
@SpringBootTest(properties = "loanservice.read-source=legacy")
@AutoConfigureMockMvc
class V1GoldenContractLegacyIT extends V1GoldenContractTestBase {
}
