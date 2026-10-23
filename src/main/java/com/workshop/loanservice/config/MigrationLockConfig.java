package com.workshop.loanservice.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ShedLock lease stored in the modern database's {@code shedlock} table, so instances sharing that
 * database never run the legacy migration concurrently. Lease times use the database clock.
 */
@Configuration(proxyBeanMethods = false)
public class MigrationLockConfig {

    @Bean
    public LockProvider migrationLockProvider(@Qualifier("modernDataSource") DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
