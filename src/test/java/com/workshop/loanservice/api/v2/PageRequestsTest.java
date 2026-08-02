package com.workshop.loanservice.api.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestsTest {

    @Test
    void defaultsToTheDefaultSize() {
        assertThat(PageRequests.clampSize(null)).isEqualTo(PageRequests.DEFAULT_SIZE);
    }

    @Test
    void clampsOversizedPagesInsteadOfTrustingTheCaller() {
        assertThat(PageRequests.clampSize(1_000_000)).isEqualTo(PageRequests.MAX_SIZE);
    }

    @Test
    void rejectsNonPositiveSize() {
        assertThatThrownBy(() -> PageRequests.clampSize(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> PageRequests.of(-1, 10, null, PageRequests.LOAN_SORT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultOrderIsTheStableIdOrder() {
        PageRequest request = PageRequests.of(null, null, null, PageRequests.LOAN_SORT);
        assertThat(request.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    void allowedSortIsAlwaysTieBrokenById() {
        PageRequest request = PageRequests.of(0, 10, "currentBalance,desc", PageRequests.LOAN_SORT);
        assertThat(request.getSort()).isEqualTo(
            Sort.by(Sort.Direction.DESC, "currentBalance").and(Sort.by(Sort.Direction.ASC, "id")));
    }

    /**
     * Spring Data turns a Sort property straight into a query fragment, so an unchecked sort
     * parameter is an injection point. Nothing outside the allow-list may reach it.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "id; DROP TABLE loan_accounts",
        "id) OR (1=1",
        "(SELECT 1)",
        "borrower.ssnHash",
        "ssnHash",
        "1=1"
    })
    void rejectsEverySortValueThatIsNotAllowListed(String malicious) {
        assertThatThrownBy(() -> PageRequests.of(0, 10, malicious, PageRequests.LOAN_SORT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unsupported sort field");
    }

    /** An allow-listed field does not make the direction free-form: it is asc or desc, nothing else. */
    @ParameterizedTest
    @ValueSource(strings = {"id,sideways", "currentBalance,desc; DELETE FROM payments", "id,asc) OR (1=1"})
    void rejectsAnyDirectionThatIsNotAscOrDesc(String malicious) {
        assertThatThrownBy(() -> PageRequests.of(0, 10, malicious, PageRequests.LOAN_SORT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("asc or desc");
    }

    @Test
    void sortAllowListNeverExposesSensitiveColumns() {
        assertThat(PageRequests.BORROWER_SORT.values()).doesNotContain("ssnHash", "annualIncome", "dateOfBirth");
    }
}
