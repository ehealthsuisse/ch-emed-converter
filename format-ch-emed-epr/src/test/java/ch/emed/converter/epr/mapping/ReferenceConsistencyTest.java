package ch.emed.converter.epr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.util.List;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T015 (feature 005, research R5, data-model §3, rule M-EPR-04): contradicting references within
 * one document are reported as WARNING {@code REFERENCE_INCONSISTENT} with paths only; the document
 * does not change.
 */
class ReferenceConsistencyTest {

  private static final String OTHER = "urn:uuid:99999999-9999-4999-8999-999999999999";
  private static final String OTHER2 = "urn:uuid:88888888-8888-4888-8888-888888888888";

  private static Bundle load(String fixture) {
    return ChEmedJson.parse(EprFixtures.bytes(fixture));
  }

  private static <T extends Resource> T first(Bundle bundle, Class<T> type) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow();
  }

  private static void setPart(Extension ext, String part, String value) {
    ((Identifier) ext.getExtensionByUrl(part).getValue()).setValue(value);
  }

  private static ReportCollector check(Bundle bundle, EprDocumentType type) {
    ReportCollector report = new ReportCollector();
    new ReferenceConsistency().check(bundle, type, report);
    return report;
  }

  /** Runs the check and asserts one warning without identifiers and an unchanged document. */
  private static ReportEntry single(Bundle bundle, EprDocumentType type) {
    String before = new String(ChEmedJson.serialize(bundle));

    ReportCollector report = check(bundle, type);

    assertThat(new String(ChEmedJson.serialize(bundle))).isEqualTo(before);
    assertThat(report.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.REFERENCE_INCONSISTENT);
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isFalse();
              assertThat(e.message()).doesNotContain("urn:uuid");
              assertThat(e.path()).startsWith("Bundle.entry[");
            });
    return report.entries().get(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PMLC", "PRE", "MTP", "DIS", "PADV", "PML"})
  void officialEprExamplesHaveNoContradiction(String type) {
    for (String name : EprFixtures.official(type)) {
      assertThat(check(load("official/" + name), EprDocumentType.valueOf(type)).entries())
          .as(name)
          .isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"DIS", "PADV", "PML"})
  void officialChEmedExamplesHaveNoContradiction(String type) {
    for (String name : EprFixtures.chEmed(type)) {
      assertThat(check(load("chemed/" + name), EprDocumentType.valueOf(type)).entries())
          .as(name)
          .isEmpty();
    }
  }

  @Test
  void c1TheSameEntryInTwoDocuments() {
    Bundle dis = load("official/Bundle-DocumentDis1.json");
    MedicationDispense dispense = first(dis, MedicationDispense.class);
    Extension plan = dispense.getExtensionByUrl(ReferenceConsistency.TREATMENT_PLAN);
    Extension prescription = dispense.getExtensionByUrl(ReferenceConsistency.PRESCRIPTION);
    String planId = ((Identifier) plan.getExtensionByUrl("id").getValue()).getValue();
    setPart(prescription, "id", planId);
    setPart(prescription, "externalDocumentId", OTHER);

    ReportEntry entry = single(dis, EprDocumentType.DIS);

    assertThat(entry.message()).contains("(C1)").contains(".extension[");
  }

  @Test
  void c3TheChangedStatementBelongsToAnotherTreatmentPlan() {
    Bundle padv = load("official/Bundle-DocumentPadv1.json");
    Extension plan =
        first(padv, Observation.class).getExtensionByUrl(ReferenceConsistency.TREATMENT_PLAN);
    setPart(plan, "id", OTHER);
    setPart(plan, "externalDocumentId", OTHER2);

    ReportEntry entry = single(padv, EprDocumentType.PADV);

    assertThat(entry.message()).contains("(C3)").contains("MedicationStatement");
  }

  @Test
  void c4ADispenseAndItsPrescriptionNameDifferentTreatmentPlans() {
    Bundle pml = load("official/Bundle-BundlePml1.json");
    MedicationDispense dispense =
        first(load("official/Bundle-DocumentDis1.json"), MedicationDispense.class);
    MedicationRequest request =
        first(load("official/Bundle-DocumentPadv2.json"), MedicationRequest.class);
    Function<Extension, String> id =
        e -> ((Identifier) e.getExtensionByUrl("id").getValue()).getValue();
    String prescriptionId = id.apply(dispense.getExtensionByUrl(ReferenceConsistency.PRESCRIPTION));
    request.getIdentifierFirstRep().setValue(prescriptionId);
    Extension requestPlan = request.getExtensionByUrl(ReferenceConsistency.TREATMENT_PLAN);
    setPart(requestPlan, "id", OTHER);
    setPart(requestPlan, "externalDocumentId", OTHER2);
    pml.addEntry()
        .setFullUrl("urn:uuid:aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1")
        .setResource(dispense);
    pml.addEntry().setFullUrl("urn:uuid:aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2").setResource(request);

    ReportEntry entry = single(pml, EprDocumentType.PML);

    assertThat(entry.message()).contains("(C4)");
  }

  @Test
  void consistentReferencesGiveNoEntry() {
    for (String name :
        List.of("official/Bundle-DocumentDis1.json", "official/Bundle-DocumentPadv2.json")) {
      Bundle bundle = load(name);
      assertThat(
              check(bundle, name.contains("Dis") ? EprDocumentType.DIS : EprDocumentType.PADV)
                  .entries())
          .isEmpty();
    }
  }
}
