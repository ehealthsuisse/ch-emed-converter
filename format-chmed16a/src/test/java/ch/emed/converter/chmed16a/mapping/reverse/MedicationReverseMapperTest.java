package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.CARD;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.document;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.entries;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus;
import org.junit.jupiter.api.Test;

class MedicationReverseMapperTest {

  private final ChEmedDocument doc = document(CARD);
  private final ReverseScope scope = scope(doc);

  private MedicationStatement statement(int index) {
    return doc.all(MedicationStatement.class).get(index).copy();
  }

  private Medicament map(MedicationStatement statement) {
    return MedicationReverseMapper.map(statement, "MedicationStatement[0]", scope);
  }

  private static Medication contained(MedicationStatement statement) {
    return (Medication) statement.getContained().get(0);
  }

  @Test
  void mapsTheOfficialGtinCodedStatement() {
    Medicament m = map(statement(0));

    assertThat(m.id()).isEqualTo("7680521101306");
    assertThat(m.idType()).isEqualTo(Medicament.ID_TYPE_GTIN);
    assertThat(m.tkgRsn()).isEqualTo("Bluthochdruck");
    assertThat(m.appInstr()).isEqualTo("-");
    assertThat(m.roa()).isEqualTo("PO");
    assertThat(m.unit()).isEqualTo("STK");
    assertThat(m.pos())
        .singleElement()
        .satisfies(p -> assertThat(p.dtFrom()).isEqualTo("2012-02-04"));
    assertThat(m.autoMed()).isEqualTo(0);
    assertThat(scope.report().hasErrors()).isFalse();
  }

  @Test
  void productDetailsOfGtinCodedMedicationAreReportedAsInformation() {
    map(statement(0));

    assertThat(entries(scope, e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT)))
        .extracting(e -> e.path() + ":" + e.severity() + ":" + e.clinicalLoss())
        .contains(
            "MedicationStatement[0].contained.Medication.ingredient:INFORMATION:false",
            "MedicationStatement[0].extension.treatmentplan:INFORMATION:false");
  }

  @Test
  void freeTextProductWithIngredientsIsReportedWithClinicalLoss() {
    MedicationStatement st = statement(0);
    Medication med = contained(st);
    med.getCode().getCoding().clear();
    med.getCode().setText("Eigenes Praeparat");

    Medicament m = map(st);

    assertThat(m.id()).isEqualTo("Eigenes Praeparat");
    assertThat(m.idType()).isEqualTo(Medicament.ID_TYPE_NONE);
    assertThat(entries(scope, e -> e.path().endsWith("Medication.ingredient")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isTrue();
            });
  }

  @Test
  void pharmacodeAndProductNumberTextConventionsAreInverted() {
    MedicationStatement pharmacode = statement(0);
    contained(pharmacode).getCode().getCoding().clear();
    contained(pharmacode).getCode().setText("Pharmacode 971867");
    MedicationStatement product = statement(1);
    contained(product).getCode().getCoding().clear();
    contained(product).getCode().setText("Product number AB-12");

    Medicament a = map(pharmacode);
    Medicament b = map(product);

    assertThat(a.idType()).isEqualTo(Medicament.ID_TYPE_PHARMACODE);
    assertThat(a.id()).isEqualTo("971867");
    assertThat(b.idType()).isEqualTo(Medicament.ID_TYPE_PRODUCT_NUMBER);
    assertThat(b.id()).isEqualTo("AB-12");
  }

  @Test
  void otherCodingsAreReportedAndTheDisplayIsUsedWithoutText() {
    MedicationStatement st = statement(0);
    CodeableConcept code = contained(st).getCode();
    code.getCoding().clear();
    code.setText(null);
    code.addCoding(new Coding("http://www.whocc.no/atc", "C07AB02", "Metoprolol"));

    Medicament m = map(st);

    assertThat(m.idType()).isEqualTo(Medicament.ID_TYPE_NONE);
    assertThat(m.id()).isEqualTo("Metoprolol");
    assertThat(entries(scope, e -> e.path().endsWith("Medication.code.coding[0]")))
        .singleElement()
        .satisfies(e -> assertThat(e.severity()).isEqualTo(Severity.INFORMATION));
  }

  @Test
  void medicationWithoutAnyTextIsMissingRequiredData() {
    MedicationStatement st = statement(0);
    contained(st).getCode().getCoding().clear();
    contained(st).getCode().setText(null);

    assertThat(map(st)).isNull();
    assertThat(entries(scope, e -> e.severity() == Severity.ERROR))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA));
  }

  @Test
  void bundledMedicationReferenceIsResolved() {
    MedicationStatement st = statement(0);
    Medication med = contained(st).copy();
    med.setId("bundled-med");
    doc.bundle()
        .addEntry()
        .setFullUrl("http://test.fhir.ch/r4/Medication/bundled-med")
        .setResource(med);
    st.getContained().clear();
    st.getMedicationReference().setReference("Medication/bundled-med");

    assertThat(map(st).id()).isEqualTo("7680521101306");
  }

  @Test
  void selfMedicationAndSeveralReasonsAreMapped() {
    MedicationStatement st = statement(0);
    st.getCategory()
        .addCoding(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/medication-statement-category",
                "patientspecified",
                null));
    st.addReasonCode().setText("Herz");

    Medicament m = map(st);

    assertThat(m.autoMed()).isEqualTo(1);
    assertThat(m.tkgRsn()).isEqualTo("Bluthochdruck; Herz");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DERIVED_VALUE))).isNotEmpty();
  }

  @Test
  void differingPatientInstructionsAreJoined() {
    MedicationStatement st = statement(0);
    st.getDosage().get(1).setPatientInstruction("Mit Wasser");

    assertThat(map(st).appInstr()).isEqualTo("-; Mit Wasser");
  }

  @Test
  void activeIntendedAndCompletedStatementsAreIncludedWithoutLossEntries() {
    for (MedicationStatementStatus status :
        new MedicationStatementStatus[] {
          MedicationStatementStatus.ACTIVE,
          MedicationStatementStatus.INTENDED,
          MedicationStatementStatus.COMPLETED
        }) {
      MedicationStatement st = statement(0);
      st.setStatus(status);

      assertThat(map(st)).as(status.toCode()).isNotNull();
    }
    assertThat(entries(scope, e -> e.clinicalLoss())).isEmpty();
  }

  @Test
  void onHoldAndUnknownAreIncludedWithClinicalLossWarning() {
    for (MedicationStatementStatus status :
        new MedicationStatementStatus[] {
          MedicationStatementStatus.ONHOLD, MedicationStatementStatus.UNKNOWN
        }) {
      MedicationStatement st = statement(0);
      st.setStatus(status);

      assertThat(map(st)).as(status.toCode()).isNotNull();
    }
    assertThat(entries(scope, e -> e.clinicalLoss() && e.severity() == Severity.WARNING))
        .hasSize(2);
  }

  @Test
  void stoppedAndNotTakenAreExcludedAndReportedWithClinicalLoss() {
    for (MedicationStatementStatus status :
        new MedicationStatementStatus[] {
          MedicationStatementStatus.STOPPED, MedicationStatementStatus.NOTTAKEN
        }) {
      MedicationStatement st = statement(0);
      st.setStatus(status);

      assertThat(map(st)).as(status.toCode()).isNull();
    }
    assertThat(
            entries(scope, e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT) && e.clinicalLoss()))
        .hasSize(2)
        .allSatisfy(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.path()).isEqualTo("MedicationStatement[0].status");
            });
    assertThat(scope.report().hasErrors()).isFalse();
  }

  @Test
  void enteredInErrorIsExcludedWithInformationOnly() {
    MedicationStatement st = statement(0);
    st.setStatus(MedicationStatementStatus.ENTEREDINERROR);

    assertThat(map(st)).isNull();
    assertThat(entries(scope, e -> e.path().equals("MedicationStatement[0].status")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
              assertThat(e.clinicalLoss()).isFalse();
            });
  }
}
