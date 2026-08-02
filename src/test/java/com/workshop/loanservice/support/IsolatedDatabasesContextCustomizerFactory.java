package com.workshop.loanservice.support;

import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;

import java.util.List;
import java.util.UUID;

/**
 * Gives every test context its own pair of in-memory databases.
 *
 * <p>The application points at named H2 databases with {@code DB_CLOSE_DELAY=-1}, which is correct
 * for a running service and wrong for a test JVM: Spring caches one context per distinct
 * configuration, and the second context to start would re-run the schema scripts against the
 * database the first one already created and fail on "table already exists". Worse, the contexts
 * that did start would share rows, so a payment posted by one test could decide the outcome of
 * another.
 *
 * <p>This is applied as a customizer rather than a {@code src/test/resources/application.properties}
 * because a test file of that name shadows the main one entirely, silently dropping every setting
 * the application relies on - actuator exposure, migration defaults, and so on.
 */
public class IsolatedDatabasesContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                     List<ContextConfigurationAttributes> configAttributes) {
        return new IsolatedDatabases();
    }

    /**
     * Deliberately not a value object: equality is identity-free so that contexts are still cached by
     * their own configuration, and each newly created context gets fresh database names.
     */
    private static final class IsolatedDatabases implements ContextCustomizer {

        @Override
        public void customizeContext(org.springframework.context.ConfigurableApplicationContext context,
                                     MergedContextConfiguration mergedConfig) {
            String suffix = UUID.randomUUID().toString();
            TestPropertyValues.of(
                "app.datasource.legacy.url=jdbc:h2:mem:legacydw-" + suffix
                    + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.datasource.modern.url=jdbc:h2:mem:moderndb-" + suffix
                    + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
            ).applyTo(context);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IsolatedDatabases;
        }

        @Override
        public int hashCode() {
            return IsolatedDatabases.class.hashCode();
        }
    }
}
