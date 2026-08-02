package com.workshop.loanservice.config;

import com.workshop.loanservice.modern.entity.Borrower;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Modern data source: the target of the migration and, once the flag flips, the system of record.
 *
 * <p>It is {@code @Primary} so that everything which is not explicitly qualified (including plain
 * {@code @Transactional}) talks to the modern store. The legacy store is reachable only through the
 * explicitly qualified beans in {@link LegacyDataSourceConfig}.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.workshop.loanservice.modern.repository",
    entityManagerFactoryRef = "modernEntityManagerFactory",
    transactionManagerRef = "modernTransactionManager")
public class ModernDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.modern")
    public DataSourceProperties modernDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.modern.hikari")
    public DataSource modernDataSource(DataSourceProperties modernDataSourceProperties) {
        return modernDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean modernEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("modernDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        // batching keeps the migration's insert path set-based rather than row-at-a-time
        properties.put("hibernate.jdbc.batch_size", "500");
        properties.put("hibernate.order_inserts", true);
        properties.put("hibernate.order_updates", true);
        properties.put("hibernate.default_batch_fetch_size", 100);
        // Feeds the Hibernate query/transaction timers Micrometer exports; the cost is a few counters.
        properties.put("hibernate.generate_statistics", true);

        return builder.dataSource(dataSource)
            .packages(Borrower.class.getPackageName())
            .persistenceUnit("modern")
            .properties(properties)
            .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager modernTransactionManager(
        @Qualifier("modernEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public DataSourceInitializer modernDataSourceInitializer(@Qualifier("modernDataSource") DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema-modern.sql")));
        return initializer;
    }
}
