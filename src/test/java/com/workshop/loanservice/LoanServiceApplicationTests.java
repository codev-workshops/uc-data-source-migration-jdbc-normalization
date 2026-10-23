package com.workshop.loanservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.service.LoanService;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class LoanServiceApplicationTests {

    private static final String MIGRATION_LOCK = "legacy-to-modern-migration";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource modernDataSource;

    @Autowired
    private LockProvider lockProvider;

    @Test
    void contextLoads() {
    }

    @Test
    void modernPersistenceUnitValidatesSchemaOnStartup() {
        assertThat(entityManagerFactory.getProperties()).containsEntry("hibernate.hbm2ddl.auto", "validate");
    }

    @Test
    void loanServiceContainsNoLegacyParsingOrCodeExpansion() {
        assertThat(Arrays.stream(LoanService.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("parse") || name.startsWith("expand"));
    }

    @Test
    void loanServiceDependsOnlyOnModernRepositories() {
        assertThat(Arrays.stream(LoanService.class.getDeclaredFields()).map(Field::getType))
                .allSatisfy(type -> assertThat(type.getPackageName())
                        .doesNotContain(".legacy"));
    }

    @Test
    void h2ConsoleIsDisabledOutsideTheDevProfile() {
        assertThat(applicationContext.getEnvironment().getProperty("spring.h2.console.enabled", Boolean.class))
                .isFalse();
        assertThat(applicationContext.containsBean("h2Console")).isFalse();
    }

    @Test
    void startupMigrationReleasesItsLockAndTheLockExcludesConcurrentHolders() {
        Integer lockRows = new JdbcTemplate(modernDataSource).queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = ?", Integer.class, MIGRATION_LOCK);
        assertThat(lockRows).isEqualTo(1);

        Optional<SimpleLock> first = lockProvider.lock(lockConfiguration());
        assertThat(first).isPresent();
        try {
            assertThat(lockProvider.lock(lockConfiguration())).isEmpty();
        } finally {
            first.get().unlock();
        }
    }

    private static LockConfiguration lockConfiguration() {
        return new LockConfiguration(Instant.now(), MIGRATION_LOCK, Duration.ofMinutes(1), Duration.ZERO);
    }
}
