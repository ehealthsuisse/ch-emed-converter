package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.math.BigDecimal;
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
 * CHMED16A prescription details → CH EMED MedicationRequest (mapping spec M-RX-05..M-RX-09): number
 * of packages, repetition, substitution rule, indication code and intended receiver.
 */
public final class PrescriptionDetailsMapper {

  /** SNOMED "Package - unit of product usage", as in the official CH EMED examples. */
  public static final String PACKAGE_CODE = "1681000175101";

  static final String PACKAGE_DISPLAY = "Package - unit of product usage (qualifier value)";

  /** Prefix of the text that carries an indication code (no code system in the pinned packages). */
  public static final String INDICATION_PREFIX = "SL indication code ";

  private PrescriptionDetailsMapper() {}

  /**
   * Applies the prescription-specific elements.
   *
   * @param request the request to fill
   * @param m the medicament
   * @param med the prescription (date, receiver)
   * @param medPath path of the medicament, e.g. {@code /Medicaments/0}
   * @param scope mapping scope
   */
  @MappingRule({"M-RX-05", "M-RX-06", "M-RX-07", "M-RX-08", "M-RX-09"})
  public static void apply(
      MedicationRequest request, Medicament m, Medication med, String medPath, MappingScope scope) {
    if (m.nbPack() != null) {
      request
          .getDispenseRequest()
          .setQuantity(
              new Quantity()
                  .setValue(BigDecimal.valueOf(m.nbPack()))
                  .setUnit(PACKAGE_DISPLAY)
                  .setSystem(Profiles.SNOMED)
                  .setCode(PACKAGE_CODE));
    }
    if (m.rep() != null && m.rep() > 0) {
      validity(request, m.rep(), med.dt());
    }
    if (med.patient() != null && present(med.patient().rcv())) {
      request
          .getDispenseRequest()
          .setPerformer(
              new Reference()
                  .setIdentifier(
                      new Identifier().setSystem(Profiles.GLN).setValue(med.patient().rcv())));
    }
    if (m.subs() != null) {
      boolean none = m.subs() == 1;
      request
          .getSubstitution()
          .setAllowed(
              new CodeableConcept(
                  new Coding(
                      Profiles.SUBSTITUTION, none ? "N" : "E", none ? "none" : "equivalent")));
    }
    if (present(m.indC())) {
      request.addReasonCode().setText(INDICATION_PREFIX + m.indC());
      scope
          .report()
          .add(
              ReportEntry.warning(
                      ReasonCode.UNMAPPABLE_ELEMENT,
                      Phase.MAP,
                      medPath + "/IndC",
                      "The pinned packages have no code system for the SL indication code; it is"
                          + " carried as text only")
                  .withTargetPath("MedicationRequest.reasonCode.text"));
    }
  }

  /** Validity from the prescription date for {@code rep} months, end inclusive. */
  private static void validity(MedicationRequest request, int rep, String dt) {
    if (dt == null || dt.length() < 10) {
      return;
    }
    try {
      LocalDate start = LocalDate.parse(dt.substring(0, 10));
      LocalDate end = start.plusMonths(rep).minusDays(1);
      request
          .getDispenseRequest()
          .getValidityPeriod()
          .setStartElement(new DateTimeType(start.toString()))
          .setEndElement(new DateTimeType(end.toString()));
    } catch (DateTimeParseException e) {
      // the validator has reported the invalid date
    }
  }
}
