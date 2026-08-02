package com.workshop.loanservice.api.v2;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link PageRequest} from v2 query parameters.
 *
 * <p>Two rules, both non-negotiable:
 * <ul>
 *   <li><b>Bounded size.</b> {@code size} is clamped to {@link #MAX_SIZE}. An unbounded page
 *       parameter is just the v1 out-of-memory problem with extra steps.</li>
 *   <li><b>Allow-listed sort.</b> A sort key is only ever accepted if it is a key of the supplied
 *       map, and what reaches JPA is the mapped entity property, never the caller's string. Spring
 *       Data will happily build a query fragment out of a sort value, so an allow-list - not
 *       escaping, not validation by regex - is what keeps this side of the API free of injected
 *       ordering expressions.</li>
 * </ul>
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** Sortable v2 loan fields, mapped from the public name to the entity property. */
    public static final Map<String, String> LOAN_SORT = Map.of(
        "id", "id",
        "accountNumber", "accountNumber",
        "currentBalance", "currentBalance",
        "originationDate", "originationDate",
        "status", "status");

    public static final Map<String, String> BORROWER_SORT = Map.of(
        "id", "id",
        "externalId", "externalId",
        "lastName", "lastName",
        "creditScore", "creditScore");

    private static final Set<String> DIRECTIONS = Set.of("asc", "desc");

    private PageRequests() {
    }

    public static int clampSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * @param sort {@code field} or {@code field,direction}; null means the entity id ascending,
     *             which is the only ordering guaranteed to be stable across pages
     */
    public static PageRequest of(Integer page, Integer size, String sort, Map<String, String> allowedSorts) {
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        return PageRequest.of(pageNumber, clampSize(size), sortOf(sort, allowedSorts));
    }

    private static Sort sortOf(String sort, Map<String, String> allowedSorts) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "id");
        }
        String[] parts = sort.split(",", 2);
        String property = allowedSorts.get(parts[0].trim());
        if (property == null) {
            throw new IllegalArgumentException("unsupported sort field; allowed: " + allowedSorts.keySet());
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String requested = parts[1].trim().toLowerCase(Locale.ROOT);
            if (!DIRECTIONS.contains(requested)) {
                throw new IllegalArgumentException("sort direction must be asc or desc");
            }
            direction = Sort.Direction.fromString(requested);
        }
        // Ties are broken by id so a page boundary never drops or repeats a row.
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
