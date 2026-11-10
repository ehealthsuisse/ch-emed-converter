package ch.emed.converter.chmed16a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.DocumentType;
import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class ChEmedDocumentReaderTest {

  private final ReportCollector report = new ReportCollector();

  private ChEmedDocument read(byte[] json) {
    return ChEmedDocumentReader.read(json, ChEmedAssertions.validator(), report);
  }

  private List<ReportEntry> errors() {
    return report.entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  @Test
  void nonJsonIsParseError() {
    assertThat(read("this is not json".getBytes(StandardCharsets.UTF_8))).isNull();
    assertThat(errors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR);
              assertThat(e.phase()).isEqualTo(Phase.PARSE);
            });
  }

  @Test
  void nonBundleIsParseError() {
    assertThat(read("{\"resourceType\":\"Patient\"}".getBytes(StandardCharsets.UTF_8))).isNull();
    assertThat(errors())
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR));
  }

  @Test
  void profileViolationsAreFhirValidationErrorsOfPhaseValidateInput() {
    assertThat(read(Fixtures.bytes("chemed/card-invalid-profile.json"))).isNull();
    assertThat(errors()).isNotEmpty();
    assertThat(errors())
        .allSatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.FHIR_VALIDATION);
              assertThat(e.phase()).isEqualTo(Phase.VALIDATE_INPUT);
            });
  }

  @Test
  void unsupportedDocumentTypeNamesTheTypeCode() {
    assertThat(read(Fixtures.bytes("chemed/card-unsupported-type.json"))).isNull();
    assertThat(errors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_DOCUMENT_TYPE);
              assertThat(e.message()).contains("77603-9");
            });
  }

  @Test
  void officialMedicationCardIsAccepted() {
    ChEmedDocument doc = read(Fixtures.bytes("chemed/medication-card-example.json"));

    assertThat(errors()).isEmpty();
    assertThat(doc.type()).isEqualTo(DocumentType.MEDICATION_CARD);
    assertThat(doc.composition().getTitle()).isEqualTo("Medikationsplan");
  }

  /**
   * Regression (feature 004): for the Medication Card, CH EMED fixes only the SNOMED CT code of the
   * document type; the LOINC code is optional (the prescription requires LOINC 57833-6). The
   * official PMLC examples of CH EMED EPR carry SNOMED only.
   */
  @Test
  void medicationCardIsRecognisedBySnomedAlone() {
    for (String[] c : new String[][] {{"chemed/medication-card-example.json", "MEDICATION_CARD"}}) {
      org.hl7.fhir.r4.model.Bundle bundle =
          ch.emed.converter.core.fhir.ChEmedJson.parse(Fixtures.bytes(c[0]));
      ((org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource())
          .getType()
          .getCoding()
          .removeIf(coding -> "http://loinc.org".equals(coding.getSystem()));

      ChEmedDocument doc = read(ch.emed.converter.core.fhir.ChEmedJson.serialize(bundle));

      assertThat(errors()).as(c[0]).isEmpty();
      assertThat(doc.type()).isEqualTo(DocumentType.valueOf(c[1]));
    }
  }

  @Test
  void officialMedicationPrescriptionIsDetectedAsPrescription() {
    ChEmedDocument doc = read(Fixtures.bytes("chemed/medication-prescription-example.json"));

    assertThat(errors()).isEmpty();
    assertThat(doc.type()).isEqualTo(DocumentType.MEDICATION_PRESCRIPTION);
  }

  @Test
  void eprOnlyElementsAreAcceptedAndReported() {
    ChEmedDocument doc = read(Fixtures.bytes("chemed/card-epr-elements.json"));

    assertThat(errors()).isEmpty();
    assertThat(doc).isNotNull();
    assertThat(report.entries())
        .anySatisfy(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.path()).isEqualTo("Composition.confidentiality");
            })
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.path()).isEqualTo("Composition.section.where(code='55108-5')");
            });
  }

  @Test
  void resolvesRelativeAndUuidReferences() {
    for (String fixture :
        List.of(
            "chemed/medication-card-example.json",
            "chemed/medication-card-example-uuid-fullurl.json")) {
      ChEmedDocument doc = read(Fixtures.bytes(fixture));

      Patient patient = doc.resolve(doc.composition().getSubject(), Patient.class);

      assertThat(patient).as(fixture).isNotNull();
      assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Wegmüller");
    }
  }

  @Test
  void unresolvableReferenceYieldsNull() {
    ChEmedDocument doc = read(Fixtures.bytes("chemed/medication-card-example.json"));

    assertThat(doc.resolve(new org.hl7.fhir.r4.model.Reference("Patient/unknown"), Patient.class))
        .isNull();
  }
}
