package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.parse;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.read;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

/** M-RX-01..M-RX-04, M-RX-11: the Medication Prescription document. */
class MedicationPrescriptionBuilderTest {

  private MappingScope scope;

  private Bundle build(Medication med, ConversionOptions options) {
    scope = scope(med, options);
    return MedicationPrescriptionBuilder.build(med, scope);
  }

  private Bundle build(String fixture) {
    return build(read(fixture), ConversionOptions.defaults());
  }

  private static <T extends Resource> List<T> all(Bundle bundle, Class<T> type) {
    return bundle.getEntry().stream()
        .map(e -> e.getResource())
        .filter(type::isInstance)
        .map(type::cast)
        .toList();
  }

  private List<ReportEntry> entries(String path) {
    return scope.report().entries().stream().filter(e -> e.path().equals(path)).toList();
  }

  @Test
  void buildsTheDocumentStructureOfAPrescription() {
    Bundle bundle = build("rx-basic.json");

    assertThat(scope.report().hasErrors()).isFalse();
    assertThat(bundle.getMeta().getProfile().get(0).getValue())
        .isEqualTo(
            "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-document-medicationprescription");
    assertThat(bundle.getIdentifier().getValue())
        .isEqualTo("urn:uuid:7b1f3c1e-9d54-4a0e-8e0c-2a6c1f9e8d02");
    Composition composition = all(bundle, Composition.class).get(0);
    assertThat(composition.getType().getCoding())
        .extracting(c -> c.getCode())
        .containsExactly("57833-6", "761938008");
    assertThat(composition.getTitle()).isEqualTo("Rezept");
    assertThat(composition.getSection())
        .extracting(s -> s.getCode().getCodingFirstRep().getCode())
        .containsExactly("55108-5", "57828-6", "48767-8");
    assertThat(composition.getSection().get(1).getEntry()).hasSize(2);
    assertThat(composition.getSection().get(1).getTitle()).isEqualTo("Arzneimittelverordnung");
  }

  @Test
  void oneMedicationRequestPerMedicamentWithTheRequiredElements() {
    Bundle bundle = build("rx-basic.json");

    List<MedicationRequest> requests = all(bundle, MedicationRequest.class);
    assertThat(requests).hasSize(2);
    MedicationRequest first = requests.get(0);
    assertThat(first.getStatus()).isEqualTo(MedicationRequest.MedicationRequestStatus.ACTIVE);
    assertThat(first.getIntent()).isEqualTo(MedicationRequest.MedicationRequestIntent.ORDER);
    assertThat(first.getAuthoredOnElement().getValueAsString())
        .isEqualTo("2026-10-01T09:30:00+02:00");
    assertThat(first.getRequester().getReference()).startsWith("urn:uuid:");
    assertThat(first.getMedicationReference().getReference()).isEqualTo("#med");
    assertThat(first.getContained()).hasSize(1);
    assertThat(first.getDosageInstruction()).hasSize(2);
    assertThat(first.getIdentifierFirstRep().getValue()).startsWith("urn:uuid:");
    assertThat(first.hasText()).isTrue();
    assertThat(((org.hl7.fhir.r4.model.Medication) first.getContained().get(0)).hasText())
        .as("dom-1")
        .isFalse();
  }

  @Test
  void aMedicamentWithoutPosologyGetsTheLocalizedNoDosageNote() {
    Bundle bundle = build("rx-basic.json");

    MedicationRequest second = all(bundle, MedicationRequest.class).get(1);

    assertThat(second.getDosageInstruction())
        .singleElement()
        .satisfies(d -> assertThat(d.getText()).isEqualTo("Dosierung nicht angegeben"));
    assertThat(entries("/Medicaments/1/Pos"))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE));
  }

  @Test
  void theAuthorComesFromHcPersonAndHcOrg() {
    Bundle bundle = build("rx-basic.json");

    Practitioner practitioner = all(bundle, Practitioner.class).get(0);
    Organization organization = all(bundle, Organization.class).get(0);
    assertThat(practitioner.getIdentifierFirstRep().getSystem()).isEqualTo(Profiles.GLN);
    assertThat(practitioner.getIdentifierFirstRep().getValue()).isEqualTo("7601000000001");
    assertThat(practitioner.getNameFirstRep().getFamily()).isEqualTo("Test");
    assertThat(organization.getName()).isEqualTo("Praxis Test");
    assertThat(organization.getAlias())
        .singleElement()
        .satisfies(a -> assertThat(a.getValue()).isEqualTo("Allgemeine Innere Medizin"));
    assertThat(organization.getTelecom())
        .extracting(t -> t.getValue())
        .containsExactly("+41 31 111 11 11", "praxis@example.org");
    assertThat(organization.getIdentifier())
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.getSystem()).isEqualTo(Profiles.ZSR);
              assertThat(i.getValue()).isEqualTo("H123456");
            });
    assertThat(organization.getAddressFirstRep().hasCountry()).as("no invented country").isFalse();
  }

  @Test
  void withoutHcPersonTheAuthorOptionSuppliesTheDetails() {
    Medication med = read("rx-missing-hcperson.json");

    Bundle bundle = build(med, TestOptions.withAuthor());

    assertThat(scope.report().hasErrors()).isFalse();
    Practitioner practitioner = all(bundle, Practitioner.class).get(0);
    assertThat(practitioner.getIdentifierFirstRep().getValue()).isEqualTo("7601000000001");
    assertThat(practitioner.getNameFirstRep().getFamily()).isEqualTo("Test");
  }

  @Test
  void withoutHcPersonAndWithoutOptionTheAuthorIsMissingRequiredData() {
    build(read("rx-missing-hcperson.json"), ConversionOptions.defaults());

    assertThat(scope.report().entries())
        .anyMatch(
            e ->
                e.severity() == Severity.ERROR
                    && e.code().equals(ReasonCode.MISSING_REQUIRED_DATA)
                    && e.path().equals("/HcPerson"));
  }

  @Test
  void authDifferingFromTheHcPersonGlnIsAWarningAndTheHcPersonWins() {
    Medication med =
        parse(
            ch.emed.converter.chmed16a.testsupport.Fixtures.text("chmed16a/rx-basic.json")
                .replace("\"Auth\":\"7601000000001\"", "\"Auth\":\"7601000000002\""));

    Bundle bundle = build(med, ConversionOptions.defaults());

    assertThat(all(bundle, Practitioner.class).get(0).getIdentifierFirstRep().getValue())
        .isEqualTo("7601000000001");
    assertThat(entries("/Auth"))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.code()).isEqualTo(ReasonCode.CHMED_INVALID_FIELD);
            });
  }

  @Test
  void aRootZsrThatIsNotOnPersonOrOrganisationIsAttachedToTheOrganisation() {
    Medication med =
        parse(
            ch.emed.converter.chmed16a.testsupport.Fixtures.text("chmed16a/rx-basic.json")
                .replace("\"Zsr\":\"H123456\",\"Dt\"", "\"Zsr\":\"H999999\",\"Dt\""));

    Bundle bundle = build(med, ConversionOptions.defaults());

    assertThat(all(bundle, Organization.class).get(0).getIdentifier())
        .extracting(i -> i.getValue())
        .containsExactly("H123456", "H999999");
    assertThat(entries("/Zsr"))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE));
  }

  @Test
  void aPrescriptionWithoutMedicamentsCannotBeConverted() {
    Medication med =
        parse(
            ch.emed.converter.chmed16a.testsupport.Fixtures.text("chmed16a/rx-basic.json")
                .replaceAll(
                    "\"Medicaments\":\\[.*?\\}\\],\"MedType\"", "\"Medicaments\":[],\"MedType\""));

    build(med, ConversionOptions.defaults());

    assertThat(entries("/Medicaments"))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.ERROR);
              assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA);
            });
  }

  @Test
  void languageComesFromTheDefaultBecausePrescriptionsHaveNoPatientLanguage() {
    Bundle bundle =
        build(read("rx-basic.json"), ConversionOptions.defaults().withDefaultLanguage("fr-CH"));

    Composition composition = all(bundle, Composition.class).get(0);
    assertThat(composition.getLanguage()).isEqualTo("fr-CH");
    assertThat(composition.getTitle()).isEqualTo("Ordonnance");
    assertThat(entries("/Patient/Lng"))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE));
  }

  @Test
  void planOnlyFieldsOfAMedicamentAreHandled() {
    Medication med =
        parse(
            ch.emed.converter.chmed16a.testsupport.Fixtures.text("chmed16a/rx-basic.json")
                .replace(
                    "\"IdType\":2,",
                    "\"IdType\":2,\"TkgRsn\":\"Schmerzen\",\"PrscbBy\":\"Dr. X\","));

    Bundle bundle = build(med, ConversionOptions.defaults());

    MedicationRequest first = all(bundle, MedicationRequest.class).get(0);
    assertThat(first.getReasonCode())
        .extracting(c -> c.getText())
        .containsExactly("Schmerzen", "SL indication code 12345.01");
    assertThat(entries("/Medicaments/0/PrscbBy"))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT));
  }

  @Test
  void aUnitWithoutPosologyIsReported() {
    Medication med =
        parse(
            ch.emed.converter.chmed16a.testsupport.Fixtures.text("chmed16a/rx-basic.json")
                .replace("\"Subs\":0}", "\"Subs\":0,\"Unit\":\"STK\"}"));

    build(med, ConversionOptions.defaults());

    assertThat(entries("/Medicaments/1/Unit"))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT));
  }

  @Test
  void anInstructionWithoutPosologyStaysThePatientInstructionAndTheNoteTheText() {
    Medication med =
        parse(
            ch.emed.converter.chmed16a.testsupport.Fixtures.text("chmed16a/rx-basic.json")
                .replace("\"Subs\":0}", "\"Subs\":0,\"AppInstr\":\"nach Bedarf\"}"));

    Bundle bundle = build(med, ConversionOptions.defaults());

    MedicationRequest second = all(bundle, MedicationRequest.class).get(1);
    assertThat(second.getDosageInstruction())
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.getText()).isEqualTo("Dosierung nicht angegeben");
              assertThat(d.getPatientInstruction()).isEqualTo("nach Bedarf");
            });
  }
}
