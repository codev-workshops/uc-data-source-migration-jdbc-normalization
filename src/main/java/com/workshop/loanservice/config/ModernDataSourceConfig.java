package com.workshop.loanservice.config;

import com.workshop.loanservice.modern.entity.Borrower;
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
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;

/**
 * Primary data source: the normalized modern schema the API is served from.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.workshop.loanservice.modern.repository",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager")
public class ModernDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.modern")
    public DataSourceProperties modernDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource modernDataSource(@Qualifier("modernDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public DataSourceScriptDatabaseInitializer modernSchemaInitializer(@Qualifier("modernDataSource") DataSource dataSource) {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("classpath:schema-modern.sql"));
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        return new DataSourceScriptDatabaseInitializer(dataSource, settings);
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder,
                                                                       @Qualifier("modernDataSource") DataSource dataSource) {
        return builder.dataSource(dataSource)
                .packages(Borrower.class)
                .persistenceUnit("modern")
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(@Qualifier("entityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    @Primary
    public JdbcTemplate modernJdbcTemplate(@Qualifier("modernDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
