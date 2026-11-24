package com.workshop.loanservice.migration;

import com.workshop.loanservice.modern.entity.BorrowerStatus;
import com.workshop.loanservice.modern.entity.LoanStatus;
import com.workshop.loanservice.modern.entity.PaymentStatus;
import com.workshop.loanservice.modern.entity.PaymentType;

import java.util.Map;

/**
 * Expansion of legacy short codes into modern values
 * (see data/mappings/column_mappings.md). Status and type codes are required;
 * a missing or unknown code rejects the record.
 */
public final class LegacyCodeMappings {

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

    private LegacyCodeMappings() {
    }

    public static BorrowerStatus borrowerStatus(String code) {
        return required(BORROWER_STATUS, "BORR_STAT_CD", code);
    }

    public static Boolean productActive(String code) {
        return required(PRODUCT_ACTIVE, "PROD_STAT_CD", code);
    }

    public static LoanStatus loanStatus(String code) {
        return required(LOAN_STATUS, "LN_STAT_CD", code);
    }

    /** Property type is optional; unknown codes are rejected. */
    public static String propertyType(String code) {
        String value = LegacyValueParser.clean(code);
        return value == null ? null : required(PROPERTY_TYPE, "PROP_TYP_CD", value);
    }

    public static PaymentType paymentType(String code) {
        return required(PAYMENT_TYPE, "PMT_TYP_CD", code);
    }

    public static PaymentStatus paymentStatus(String code) {
        return required(PAYMENT_STATUS, "PMT_STAT_CD", code);
    }

    private static <T> T required(Map<String, T> mapping, String column, String code) {
        String value = LegacyValueParser.clean(code);
        if (value == null) {
            throw new MalformedLegacyDataException(column, code, "missing code");
        }
        T mapped = mapping.get(value);
        if (mapped == null) {
            throw new MalformedLegacyDataException(column, code, "unknown code");
        }
        return mapped;
    }
}
