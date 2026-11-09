package ch.emed.converter.epr;

/**
 * Reason codes of the CH EMED EPR module in addition to {@link
 * ch.emed.converter.core.report.ReasonCode} (data-model.md §3), documented in {@code
 * docs/mapping/ch-emed-epr.md}.
 */
public final class EprReasonCode {

  /**
   * The declared profiles of a resource were replaced by their counterparts in the other IG
   * (INFORMATION, mapping rule M-EPR-01).
   */
  public static final String PROFILE_CHANGED = "PROFILE_CHANGED";

  private EprReasonCode() {}
}
