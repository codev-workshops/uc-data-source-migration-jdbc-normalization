package com.workshop.loanservice.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.LoanServiceApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a data-source parameter ({@code "legacy"} / {@code "modern"}) to the profile
 * that backs it and to a Spring context started with that profile active.
 *
 * <p>This is the heart of the parameterization: {@code @SpringBootTest} binds the
 * data source to the active profile and a single context cannot swap profiles
 * mid-test, so instead each data-source value gets its OWN per-profile context,
 * booted once and cached here for the whole suite. The single set of contract
 * assertions then runs against whichever context the parameter selects.
 */
public final class DataSourceContexts {

    /** data-source parameter value -> Spring profile name. */
    private static final Map<String, String> PROFILE_BY_DATASOURCE = Map.of(
            "legacy", "legacy",
            "modern", "modern");

    private static final Map<String, DataSourceContext> CACHE = new ConcurrentHashMap<>();

    private DataSourceContexts() {
    }

    /** Returns the cached context for the given data source, starting it on first use. */
    public static synchronized DataSourceContext forDataSource(String dataSource) {
        return CACHE.computeIfAbsent(dataSource, DataSourceContexts::start);
    }

    private static DataSourceContext start(String dataSource) {
        String profile = PROFILE_BY_DATASOURCE.get(dataSource);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown data source: " + dataSource);
        }
        // Each context gets its own isolated in-memory H2 database (unique name,
        // passed as a high-precedence command-line arg so it overrides the profile's
        // declared URL). This keeps the per-profile contexts, and the default-profile
        // @SpringBootTest, from clashing over a shared persistent mem DB in one JVM.
        String dbName = dataSource + "_" + System.nanoTime();
        ConfigurableApplicationContext context = new SpringApplicationBuilder(LoanServiceApplication.class)
                .profiles(profile)
                .run(
                        // random port so multiple per-profile contexts can coexist in one JVM
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:" + dbName
                                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        Runtime.getRuntime().addShutdownHook(new Thread(context::close));

        MockMvc mockMvc = MockMvcBuilders
                .webAppContextSetup((WebApplicationContext) context)
                .build();
        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
        return new DataSourceContext(dataSource, context, mockMvc, objectMapper);
    }
}
