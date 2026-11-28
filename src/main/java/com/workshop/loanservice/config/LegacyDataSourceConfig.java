package com.workshop.loanservice.config;

import com.workshop.loanservice.entity.LegacyBorrower;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;

/**
 * Secondary data source: the legacy CDW tables. Used as the migration source
 * and by the deprecated legacy read path ({@code loan.datasource.mode=legacy}).
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.repository",
        entityManagerFactoryRef = "legacyEntityManagerFactory",
        transactionManagerRef = "legacyTransactionManager")
public class LegacyDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.legacy")
    public DataSourceProperties legacyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource legacyDataSource(@Qualifier("legacyDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public DataSourceScriptDatabaseInitializer legacySchemaInitializer(@Qualifier("legacyDataSource") DataSource dataSource) {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("classpath:schema-legacy.sql"));
        settings.setDataLocations(List.of("classpath:data-legacy.sql"));
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        return new DataSourceScriptDatabaseInitializer(dataSource, settings);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean legacyEntityManagerFactory(EntityManagerFactoryBuilder builder,
                                                                             @Qualifier("legacyDataSource") DataSource dataSource) {
        return builder.dataSource(dataSource)
                .packages(LegacyBorrower.class)
                .persistenceUnit("legacy")
                .build();
    }

    @Bean
    public PlatformTransactionManager legacyTransactionManager(@Qualifier("legacyEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    public JdbcTemplate legacyJdbcTemplate(@Qualifier("legacyDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
