package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.CARD;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.document;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.entries;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.junit.jupiter.api.Test;

class MedicationPlanBuilderTest {

  private final ChEmedDocument doc = document(CARD);
  private final ReverseScope scope = scope(doc);

  private Medication build() {
    return MedicationPlanBuilder.build(scope);
  }

  private void setAnnotationText(String xhtml) throws Exception {
    var section =
        doc.composition().getSection().stream()
            .filter(s -> s.getCode().getCodingFirstRep().getCode().equals("48767-8"))
            .findFirst()
            .orElseThrow();
    Narrative text = new Narrative();
    text.setStatus(Narrative.NarrativeStatus.GENERATED);
    text.setDiv(new XhtmlParser().parseFragment(xhtml));
    section.setText(text);
  }

  @Test
  void buildsTheMedicationPlanOfTheOfficialExample() {
    Medication med = build();

    assertThat(scope.report().hasErrors()).isFalse();
    assertThat(med.medType()).isEqualTo(Medication.MED_TYPE_PLAN);
    assertThat(med.id()).isEqualTo("b4bbd9ed-bada-41b5-a513-b4bc73ca0ebb");
    assertThat(med.dt()).isEqualTo("2012-02-04T14:05:00+01:00");
    assertThat(med.auth()).isEqualTo("7601000234438");
    assertThat(med.rmk()).isEqualTo("Kommentar zu Medication Treatment");
    assertThat(med.patient().lName()).isEqualTo("Wegmüller");
    assertThat(med.medicaments()).hasSize(2);
    assertThat(med.medicaments())
        .extracting(m -> m.id())
        .containsExactly("7680521101306", "7680500440334");
    assertThat(med.rev()).isNull();
  }

  @Test
  void autoMedIsSetToZeroWithoutSelfMedicationCategoryAndReportedOnce() {
    Medication med = build();

    assertThat(med.medicaments()).allSatisfy(m -> assertThat(m.autoMed()).isEqualTo(0));
    assertThat(entries(scope, e -> e.path().equals("MedicationStatement.category")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE);
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
              assertThat(e.clinicalLoss()).isFalse();
            });
  }

  @Test
  void validationInformationAndMedicalDataAreNeverProduced() {
    Medication med = build();

    assertThat(med.valBy()).isNull();
    assertThat(med.valDt()).isNull();
    assertThat(med.patient().med()).isNull();
    assertThat(med.hasRecommendations()).isFalse();
    assertThat(med.hcPerson()).isNull();
    assertThat(med.hcOrg()).isNull();
  }

  @Test
  void idFallsBackToTheCompositionIdentifierWhenTheBundleIdentifierIsNoUuid() {
    doc.bundle()
        .setIdentifier(new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:oid:1.2.3"));

    assertThat(build().id()).isEqualTo("b4bbd9ed-bada-41b5-a513-b4bc73ca0ebb");
  }

  @Test
  void missingIdentifiersAreMissingRequiredData() {
    doc.bundle().setIdentifier(null);
    doc.composition().setIdentifier(null);

    build();

    assertThat(entries(scope, e -> e.severity() == Severity.ERROR))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA);
              assertThat(e.path()).isEqualTo("Bundle.identifier");
            });
  }

  @Test
  void noAnnotationSectionMeansNoRmk() {
    doc.composition()
        .getSection()
        .removeIf(s -> s.getCode().getCodingFirstRep().getCode().equals("48767-8"));

    assertThat(build().rmk()).isNull();
  }

  @Test
  void annotationMarkupIsRemovedAndReported() throws Exception {
    setAnnotationText(
        "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>Erste Zeile</p><p>Zweite Zeile</p></div>");

    assertThat(build().rmk()).isEqualTo("Erste Zeile Zweite Zeile");
    assertThat(
            entries(scope, e -> e.path().equals("Composition.section.where(code='48767-8').text")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE);
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
            });
  }

  @Test
  void plainAnnotationTextIsNotReported() throws Exception {
    setAnnotationText("<div xmlns=\"http://www.w3.org/1999/xhtml\">Nur Text</div>");

    assertThat(build().rmk()).isEqualTo("Nur Text");
    assertThat(entries(scope, e -> e.path().startsWith("Composition.section"))).isEmpty();
  }

  @Test
  void inactiveMedicationsAreExcludedAndReportedNeverDroppedSilently() {
    ChEmedDocument inactive = document("chemed/card-inactive-medication.json");
    ReverseScope inactiveScope = scope(inactive);

    Medication med = MedicationPlanBuilder.build(inactiveScope);

    assertThat(inactive.all(MedicationStatement.class)).hasSize(6);
    assertThat(med.medicaments()).hasSize(3);
    long reportedExclusions =
        entries(
                inactiveScope,
                e ->
                    e.path().matches("MedicationStatement\\[\\d+]\\.status")
                        && e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT))
            .size();
    // stopped, not-taken, entered-in-error excluded; on-hold included with a warning
    assertThat(reportedExclusions).isEqualTo(4);
    assertThat(inactive.all(MedicationStatement.class).stream().map(MedicationStatement::getStatus))
        .contains(
            MedicationStatementStatus.STOPPED,
            MedicationStatementStatus.ONHOLD,
            MedicationStatementStatus.NOTTAKEN,
            MedicationStatementStatus.ENTEREDINERROR);
  }

  @Test
  void aDocumentWithoutStatementsGivesAnEmptyMedicamentList() {
    doc.bundle().getEntry().removeIf(e -> e.getResource() instanceof MedicationStatement);

    Medication med = build();

    assertThat(med.medicaments()).isEmpty();
    assertThat(scope.report().hasErrors()).isFalse();
  }

  @Test
  void originalRepresentationAndConfidentialityAreReportedByTheReaderNotTheBuilder() {
    build();

    assertThat(entries(scope, e -> e.path().startsWith("Composition."))).isEmpty();
  }
}
