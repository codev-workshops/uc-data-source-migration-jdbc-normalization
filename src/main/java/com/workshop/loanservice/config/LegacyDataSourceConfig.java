package com.workshop.loanservice.config;

import com.workshop.loanservice.entity.legacy.LegacyBorrower;
import com.workshop.loanservice.repository.legacy.LegacyBorrowerRepository;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Secondary (legacy CDW) persistence unit, bound to {@code legacy.datasource.*} and
 * {@code legacy.sql.init.*}. Kept so {@code DataMigrationService} can read the legacy tables while both
 * schemas coexist; the REST API only reads from it when {@code loan-service.read-source=legacy}.
 */
@SuppressWarnings("deprecation")
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackageClasses = LegacyBorrowerRepository.class,
        entityManagerFactoryRef = "legacyEntityManagerFactory",
        transactionManagerRef = "legacyTransactionManager")
public class LegacyDataSourceConfig {

    @Bean
    @ConfigurationProperties("legacy.datasource")
    public DataSourceProperties legacyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource legacyDataSource(
            @Qualifier("legacyDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConfigurationProperties("legacy.sql.init")
    public DatabaseInitializationSettings legacyDatabaseInitializationSettings() {
        return new DatabaseInitializationSettings();
    }

    /**
     * Deliberately the base {@link DataSourceScriptDatabaseInitializer} type: Spring Boot only
     * backs off its own {@code spring.sql.init} initializer (used for the modern schema) when an
     * {@code SqlDataSourceScriptDatabaseInitializer} bean exists.
     */
    @Bean
    public DataSourceScriptDatabaseInitializer legacyDataSourceInitializer(
            @Qualifier("legacyDataSource") DataSource dataSource,
            @Qualifier("legacyDatabaseInitializationSettings") DatabaseInitializationSettings settings) {
        return new DataSourceScriptDatabaseInitializer(dataSource, settings);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean legacyEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("legacyDataSource") DataSource dataSource) {
        return builder.dataSource(dataSource)
                .packages(LegacyBorrower.class)
                .persistenceUnit("legacy")
                .properties(Map.of("hibernate.hbm2ddl.auto", "none"))
                .build();
    }

    @Bean
    public PlatformTransactionManager legacyTransactionManager(
            @Qualifier("legacyEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
