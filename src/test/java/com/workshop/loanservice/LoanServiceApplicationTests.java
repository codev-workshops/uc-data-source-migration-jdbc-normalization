package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:loansvc-${random.uuid};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class LoanServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
