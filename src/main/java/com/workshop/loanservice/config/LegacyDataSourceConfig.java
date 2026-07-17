package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Legacy CDW data source. Marked {@link Primary} so it is the default for the
 * shared {@link EntityManagerFactoryBuilder} and any unqualified injection.
 * Its schema and seed data are initialized from the classpath legacy scripts.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.repository",
        entityManagerFactoryRef = "legacyEntityManagerFactory",
        transactionManagerRef = "legacyTransactionManager")
public class LegacyDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("app.datasource.legacy")
    public DataSourceProperties legacyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    public DataSource legacyDataSource() {
        return legacyDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "legacyEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean legacyEntityManagerFactory(EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(legacyDataSource())
                .packages("com.workshop.loanservice.entity")
                .persistenceUnit("legacy")
                .build();
    }

    @Primary
    @Bean
    public PlatformTransactionManager legacyTransactionManager(
            @Qualifier("legacyEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    public DataSourceInitializer legacyDataSourceInitializer(
            @Qualifier("legacyDataSource") DataSource dataSource,
            @Value("${app.datasource.legacy.schema:schema-legacy.sql}") String schemaLocation,
            @Value("${app.datasource.legacy.data:data-legacy.sql}") String dataLocation) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource(schemaLocation),
                new ClassPathResource(dataLocation));
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}
