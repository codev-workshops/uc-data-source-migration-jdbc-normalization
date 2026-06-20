package com.workshop.loanservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Modern (target) DataSource: a normalized, strongly typed H2 schema.
 *
 * This is the PRIMARY persistence unit. The application reads from it once the
 * migration has populated it; the legacy unit remains wired so the source data
 * can be re-read and the system can be switched back if needed.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.modern.repository",
        entityManagerFactoryRef = "modernEntityManagerFactory",
        transactionManagerRef = "modernTransactionManager")
public class ModernDataSourceConfig {

    @Primary
    @Bean
    public DataSource modernDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:h2:mem:moderndb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
                .driverClassName("org.h2.Driver")
                .username("sa")
                .password("")
                .build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean modernEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("modernDataSource") DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        return builder.dataSource(dataSource)
                .packages("com.workshop.loanservice.modern.entity")
                .persistenceUnit("modern")
                .properties(properties)
                .build();
    }

    @Primary
    @Bean
    public PlatformTransactionManager modernTransactionManager(
            @Qualifier("modernEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
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
}
