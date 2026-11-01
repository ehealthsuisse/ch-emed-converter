package ch.emed.converter.chmed16a.model;

import java.util.List;

/** CHMED16A MedicalData (medication plans only). */
public record MedicalData(
    String dLstMen,
    Integer prem,
    String toG,
    List<RiskCategory> rc,
    List<Measurement> meas,
    List<PField> pFields) {

  public MedicalData {
    rc = rc == null ? List.of() : List.copyOf(rc);
    meas = meas == null ? List.of() : List.copyOf(meas);
    pFields = pFields == null ? List.of() : List.copyOf(pFields);
  }
}
