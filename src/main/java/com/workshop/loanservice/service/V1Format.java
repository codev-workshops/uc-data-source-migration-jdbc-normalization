package com.workshop.loanservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Renders modern typed values back into the exact wire shapes the frozen v1 contract has always
 * emitted. This is the only place where v1's presentation quirks live.
 *
 * <p><b>Why scale matters.</b> Jackson serialises a {@code BigDecimal} with its scale intact, so
 * {@code 285000} and {@code 285000.00} are different bytes on the wire. Legacy stored amounts as
 * strings and inherited whatever scale the source text happened to have: {@code LN_ORIG_AMT} is
 * written as {@code "285,000"} (scale 0) while {@code PMT_LATE_FEE} is written as {@code "0.00"}
 * (scale 2). The modern schema stores every amount as {@code DECIMAL(_,2)}, so that distinction is
 * gone from the data and has to be reapplied per field on the way out:
 * {@link #originationAmount(BigDecimal)} for the whole-dollar column, {@link #money(BigDecimal)} for
 * the rest.
 *
 * <p>The residual difference is a legacy value written with a non-standard scale, for example
 * {@code "285,000.5"}, which legacy renders as {@code 285000.5} and this renders as
 * {@code 285000.50}. No such value exists in the seed data; it is called out in
 * {@code docs/MIGRATION_NOTES.md} rather than papered over.
 */
public final class V1Format {

    private static final DateTimeFormatter V1_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private V1Format() {
    }

    /** {@code LocalDate} to the {@code MM/DD/YYYY} string v1 clients parse. */
    public static String date(LocalDate date) {
        return date == null ? null : date.format(V1_DATE);
    }

    /** Money as v1 emits it for every column except the origination amount: always two decimals. */
    public static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** {@code LN_ORIG_AMT} was stored in whole dollars, so v1 emits it without a cents part. */
    public static BigDecimal originationAmount(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.setScale(0) : value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Rates keep the scale the column defines; v1 has always emitted three decimals. */
    public static BigDecimal rate(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(3, RoundingMode.UNNECESSARY);
    }

    /** v1 concatenates the four property columns into one line. */
    public static String propertyAddress(String line, String city, String state, String zip) {
        return line + ", " + city + ", " + state + " " + zip;
    }

    /** v1 renders the borrower name as "First M. Last", dropping the middle part when absent. */
    public static String fullName(String first, String middleInitial, String last) {
        String middle = middleInitial != null ? " " + middleInitial + "." : "";
        return first + middle + " " + last;
    }
}
