package ch.emed.converter.chmed23a.mapping;

import static ch.emed.converter.chmed23a.mapping.MappingScope.present;

import ch.emed.converter.chmed23a.model.Medicament;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.Repetition;
import ch.emed.converter.chmed23a.model.TimeUnit;
import ch.emed.converter.core.chemed.Profiles;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;

/**
 * CHMED23A prescription details → CH EMED MedicationRequest (mapping spec M23-RX-03..09, research
 * R10): repetitions, number of packages, substitution, sic and recipient.
 */
public final class PrescriptionDetailsMapper {

  /** SNOMED "Package - unit of product usage", as in the official CH EMED examples. */
  public static final String PACKAGE_CODE = "1681000175101";

  static final String PACKAGE_DISPLAY = "Package - unit of product usage (qualifier value)";

  private PrescriptionDetailsMapper() {}

  /**
   * Applies the prescription-specific elements.
   *
   * @param request the request to fill
   * @param m the medicament
   * @param med the prescription (date, recipient)
   * @param medPath path of the medicament, e.g. {@code /meds/0}
   * @param scope mapping scope
   */
  @MappingRule({
    "M23-RX-03",
    "M23-RX-04",
    "M23-RX-05",
    "M23-RX-06",
    "M23-RX-07",
    "M23-RX-08",
    "M23-RX-09"
  })
  public static void apply(
      MedicationRequest request, Medicament m, Medication med, String medPath, MappingScope scope) {
    if (m.nbPack() != null) {
      request
          .getDispenseRequest()
          .setQuantity(
              new Quantity()
                  .setValue(m.nbPack())
                  .setUnit(PACKAGE_DISPLAY)
                  .setSystem(Profiles.SNOMED)
                  .setCode(PACKAGE_CODE));
    }
    switch (m.reps()) {
      case null -> {}
      case Repetition.Number n -> request.getDispenseRequest().setNumberOfRepeatsAllowed(n.v());
      case Repetition.Duration d -> validity(request, d.d(), d.u(), med.dt(), medPath, scope);
      case Repetition.NumberAndDuration nd -> {
        request.getDispenseRequest().setNumberOfRepeatsAllowed(nd.v());
        validity(request, nd.d(), nd.u(), med.dt(), medPath, scope);
      }
    }
    if (m.isNotSub() != null) {
      boolean none = m.isNotSub();
      request
          .getSubstitution()
          .setAllowed(
              new CodeableConcept(
                  new Coding(
                      Profiles.SUBSTITUTION, none ? "N" : "E", none ? "none" : "equivalent")));
    }
    if (Boolean.TRUE.equals(m.sic())) {
      scope
          .report()
          .add(
              ReportEntry.warning(
                      ReasonCode.UNMAPPABLE_ELEMENT,
                      Phase.MAP,
                      medPath + "/sic",
                      "Sic erat scriptum has no representation in the CH EMED Medication"
                          + " Prescription")
                  .withClinicalLoss(true));
    }
    if (present(med.rec())) {
      request
          .getDispenseRequest()
          .setPerformer(
              new Reference()
                  .setIdentifier(new Identifier().setSystem(Profiles.GLN).setValue(med.rec())));
    }
  }

  /**
   * Validity from the date of the prescription for {@code d} units, end inclusive (M23-RX-04).
   * Durations under one day cannot be expressed as a period of dates.
   */
  private static void validity(
      MedicationRequest request,
      Integer d,
      Integer u,
      String dt,
      String medPath,
      MappingScope scope) {
    TimeUnit unit = TimeUnit.of(u);
    if (unit == TimeUnit.SECOND || unit == TimeUnit.MINUTE || unit == TimeUnit.HOUR) {
      scope
          .report()
          .add(
              ReportEntry.warning(
                      ReasonCode.UNMAPPABLE_ELEMENT,
                      Phase.MAP,
                      medPath + "/reps",
                      "A validity shorter than one day cannot be represented as validity period")
                  .withTargetPath("MedicationRequest.dispenseRequest.validityPeriod")
                  .withClinicalLoss(true));
      return;
    }
    if (dt == null || dt.length() < 10 || d == null || unit == null) {
      return;
    }
    try {
      LocalDate start = LocalDate.parse(dt.substring(0, 10));
      LocalDate end =
          switch (unit) {
            case DAY -> start.plusDays(d);
            case WEEK -> start.plusWeeks(d);
            case MONTH -> start.plusMonths(d);
            default -> start.plusYears(d);
          };
      request
          .getDispenseRequest()
          .getValidityPeriod()
          .setStartElement(new DateTimeType(start.toString()))
          .setEndElement(new DateTimeType(end.minusDays(1).toString()));
    } catch (DateTimeParseException e) {
      // the validator has reported the invalid date
    }
  }
}
