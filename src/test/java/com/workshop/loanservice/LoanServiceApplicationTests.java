package com.workshop.loanservice;

import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoanServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsModernSchemaByDefault() {
        assertThat(applicationContext.getBeansOfType(LegacyBorrowerRepository.class))
                .isEmpty();
        assertThat(tableCount("BORROWERS")).isEqualTo(1);
        assertThat(tableCount("LOAN_PRODUCTS")).isEqualTo(1);
        assertThat(tableCount("LOAN_ACCOUNTS")).isEqualTo(1);
        assertThat(tableCount("PAYMENTS")).isEqualTo(1);
        assertThat(tableCount("CDW_BORR_MSTR")).isZero();
    }

    private Integer tableCount(String tableName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ?
                """,
                Integer.class,
                tableName
        );
    }
}
