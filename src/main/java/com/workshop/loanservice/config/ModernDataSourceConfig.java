package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
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

/**
 * Modern normalized data source, wired alongside the legacy one for dual-read.
 * Only the DDL is initialized here ({@code schema-modern.sql}); the rows are
 * populated at startup by the migration utility (see
 * {@code com.workshop.loanservice.migration.DataMigrationService}).
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.modern.repository",
        entityManagerFactoryRef = "modernEntityManagerFactory",
        transactionManagerRef = "modernTransactionManager")
public class ModernDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.modern")
    public DataSourceProperties modernDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource modernDataSource() {
        return modernDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean(name = "modernEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean modernEntityManagerFactory(EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(modernDataSource())
                .packages("com.workshop.loanservice.modern.entity")
                .persistenceUnit("modern")
                .build();
    }

    @Bean
    public PlatformTransactionManager modernTransactionManager(
            @Qualifier("modernEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    public DataSourceInitializer modernDataSourceInitializer(@Qualifier("modernDataSource") DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema-modern.sql"));
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}
