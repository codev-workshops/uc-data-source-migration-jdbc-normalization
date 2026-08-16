package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
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

import javax.sql.DataSource;

/**
 * Wiring for the modern normalized data source, which is the primary one: it is
 * the end state of the migration, so unqualified injection points and
 * unqualified {@code @Transactional} resolve to it.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.repository.modern",
        entityManagerFactoryRef = "modernEntityManagerFactory",
        transactionManagerRef = "modernTransactionManager")
public class ModernDataSourceConfig {

    public static final String TRANSACTION_MANAGER = "modernTransactionManager";

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.modern")
    public DataSource modernDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public DataSourceInitializer modernDataSourceInitializer(
            @Qualifier("modernDataSource") DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-modern.sql"));

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean modernEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("modernDataSource") DataSource dataSource,
            JpaProperties jpaProperties) {
        return builder.dataSource(dataSource)
                .packages("com.workshop.loanservice.entity.modern")
                .persistenceUnit("modern")
                .properties(jpaProperties.getProperties())
                .build();
    }

    @Bean(TRANSACTION_MANAGER)
    @Primary
    public PlatformTransactionManager modernTransactionManager(
            @Qualifier("modernEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
