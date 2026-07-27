package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Legacy (CDW) datasource, entity manager and transaction manager.
 *
 * <p>Declared explicitly because a second datasource is present; the beans are
 * marked {@link Primary} so existing behaviour (SQL init, default transactions)
 * is unchanged and still driven by the {@code spring.datasource.*} and
 * {@code spring.jpa.*} properties.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.repository",
        entityManagerFactoryRef = "legacyEntityManagerFactory",
        transactionManagerRef = "legacyTransactionManager")
public class LegacyDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties legacyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    public DataSource legacyDataSource(
            @Qualifier("legacyDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean legacyEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("legacyDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.workshop.loanservice.entity")
                .persistenceUnit("legacy")
                .build();
    }

    @Primary
    @Bean
    public PlatformTransactionManager legacyTransactionManager(
            @Qualifier("legacyEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
