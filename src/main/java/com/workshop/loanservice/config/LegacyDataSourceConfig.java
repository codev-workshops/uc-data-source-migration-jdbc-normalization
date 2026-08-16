package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Wiring for the legacy CDW data source.
 *
 * <p>Spring Boot's single-data-source auto-configuration does not apply once two
 * data sources exist, so the entity manager factory, transaction manager and
 * schema initialisation are all declared explicitly here. Beans are qualified
 * with {@code legacy} and the repositories in
 * {@code com.workshop.loanservice.repository.legacy} are bound to them.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.repository.legacy",
        entityManagerFactoryRef = "legacyEntityManagerFactory",
        transactionManagerRef = "legacyTransactionManager")
public class LegacyDataSourceConfig {

    public static final String TRANSACTION_MANAGER = "legacyTransactionManager";

    @Bean
    @ConfigurationProperties("spring.datasource.legacy")
    public DataSource legacyDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public DataSourceInitializer legacyDataSourceInitializer(
            @Qualifier("legacyDataSource") DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new org.springframework.core.io.ClassPathResource("schema-legacy.sql"));
        populator.addScript(new org.springframework.core.io.ClassPathResource("data-legacy.sql"));

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean legacyEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("legacyDataSource") DataSource dataSource,
            JpaProperties jpaProperties) {
        return builder.dataSource(dataSource)
                .packages("com.workshop.loanservice.entity.legacy")
                .persistenceUnit("legacy")
                .properties(jpaProperties.getProperties())
                .build();
    }

    @Bean(TRANSACTION_MANAGER)
    public PlatformTransactionManager legacyTransactionManager(
            @Qualifier("legacyEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
