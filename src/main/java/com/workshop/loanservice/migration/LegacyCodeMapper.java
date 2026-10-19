package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.modern.BorrowerStatus;
import com.workshop.loanservice.entity.modern.LoanStatus;
import com.workshop.loanservice.entity.modern.PaymentStatus;
import com.workshop.loanservice.entity.modern.PaymentType;

import java.util.Locale;
import java.util.Map;

/** Expands legacy CDW status/type codes into modern schema values (see data/mappings/column_mappings.md). */
final class LegacyCodeMapper {

    private static final Map<String, BorrowerStatus> BORROWER_STATUS = Map.of(
            "ACT", BorrowerStatus.ACTIVE,
            "INA", BorrowerStatus.INACTIVE);

    private static final Map<String, Boolean> PRODUCT_ACTIVE = Map.of(
            "ACT", Boolean.TRUE,
            "INA", Boolean.FALSE);

    private static final Map<String, LoanStatus> LOAN_STATUS = Map.of(
            "ACT", LoanStatus.ACTIVE,
            "CLO", LoanStatus.CLOSED,
            "DFT", LoanStatus.DEFAULT,
            "FRB", LoanStatus.FORBEARANCE);

    private static final Map<String, String> PROPERTY_TYPE = Map.of(
            "SFR", "Single Family Residence",
            "CND", "Condominium",
            "MFR", "Multi-Family Residence",
            "TWN", "Townhouse");

    private static final Map<String, PaymentType> PAYMENT_TYPE = Map.of(
            "REG", PaymentType.REGULAR,
            "EXT", PaymentType.EXTRA,
            "PRT", PaymentType.PARTIAL,
            "PRE", PaymentType.PREPAYMENT);

    private static final Map<String, PaymentStatus> PAYMENT_STATUS = Map.of(
            "PST", PaymentStatus.POSTED,
            "REV", PaymentStatus.REVERSED,
            "NSF", PaymentStatus.NSF,
            "PND", PaymentStatus.PENDING);

    private LegacyCodeMapper() {
    }

    /** Blank defaults to {@link BorrowerStatus#ACTIVE}, matching the modern column default. */
    static BorrowerStatus borrowerStatus(String code) {
        return lookupOrDefault("BORR_STAT_CD", code, BORROWER_STATUS, BorrowerStatus.ACTIVE);
    }

    /** Blank defaults to {@code true}, matching the modern {@code is_active} column default. */
    static Boolean productActive(String code) {
        return lookupOrDefault("PROD_STAT_CD", code, PRODUCT_ACTIVE, Boolean.TRUE);
    }

    /**
     * Blank stays {@code null} (rendered as "Unknown" by the API, as before). Unrecognised codes map to
     * {@link LoanStatus#UNKNOWN} instead of rejecting the loan; the caller keeps the original code.
     */
    static LoanStatus loanStatus(String code) {
        String normalized = normalize(code);
        if (normalized == null) {
            return null;
        }
        return LOAN_STATUS.getOrDefault(normalized, LoanStatus.UNKNOWN);
    }

    /** Unrecognised codes are carried over verbatim, as the legacy API displayed them. */
    static String propertyType(String code) {
        String normalized = normalize(code);
        return normalized == null ? null : PROPERTY_TYPE.getOrDefault(normalized, code.trim());
    }

    static PaymentType paymentType(String code) {
        return LegacyValueParser.require("PMT_TYP_CD",
                lookupOrDefault("PMT_TYP_CD", code, PAYMENT_TYPE, null));
    }

    static PaymentStatus paymentStatus(String code) {
        return LegacyValueParser.require("PMT_STAT_CD",
                lookupOrDefault("PMT_STAT_CD", code, PAYMENT_STATUS, null));
    }

    private static <T> T lookupOrDefault(String column, String code, Map<String, T> mapping, T blankDefault) {
        String normalized = normalize(code);
        if (normalized == null) {
            return blankDefault;
        }
        T value = mapping.get(normalized);
        if (value == null) {
            throw new LegacyDataException(column + " has an unknown code");
        }
        return value;
    }

    private static String normalize(String code) {
        String trimmed = LegacyValueParser.trimToNull(code);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
