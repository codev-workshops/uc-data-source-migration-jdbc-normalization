package com.workshop.loanservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * In-process caching only.
 *
 * <p>Caffeine, not Redis: the only table worth caching is {@code loan_products} - a handful of rows,
 * read on every loan-account resolution, changed a few times a year. A shared cache would add a
 * network hop and an operational dependency to save a lookup that fits in a few kilobytes of heap.
 * The loan and borrower tables are deliberately not cached: they are large, per-entity, and written
 * to, so a cache would mostly serve misses and invalidation bugs.
 *
 * <p>{@code recordStats} is on so hit ratio shows up in {@code /actuator/metrics/cache.gets}; a cache
 * whose hit ratio nobody can see is a guess, not an optimisation.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String LOAN_PRODUCTS = "loanProducts";

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(LOAN_PRODUCTS);
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats());
        return manager;
    }
}
