package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

/**
 * Secondary (modern) persistence unit: scans the modern entity and repository
 * packages only, backed by the modern.datasource.* connection. The modern
 * schema is applied explicitly at startup because Spring Boot's SQL
 * initialization only covers the primary datasource.
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

    @Bean(name = "modernDataSource")
    public DataSource modernDataSource() {
        return modernDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean
    @ConditionalOnProperty(name = "modern.datasource.initialize-schema", havingValue = "true")
    public DataSourceInitializer modernDataSourceInitializer(
            @Qualifier("modernDataSource") DataSource modernDataSource) {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("schema-modern.sql"));
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(modernDataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    @Bean(name = "modernEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean modernEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("modernDataSource") DataSource modernDataSource) {
        return builder
                .dataSource(modernDataSource)
                .packages("com.workshop.loanservice.modern.entity")
                .persistenceUnit("modern")
                .build();
    }

    @Bean(name = "modernTransactionManager")
    public PlatformTransactionManager modernTransactionManager(
            @Qualifier("modernEntityManagerFactory") EntityManagerFactory modernEntityManagerFactory) {
        return new JpaTransactionManager(modernEntityManagerFactory);
    }
}
