package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.model.MedicalData;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;

/**
 * CHMED16A medical data has no representation in the CH EMED Medication Card; every element is
 * reported with clinical loss (mapping spec M-MDT-01, capability gap).
 */
public final class MedicalDataMapper {

  private MedicalDataMapper() {}

  @MappingRule("M-MDT-01")
  public static void map(MedicalData md, MappingScope scope) {
    if (md == null) {
      return;
    }
    if (present(md.dLstMen())) {
      report(scope, "/Patient/Med/DLstMen", "last menstruation");
    }
    if (md.prem() != null) {
      report(scope, "/Patient/Med/Prem", "premature birth");
    }
    if (present(md.toG())) {
      report(scope, "/Patient/Med/ToG", "time of gestation");
    }
    for (int i = 0; i < md.meas().size(); i++) {
      report(scope, "/Patient/Med/Meas/" + i, "measurement");
    }
    for (int i = 0; i < md.rc().size(); i++) {
      report(scope, "/Patient/Med/Rc/" + i, "risk category");
    }
  }

  private static void report(MappingScope scope, String path, String kind) {
    scope
        .report()
        .add(
            ReportEntry.warning(
                    ReasonCode.UNMAPPABLE_ELEMENT,
                    Phase.MAP,
                    path,
                    "Medical data ("
                        + kind
                        + ") has no representation in the CH EMED Medication"
                        + " Card")
                .withClinicalLoss(true));
  }
}
