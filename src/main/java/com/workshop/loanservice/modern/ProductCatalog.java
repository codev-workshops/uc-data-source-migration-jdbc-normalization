package com.workshop.loanservice.modern;

import com.workshop.loanservice.config.CacheConfig;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Product code to surrogate id, cached.
 *
 * <p>The migration resolves this mapping for every loan account; without a cache that is one
 * {@code SELECT * FROM loan_products} per chunk, i.e. 500 extra full scans on a 500k-row backfill.
 * Ids are cached rather than entities on purpose - a cached detached entity is a stale-state bug
 * waiting to happen, whereas an id is immutable, and
 * {@link org.springframework.data.jpa.repository.JpaRepository#getReferenceById} turns it back into
 * a usable association without a read.
 */
@Component
public class ProductCatalog {

    private final LoanProductRepository products;

    public ProductCatalog(LoanProductRepository products) {
        this.products = products;
    }

    @Cacheable(CacheConfig.LOAN_PRODUCTS)
    @Transactional(transactionManager = "modernTransactionManager", readOnly = true)
    public Map<String, Long> idsByCode() {
        Map<String, Long> byCode = new HashMap<>();
        products.findAll().forEach(p -> byCode.put(p.getCode(), p.getId()));
        return byCode;
    }

    /** Called after the product table is written to, which during a migration is exactly once. */
    @CacheEvict(value = CacheConfig.LOAN_PRODUCTS, allEntries = true)
    public void invalidate() {
        // The annotation is the behaviour.
    }

    public LoanProduct reference(Long id) {
        return products.getReferenceById(id);
    }
}
