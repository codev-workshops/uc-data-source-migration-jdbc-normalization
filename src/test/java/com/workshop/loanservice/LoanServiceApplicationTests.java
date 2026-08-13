package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:contextloadsdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class LoanServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
