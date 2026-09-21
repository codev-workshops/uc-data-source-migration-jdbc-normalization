package com.workshop.loanservice.service.migration;

import java.util.Map;

/**
 * Expands legacy short codes to the canonical modern values defined in
 * data/mappings/column_mappings.md. Unknown, null or blank codes raise
 * {@link MigrationException}; there is deliberately no fallback value.
 */
public final class LegacyCodeMapper {

    private static final Map<String, String> BORROWER_STATUS = Map.of(
            "ACT", "ACTIVE",
            "INA", "INACTIVE");

    private static final Map<String, String> LOAN_STATUS = Map.of(
            "ACT", "ACTIVE",
            "CLO", "CLOSED",
            "DFT", "DEFAULT",
            "FRB", "FORBEARANCE");

    private static final Map<String, Boolean> PRODUCT_ACTIVE = Map.of(
            "ACT", Boolean.TRUE,
            "INA", Boolean.FALSE);

    private static final Map<String, String> PAYMENT_TYPE = Map.of(
            "REG", "REGULAR",
            "EXT", "EXTRA",
            "PRT", "PARTIAL",
            "PRE", "PREPAYMENT");

    private static final Map<String, String> PAYMENT_STATUS = Map.of(
            "PST", "POSTED",
            "REV", "REVERSED",
            "NSF", "NSF",
            "PND", "PENDING");

    private static final Map<String, String> PROPERTY_TYPE = Map.of(
            "SFR", "SINGLE_FAMILY",
            "CND", "CONDOMINIUM",
            "MFR", "MULTI_FAMILY",
            "TWN", "TOWNHOUSE");

    private LegacyCodeMapper() {
    }

    public static String borrowerStatus(String code, String recordId) {
        return lookup(BORROWER_STATUS, code, recordId, "BORR_STAT_CD");
    }

    public static String loanStatus(String code, String recordId) {
        return lookup(LOAN_STATUS, code, recordId, "LN_STAT_CD");
    }

    public static Boolean productActive(String code, String recordId) {
        return lookup(PRODUCT_ACTIVE, code, recordId, "PROD_STAT_CD");
    }

    public static String paymentType(String code, String recordId) {
        return lookup(PAYMENT_TYPE, code, recordId, "PMT_TYP_CD");
    }

    public static String paymentStatus(String code, String recordId) {
        return lookup(PAYMENT_STATUS, code, recordId, "PMT_STAT_CD");
    }

    public static String propertyType(String code, String recordId) {
        return lookup(PROPERTY_TYPE, code, recordId, "PROP_TYP_CD");
    }

    private static <T> T lookup(Map<String, T> table, String code, String recordId, String field) {
        if (LegacyValueParser.isBlank(code)) {
            throw new MigrationException(recordId, field, "code is null or blank");
        }
        T value = table.get(code.trim().toUpperCase());
        if (value == null) {
            throw new MigrationException(recordId, field, "unknown code '" + code.trim() + "'");
        }
        return value;
    }
}
