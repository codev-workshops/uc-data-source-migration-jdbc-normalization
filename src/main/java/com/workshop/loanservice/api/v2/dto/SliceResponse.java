package com.workshop.loanservice.api.v2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * v2 list envelope.
 *
 * <p>{@code totalElements} is absent unless the caller asked for it with {@code count=true}. A
 * {@code COUNT(*)} over 500k rows costs as much as the page itself and almost no caller uses the
 * number, so it is opt-in rather than free-by-default.
 *
 * <p>{@code nextAfterId} is the cursor for the next page. Following it uses keyset pagination, which
 * stays flat as the offset grows; the Phase 0 benchmark measured 0.11 ms for a deep keyset page
 * against 85 ms for the equivalent deep offset page.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SliceResponse<T>(List<T> content,
                               int size,
                               boolean hasNext,
                               Long nextAfterId,
                               Long totalElements) {

    public static <T> SliceResponse<T> of(List<T> content, boolean hasNext, Long nextAfterId, Long totalElements) {
        return new SliceResponse<>(content, content.size(), hasNext, hasNext ? nextAfterId : null, totalElements);
    }
}
