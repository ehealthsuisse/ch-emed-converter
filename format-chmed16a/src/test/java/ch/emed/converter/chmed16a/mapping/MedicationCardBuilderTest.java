package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.read;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.Confidentiality;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.util.List;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.Test;

class MedicationCardBuilderTest {

  private static <T extends Resource> List<T> resources(Bundle bundle, Class<T> type) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast)
        .toList();
  }

  private static SectionComponent section(Composition composition, String loinc) {
    return composition.getSection().stream()
        .filter(s -> s.getCode().getCodingFirstRep().getCode().equals(loinc))
        .findFirst()
        .orElse(null);
  }

  @Test
  void buildsMedicationCardDocument() {
    Medication med = read("plan-basic.json");
    MappingScope scope = scope(med, TestOptions.withAuthor());

    Bundle bundle = MedicationCardBuilder.build(med, scope);

    assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.DOCUMENT);
    assertThat(bundle.getIdentifier().getValue())
        .isEqualTo("urn:uuid:0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01");
    assertThat(bundle.getTimestampElement().getValueAsString())
        .isEqualTo("2026-10-01T09:30:00+02:00");
    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
    assertThat(composition.getDateElement().getValueAsString())
        .isEqualTo("2026-10-01T09:30:00+02:00");
    assertThat(composition.getType().getCoding()).extracting(c -> c.getCode()).contains("56445-0");
    assertThat(resources(bundle, MedicationStatement.class)).hasSize(2);
    assertThat(section(composition, "10160-0").getEntry()).hasSize(2);
    assertThat(bundle.getEntry())
        .allSatisfy(
            e -> assertThat(e.getFullUrl()).isEqualTo("urn:uuid:" + e.getResource().getIdPart()));
    assertThat(scope.report().hasErrors()).isFalse();
  }

  @Test
  void languageFromPatientLngTitleAndConfidentialityAreDerived() {
    Medication med = read("plan-medical-data.json");
    MappingScope scope =
        scope(med, TestOptions.withAuthor().withConfidentiality(Confidentiality.RESTRICTED));

    Composition composition =
        (Composition) MedicationCardBuilder.build(med, scope).getEntryFirstRep().getResource();

    assertThat(composition.getLanguage()).isEqualTo("fr-CH");
    assertThat(composition.getTitle()).isEqualTo("Plan de médication");
    assertThat(composition.getConfidentiality().toCode()).isEqualTo("R");
    assertThat(scope.report().entries())
        .filteredOn(e -> e.code().equals(ReasonCode.DERIVED_VALUE))
        .extracting(ReportEntry::targetPath)
        .contains("Composition.language", "Composition.confidentiality");
  }

  @Test
  void defaultLanguageWhenLngUnsupported() {
    Medication med =
        MappingTestSupport.parse(
            ch.emed.converter.chmed16a.testsupport.Fixtures.text("chmed16a/plan-basic.json")
                .replace("\"Lng\":\"de\"", "\"Lng\":\"rm\""));
    MappingScope scope = scope(med, TestOptions.withAuthor().withDefaultLanguage("it-CH"));

    Composition composition =
        (Composition) MedicationCardBuilder.build(med, scope).getEntryFirstRep().getResource();

    assertThat(composition.getLanguage()).isEqualTo("it-CH");
    assertThat(composition.getTitle()).isEqualTo("Piano farmacologico");
  }

  @Test
  void originalRepresentationPdfIsIncluded() {
    Medication med = read("plan-basic.json");
    Bundle bundle = MedicationCardBuilder.build(med, scope(med, TestOptions.withAuthor()));

    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
    SectionComponent original = section(composition, "55108-5");
    Binary binary = resources(bundle, Binary.class).get(0);
    assertThat(original.getEntryFirstRep().getReference())
        .isEqualTo("urn:uuid:" + binary.getIdPart());
    assertThat(binary.getContentType()).isEqualTo("application/pdf");
    assertThat(new String(binary.getData(), 0, 5)).isEqualTo("%PDF-");
  }

  @Test
  void remarkBecomesAnnotationSectionAndValidationIsReported() {
    Medication med = read("plan-remarks-validation.json");
    MappingScope scope = scope(med, TestOptions.withAuthor());

    Composition composition =
        (Composition) MedicationCardBuilder.build(med, scope).getEntryFirstRep().getResource();

    SectionComponent annotation = section(composition, "48767-8");
    assertThat(annotation.getTitle()).isEqualTo("Kommentar");
    assertThat(annotation.getText().getDivAsString())
        .contains("Bitte Blutdruck wöchentlich messen.");
    assertThat(scope.report().entries())
        .filteredOn(e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT))
        .extracting(ReportEntry::path)
        .contains("/ValBy", "/ValDt");
  }

  @Test
  void emptyMedicationListUsesEmptyReason() {
    Medication med = read("plan-empty-meds.json");
    Bundle bundle = MedicationCardBuilder.build(med, scope(med, TestOptions.withAuthor()));

    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
    SectionComponent card = section(composition, "10160-0");
    assertThat(card.hasEntry()).isFalse();
    assertThat(card.getEmptyReason().getCodingFirstRep().getCode()).isEqualTo("nilknown");
    assertThat(bundle.getEntry())
        .noneMatch(e -> e.getResource().getResourceType() == ResourceType.MedicationStatement);
  }

  @Test
  void medicamentWithoutPosologyGetsLocalizedDosageText() {
    Medication med = read("plan-older-revision.json");
    MappingScope scope = scope(med, TestOptions.withAuthor());

    MedicationStatement statement =
        resources(MedicationCardBuilder.build(med, scope), MedicationStatement.class).get(0);

    assertThat(statement.getDosageFirstRep().getText()).isEqualTo("Dosierung nicht angegeben");
    assertThat(statement.getIdPart()).isEqualTo(scope.id("/Medicaments/0"));
  }

  @Test
  void aUnitWithoutPosologyIsReportedBecauseThereIsNoDoseToCarryIt() {
    Medication med = read("plan-older-revision.json");
    MappingScope scope = scope(med, TestOptions.withAuthorAndPatientId());

    MedicationCardBuilder.build(med, scope);

    assertThat(scope.report().entries())
        .filteredOn(e -> e.path().equals("/Medicaments/0/Unit"))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.severity().name()).isEqualTo("INFORMATION");
              assertThat(e.clinicalLoss()).isFalse();
            });
  }

  private static final String NO_POSOLOGY =
      """
      {"Id":"0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01","MedType":1,"Auth":"7601000000001",
       "Dt":"2026-10-01T09:30:00+02:00",
       "Patient":{"FName":"Hans","LName":"Muster","BDt":"1950-03-21","Gender":1,"Lng":"de",
                  "Ids":[{"Type":1,"Val":"80756000000000000001"}]},
       "Medicaments":[{"Id":"7680521101306","IdType":2,"TkgRsn":"Schmerzen","AutoMed":1,
                       "AppInstr":"vor dem Essen","Roa":"%s"}]}
      """;

  @Test
  void aMedicamentWithoutPosologyKeepsReasonSelfMedicationInstructionAndRoute() {
    Medication med = MappingTestSupport.parse(NO_POSOLOGY.formatted("PO"));
    MappingScope scope = scope(med, TestOptions.withAuthor());

    Bundle bundle = MedicationCardBuilder.build(med, scope);

    MedicationStatement statement = resources(bundle, MedicationStatement.class).get(0);
    assertThat(statement.getReasonCodeFirstRep().getText()).isEqualTo("Schmerzen");
    assertThat(statement.getCategory().getCodingFirstRep().getCode()).isEqualTo("patientspecified");
    assertThat(statement.getDosage())
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.getText()).isEqualTo("Dosierung nicht angegeben");
              assertThat(d.getPatientInstruction()).isEqualTo("vor dem Essen");
              assertThat(d.getRoute().getCodingFirstRep().getCode()).isEqualTo("20053000");
              assertThat(d.hasTiming()).isFalse();
            });
  }

  @Test
  void anUnmappableRouteWithoutPosologyIsReportedWithClinicalLoss() {
    Medication med = MappingTestSupport.parse(NO_POSOLOGY.formatted("SC"));
    MappingScope scope = scope(med, TestOptions.withAuthor());

    MedicationCardBuilder.build(med, scope);

    assertThat(scope.report().entries())
        .filteredOn(e -> e.path().equals("/Medicaments/0/Roa"))
        .singleElement()
        .satisfies(e -> assertThat(e.clinicalLoss()).isTrue());
  }
}
