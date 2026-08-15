package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Modern (normalized) data source. This is the source that serves the public
 * REST API once {@code DataMigrationService} has copied the legacy records over.
 *
 * <p>Its schema is created eagerly inside the {@code DataSource} bean method so
 * that the tables exist before any repository, migration runner or JPA bootstrap
 * touches the database.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.modern.repository",
        entityManagerFactoryRef = "modernEntityManagerFactory",
        transactionManagerRef = "modernTransactionManager")
public class ModernDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.modern")
    public DataSourceProperties modernDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource modernDataSource(
            @Qualifier("modernDataSourceProperties") DataSourceProperties properties) {
        DataSource dataSource = properties.initializeDataSourceBuilder().build();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema-modern.sql"),
                new ClassPathResource("data-modern.sql"));
        populator.execute(dataSource);
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean modernEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("modernDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties) {
        Map<String, Object> properties = hibernateProperties.determineHibernateProperties(
                jpaProperties.getProperties(), new HibernateSettings());
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
