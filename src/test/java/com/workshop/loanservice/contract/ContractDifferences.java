package com.workshop.loanservice.contract;

import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.json.JSONException;

import java.util.List;

/**
 * Central comparison policy for the contract suite.
 *
 * <p>By default the comparison is STRICT JSON equality (exact values, exact array
 * ordering, no missing/extra fields) -- this is what pins the payment ordering and
 * the re-serialized date strings / preserved {@code paymentId} values.
 *
 * <p>{@code docs/MIGRATION_TASKS.md} Task 4 Step 4 explicitly permits documented,
 * intentional differences between the two data sources (e.g. date-format changes).
 * The {@link #ACCEPTED_DIFFERENCES} allow-list is the hook for that: any entry
 * relaxes the strict match for a specific JSON path. Per the mandatory
 * contract-stability requirement this list is EMPTY -- every field must match
 * byte-for-byte -- but the mechanism exists so future, justified differences can
 * be admitted deliberately rather than by loosening the whole suite.
 */
final class ContractDifferences {

    /**
     * Documented, accepted differences between data sources. MUST stay empty to
     * enforce full contract stability. To admit a justified difference, add a
     * {@link Customization} for its JSON path (and record it in TESTING_STRATEGY.md).
     */
    static final List<Customization> ACCEPTED_DIFFERENCES = List.of();

    private ContractDifferences() {
    }

    static void assertMatchesGolden(String goldenJson, String actualJson) throws JSONException {
        JSONComparator comparator = new CustomComparator(
                JSONCompareMode.STRICT,
                ACCEPTED_DIFFERENCES.toArray(new Customization[0]));
        JSONAssert.assertEquals(goldenJson, actualJson, comparator);
    }
}
