package com.workshop.loanservice.modern;

import com.workshop.loanservice.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one thing that is cached, and the reason it is safe to cache it.
 *
 * <p>Loan and borrower rows are deliberately not cached: they change, they are large, and a stale
 * balance is a correctness bug. The product code to id mapping is the opposite - a handful of rows
 * that change only when the product catalogue is edited - and the migration needs it once per chunk,
 * which is 500 full scans of {@code loan_products} on a 500k-row backfill without a cache.
 */
@SpringBootTest
class ProductCatalogCacheIT {

    @Autowired
    private ProductCatalog catalog;
    @Autowired
    private CacheManager cacheManager;

    @Test
    void theMappingIsServedFromTheCacheAfterTheFirstLookup() {
        Map<String, Long> first = catalog.idsByCode();
        Map<String, Long> second = catalog.idsByCode();

        assertThat(first).isNotEmpty().containsKey("FXD30");
        assertThat(second).isSameAs(first);
        assertThat(cacheManager.getCache(CacheConfig.LOAN_PRODUCTS).get(SimpleKey.EMPTY)).isNotNull();
    }

    /** Writing products must not leave the migration resolving ids against a stale map. */
    @Test
    void invalidationForcesAFreshLookup() {
        Map<String, Long> before = catalog.idsByCode();

        catalog.invalidate();

        assertThat(catalog.idsByCode())
            .isNotSameAs(before)
            .isEqualTo(before);
    }
}
