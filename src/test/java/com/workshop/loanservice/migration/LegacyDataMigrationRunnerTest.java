package com.workshop.loanservice.migration;

import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:legacymigrationrunner;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-legacy.sql,classpath:schema-modern.sql",
        "spring.sql.init.data-locations=classpath:data-legacy.sql"
})
@ActiveProfiles("legacy-migration-run")
class LegacyDataMigrationRunnerTest {

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanProductRepository loanProductRepository;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void migratesLegacyDataWhenRunnerProfileIsActive() {
        assertThat(borrowerRepository.count()).isEqualTo(5);
        assertThat(loanProductRepository.count()).isEqualTo(5);
        assertThat(loanAccountRepository.count()).isEqualTo(5);
        assertThat(paymentRepository.count()).isEqualTo(10);
    }
}
