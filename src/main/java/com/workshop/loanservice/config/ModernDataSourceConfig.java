package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
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
 * Modern (normalized) schema datasource, entity manager and transaction manager.
 *
 * <p>Coexists with the legacy datasource; configured from the
 * {@code modern.datasource.*} and {@code modern.jpa.*} properties.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.modern.repository",
        entityManagerFactoryRef = "modernEntityManagerFactory",
        transactionManagerRef = "modernTransactionManager")
public class ModernDataSourceConfig {

    @Bean
    @ConfigurationProperties("modern.datasource")
    public DataSourceProperties modernDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource modernDataSource(
            @Qualifier("modernDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    /**
     * Runs the approved modern DDL against the modern datasource; the equivalent of
     * {@code spring.sql.init}, which only applies to the primary (legacy) datasource.
     */
    @Bean
    public DataSourceInitializer modernDataSourceInitializer(
            @Qualifier("modernDataSource") DataSource dataSource,
            @Value("${modern.sql.init.schema-locations}") Resource[] schemaLocations) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(schemaLocations));
        return initializer;
    }

    @Bean
    @DependsOn("modernDataSourceInitializer")
    public LocalContainerEntityManagerFactoryBean modernEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("modernDataSource") DataSource dataSource,
            Environment environment) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto",
                environment.getProperty("modern.jpa.hibernate.ddl-auto", "none"));
        properties.put("hibernate.dialect",
                environment.getProperty("modern.jpa.database-platform", "org.hibernate.dialect.H2Dialect"));
        return builder
                .dataSource(dataSource)
                .packages("com.workshop.loanservice.modern.entity")
                .persistenceUnit("modern")
                .properties(properties)
                .build();
    }

    @Bean
    public PlatformTransactionManager modernTransactionManager(
            @Qualifier("modernEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
