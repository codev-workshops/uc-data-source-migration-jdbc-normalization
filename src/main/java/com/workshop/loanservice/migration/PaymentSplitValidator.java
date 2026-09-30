package com.workshop.loanservice.migration;

import com.workshop.loanservice.modern.entity.QuarantineReason;
import java.math.BigDecimal;

/**
 * Application-side mirror of {@code ck_payment_split}: a payment total must equal principal +
 * interest + escrow (the late fee is billed separately). Checking before insert lets the loader
 * quarantine the row with a precise reason instead of relying on a generic constraint error.
 */
public final class PaymentSplitValidator {

  static final String FIELD = "PMT_AMT";

  private PaymentSplitValidator() {}

  public static void validate(
      BigDecimal total, BigDecimal principal, BigDecimal interest, BigDecimal escrow) {
    BigDecimal componentSum = principal.add(interest).add(escrow);
    if (total.compareTo(componentSum) != 0) {
      throw new RowRejectedException(
          QuarantineReason.PAYMENT_SPLIT_MISMATCH,
          FIELD,
          String.format(
              "total %s != principal %s + interest %s + escrow %s = %s (difference %s)",
              total, principal, interest, escrow, componentSum, total.subtract(componentSum)));
    }
  }
}
