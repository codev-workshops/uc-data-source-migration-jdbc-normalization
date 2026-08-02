package com.workshop.loanservice.config;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Legacy CDW data source. Kept for the duration of the migration: it is the extract side of the
 * backfill, the shadow-read comparison source, and the rollback target.
 *
 * <p>Deliberately not {@code @Primary}. Every bean here is qualified, so a stray
 * {@code @Transactional} or an unqualified repository can never reach the legacy store by accident.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.workshop.loanservice.repository",
    entityManagerFactoryRef = "legacyEntityManagerFactory",
    transactionManagerRef = "legacyTransactionManager")
public class LegacyDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.legacy")
    public DataSourceProperties legacyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.legacy.hikari")
    public DataSource legacyDataSource(
        @Qualifier("legacyDataSourceProperties") DataSourceProperties legacyDataSourceProperties) {
        return legacyDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean legacyEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("legacyDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.default_batch_fetch_size", 100);
        // Feeds the Hibernate query/transaction timers Micrometer exports; the cost is a few counters.
        properties.put("hibernate.generate_statistics", true);

        return builder.dataSource(dataSource)
            .packages(LegacyBorrower.class.getPackageName())
            .persistenceUnit("legacy")
            .properties(properties)
            .build();
    }

    @Bean
    public PlatformTransactionManager legacyTransactionManager(
        @Qualifier("legacyEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * Seeds the in-memory warehouse with the workshop fixture. Switched off when the data source
     * already points at a populated database - the 500k-row load-test warehouse, for instance -
     * where re-running the schema script would only fail.
     */
    @Bean
    @ConditionalOnProperty(name = "app.datasource.legacy.initialize", havingValue = "true", matchIfMissing = true)
    public DataSourceInitializer legacyDataSourceInitializer(@Qualifier("legacyDataSource") DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(
            new ClassPathResource("schema-legacy.sql"),
            new ClassPathResource("data-legacy.sql")));
        return initializer;
    }
}
