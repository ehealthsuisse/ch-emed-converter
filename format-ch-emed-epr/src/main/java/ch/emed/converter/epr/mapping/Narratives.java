package ch.emed.converter.epr.mapping;

import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Resource;

/** Narratives the module generates (CH EMED EPR requires section text). */
final class Narratives {

  private Narratives() {}

  /**
   * Text of a treatment-plan section: the medication and its dosage text.
   *
   * @param statement the treatment
   * @return generated narrative
   */
  static Narrative treatment(MedicationStatement statement) {
    StringBuilder div = new StringBuilder("<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>");
    div.append(escape(medication(statement))).append("</p>");
    for (Dosage dosage : statement.getDosage()) {
      String text = dosage.hasText() ? dosage.getText() : dosage.getPatientInstruction();
      if (text != null && !text.isBlank()) {
        div.append("<p>").append(escape(text)).append("</p>");
      }
    }
    div.append("</div>");
    Narrative narrative = new Narrative();
    narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
    narrative.setDivAsString(div.toString());
    return narrative;
  }

  private static String medication(MedicationStatement statement) {
    if (statement.hasMedicationCodeableConcept()) {
      return statement.getMedicationCodeableConcept().getText();
    }
    String ref =
        statement.hasMedicationReference()
            ? statement.getMedicationReference().getReference()
            : null;
    for (Resource r : statement.getContained()) {
      if (r instanceof Medication m
          && (ref == null || ref.equals("#" + m.getIdPart()) || ref.equals(m.getId()))) {
        if (m.getCode().hasText()) {
          return m.getCode().getText();
        }
        if (m.getCode().hasCoding() && m.getCode().getCodingFirstRep().hasDisplay()) {
          return m.getCode().getCodingFirstRep().getDisplay();
        }
      }
    }
    return "Medication";
  }

  private static String escape(String s) {
    return s == null
        ? ""
        : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
