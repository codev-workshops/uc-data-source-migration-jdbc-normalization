package com.workshop.loanservice.config;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.modern.entity.Borrower;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Two persistence units: the legacy CDW tables ({@code spring.datasource.*}, primary, still
 * serving the API) and the normalized schema ({@code app.datasource.modern.*}).
 */
public class DataSourceConfig {

    private DataSourceConfig() {}

    @Configuration
    @EnableJpaRepositories(basePackages = "com.workshop.loanservice.repository")
    static class Legacy {

        @Bean
        @Primary
        @ConfigurationProperties("spring.datasource")
        DataSourceProperties legacyDataSourceProperties() {
            return new DataSourceProperties();
        }

        @Bean
        @Primary
        DataSource dataSource(DataSourceProperties legacyDataSourceProperties) {
            return legacyDataSourceProperties.initializeDataSourceBuilder().build();
        }

        @Bean
        @Primary
        LocalContainerEntityManagerFactoryBean entityManagerFactory(
                EntityManagerFactoryBuilder builder, DataSource dataSource) {
            return builder.dataSource(dataSource)
                    .packages(LegacyBorrower.class)
                    .persistenceUnit("legacy")
                    .build();
        }

        @Bean
        @Primary
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.workshop.loanservice.modern.repository",
            entityManagerFactoryRef = "modernEntityManagerFactory",
            transactionManagerRef = "modernTransactionManager")
    static class Modern {

        @Bean
        @ConfigurationProperties("app.datasource.modern")
        DataSourceProperties modernDataSourceProperties() {
            return new DataSourceProperties();
        }

        /** Creates the schema here so it exists before Hibernate validates the entities. */
        @Bean
        DataSource modernDataSource(
                @Qualifier("modernDataSourceProperties") DataSourceProperties properties) {
            DataSource dataSource = properties.initializeDataSourceBuilder().build();
            new ResourceDatabasePopulator(new ClassPathResource("schema-modern.sql"))
                    .execute(dataSource);
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean modernEntityManagerFactory(
                EntityManagerFactoryBuilder builder,
                @Qualifier("modernDataSource") DataSource dataSource) {
            return builder.dataSource(dataSource)
                    .packages(Borrower.class)
                    .persistenceUnit("modern")
                    .properties(Map.of(
                            "hibernate.hbm2ddl.auto", "validate",
                            "hibernate.physical_naming_strategy",
                            CamelCaseToUnderscoresNamingStrategy.class.getName()))
                    .build();
        }

        @Bean
        PlatformTransactionManager modernTransactionManager(
                @Qualifier("modernEntityManagerFactory") EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
