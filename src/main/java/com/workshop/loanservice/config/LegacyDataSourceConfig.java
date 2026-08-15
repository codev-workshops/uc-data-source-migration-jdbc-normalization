package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Legacy (CDW) data source. Remains the primary data source so that
 * {@code spring.sql.init.*} keeps seeding the legacy schema and so that the
 * migration and reconciliation code always has the source of record available.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.repository",
        entityManagerFactoryRef = "legacyEntityManagerFactory",
        transactionManagerRef = "legacyTransactionManager")
public class LegacyDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.legacy")
    public DataSourceProperties legacyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource legacyDataSource(
            @Qualifier("legacyDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean legacyEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("legacyDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties) {
        Map<String, Object> properties = hibernateProperties.determineHibernateProperties(
                jpaProperties.getProperties(), new HibernateSettings());
        return builder
                .dataSource(dataSource)
                .packages("com.workshop.loanservice.entity")
                .persistenceUnit("legacy")
                .properties(properties)
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager legacyTransactionManager(
            @Qualifier("legacyEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
