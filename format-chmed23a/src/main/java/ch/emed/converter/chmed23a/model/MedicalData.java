package ch.emed.converter.chmed23a.model;

import java.math.BigDecimal;
import java.util.List;

/** Medical data of a medication plan (README "MedicalData"). */
public record MedicalData(
    String dLstMen,
    Boolean prem,
    String toG,
    List<RiskCategory> rcs,
    BigDecimal w,
    BigDecimal h,
    List<Extension> exts) {

  public MedicalData {
    rcs = rcs == null ? List.of() : List.copyOf(rcs);
    exts = exts == null ? List.of() : List.copyOf(exts);
  }
}
