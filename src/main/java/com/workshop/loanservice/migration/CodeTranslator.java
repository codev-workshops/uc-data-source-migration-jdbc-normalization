package com.workshop.loanservice.migration;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Translates legacy CDW codes to modern canonical values, and back to the human-readable strings
 * the frozen v1 API contract exposes.
 *
 * <p>The stored modern value is a canonical code (for example {@code SINGLE_FAMILY}), not a display
 * label. Display text belongs to the presentation layer: storing "Single Family Residence" in the
 * database is how the v1 wording became impossible to change. See
 * {@code docs/MIGRATION_NOTES.md} for the deviation this introduces from
 * {@code data/mappings/column_mappings.md}, which suggests storing the label.
 */
@Component
public class CodeTranslator {

    private static final Map<String, String> BORROWER_STATUS = Map.of(
        "ACT", "ACTIVE",
        "INA", "INACTIVE");

    private static final Map<String, String> LOAN_STATUS = Map.of(
        "ACT", "ACTIVE",
        "CLO", "CLOSED",
        "DFT", "DEFAULT",
        "FRB", "FORBEARANCE");

    private static final Map<String, String> PROPERTY_TYPE = Map.of(
        "SFR", "SINGLE_FAMILY",
        "CND", "CONDOMINIUM",
        "MFR", "MULTI_FAMILY",
        "TWN", "TOWNHOUSE");

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

    /** Modern canonical value to the exact label v1 has always returned. */
    private static final Map<String, String> LOAN_STATUS_LABEL = Map.of(
        "ACTIVE", "Active",
        "CLOSED", "Closed",
        "DEFAULT", "Default",
        "FORBEARANCE", "Forbearance");

    private static final Map<String, String> PROPERTY_TYPE_LABEL = Map.of(
        "SINGLE_FAMILY", "Single Family Residence",
        "CONDOMINIUM", "Condominium",
        "MULTI_FAMILY", "Multi-Family Residence",
        "TOWNHOUSE", "Townhouse");

    private static final Map<String, String> PAYMENT_TYPE_LABEL = Map.of(
        "REGULAR", "Regular",
        "EXTRA", "Extra",
        "PARTIAL", "Partial",
        "PREPAYMENT", "Prepayment");

    private static final Map<String, String> PAYMENT_STATUS_LABEL = Map.of(
        "POSTED", "Posted",
        "REVERSED", "Reversed",
        "NSF", "Non-Sufficient Funds",
        "PENDING", "Pending");

    public String borrowerStatus(String legacyCode) {
        return translate(BORROWER_STATUS, legacyCode);
    }

    public boolean productActive(String legacyStatusCode) {
        return "ACT".equals(legacyStatusCode);
    }

    public String loanStatus(String legacyCode) {
        return translate(LOAN_STATUS, legacyCode);
    }

    public String propertyType(String legacyCode) {
        return translate(PROPERTY_TYPE, legacyCode);
    }

    public String paymentType(String legacyCode) {
        return translate(PAYMENT_TYPE, legacyCode);
    }

    public String paymentStatus(String legacyCode) {
        return translate(PAYMENT_STATUS, legacyCode);
    }

    public String loanStatusLabel(String modernValue) {
        return label(LOAN_STATUS_LABEL, modernValue);
    }

    public String propertyTypeLabel(String modernValue) {
        return label(PROPERTY_TYPE_LABEL, modernValue);
    }

    public String paymentTypeLabel(String modernValue) {
        return label(PAYMENT_TYPE_LABEL, modernValue);
    }

    public String paymentStatusLabel(String modernValue) {
        return label(PAYMENT_STATUS_LABEL, modernValue);
    }

    /**
     * Unknown codes pass through unchanged, matching the legacy service's {@code default -> code}
     * behaviour. A null becomes "UNKNOWN" on the storage side; the label side turns it back into the
     * v1 wording "Unknown".
     */
    private static String translate(Map<String, String> mapping, String legacyCode) {
        if (legacyCode == null) {
            return "UNKNOWN";
        }
        return mapping.getOrDefault(legacyCode, legacyCode);
    }

    private static String label(Map<String, String> mapping, String modernValue) {
        if (modernValue == null || "UNKNOWN".equals(modernValue)) {
            return "Unknown";
        }
        return mapping.getOrDefault(modernValue, modernValue);
    }
}
