package ch.emed.converter.epr.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.Confidentiality;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.TreatmentPlanReference;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

/**
 * T026 (feature 004, research R9, R15, F4, F8, data model §6): data CH EMED EPR requires beyond CH
 * EMED is taken from the source or the options, never invented.
 */
class RequiredDataTest {

  private static final String CARD = "chemed/medication-card-example.json";
  private static final String PRESCRIPTION = "chemed/medication-prescription-example.json";
  private static final String PMLC =
      "official/Bundle-DocumentPmlcCARAPMP004AxapharmParacetamol.json";
  private static final String DOC1 = "urn:uuid:11111111-1111-4111-8111-111111111111";
  private static final String MS1 = "urn:uuid:22222222-2222-4222-8222-222222222222";
  private static final String DOC2 = "urn:uuid:33333333-3333-4333-8333-333333333333";
  private static final String MS2 = "urn:uuid:44444444-4444-4444-8444-444444444444";

  private static Bundle chEmed(String fixture) {
    return withoutReferences(ChEmedJson.parse(Fixtures.bytes(fixture)));
  }

  /** The official PMLC example without its treatment-plan and last-considered extensions. */
  private static Bundle pmlcWithoutReferences() {
    return withoutReferences(ChEmedJson.parse(EprFixtures.bytes(PMLC)));
  }

  /**
   * Removes the EPR references, as in a CH EMED document made from an eMediplan; the CH EMED IG
   * examples carry them.
   */
  private static Bundle withoutReferences(Bundle bundle) {
    for (DomainResource r : resources(bundle, DomainResource.class)) {
      r.getExtension()
          .removeIf(
              e ->
                  e.getUrl().equals(RequiredData.EXT_TREATMENT_PLAN)
                      || e.getUrl().equals(RequiredData.EXT_LAST_CONSIDERED));
    }
    return bundle;
  }

  private static <T extends Resource> List<T> resources(Bundle bundle, Class<T> type) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast)
        .toList();
  }

  private static ReportCollector run(Bundle bundle, EprDocumentType type, ConversionOptions o) {
    ReportCollector report = new ReportCollector();
    new RequiredData().complete(bundle, type, new MappingContext(o, report));
    return report;
  }

  private static ConversionOptions references(int n) {
    List<TreatmentPlanReference> refs =
        List.of(new TreatmentPlanReference(DOC1, MS1), new TreatmentPlanReference(DOC2, MS2));
    return ConversionOptions.defaults().withTreatmentPlanReferences(refs.subList(0, n));
  }

  private static List<ReportEntry> errors(ReportCollector report) {
    return report.entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  private static String value(Extension ext, String url) {
    return ((Identifier) ext.getExtensionByUrl(url).getValue()).getValue();
  }

  @Test
  void prescriptionWithReferencesIsCompleteAndReportsWhatWasAdded() {
    Bundle bundle = chEmed(PRESCRIPTION);
    List<MedicationRequest> requests = resources(bundle, MedicationRequest.class);
    ConversionOptions options = references(requests.size());

    ReportCollector report = run(bundle, EprDocumentType.PRE, options);

    assertThat(errors(report)).isEmpty();
    for (int i = 0; i < requests.size(); i++) {
      Extension plan = requests.get(i).getExtensionByUrl(RequiredData.EXT_TREATMENT_PLAN);
      assertThat(value(plan, "externalDocumentId")).isEqualTo(i == 0 ? DOC1 : DOC2);
      assertThat(value(plan, "id")).isEqualTo(i == 0 ? MS1 : MS2);
      assertThat(((Identifier) plan.getExtensionByUrl("id").getValue()).getSystem())
          .isEqualTo("urn:ietf:rfc:3986");
    }
    assertThat(report.entries())
        .filteredOn(e -> e.code().equals(ReasonCode.DERIVED_VALUE))
        .hasSize(requests.size())
        .allSatisfy(e -> assertThat(e.message()).doesNotContain("urn:uuid"));
  }

  @Test
  void missingReferencesNameEveryEntryPath() {
    Bundle bundle = chEmed(PRESCRIPTION);
    int requests = resources(bundle, MedicationRequest.class).size();

    ReportCollector report = run(bundle, EprDocumentType.PRE, ConversionOptions.defaults());

    assertThat(errors(report))
        .hasSize(requests)
        .allSatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA);
              assertThat(e.path()).matches("Bundle\\.entry\\[\\d+]\\.resource\\.extension");
            });
  }

  @Test
  void aWrongNumberOfReferencesNamesBothCountsNotTheValues() {
    Bundle bundle = chEmed(PRESCRIPTION);
    int requests = resources(bundle, MedicationRequest.class).size();
    ConversionOptions options = references(requests == 1 ? 2 : 1);

    ReportCollector report = run(bundle, EprDocumentType.PRE, options);

    assertThat(errors(report))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.TREATMENT_PLAN_REFERENCES_MISMATCH);
              assertThat(e.message())
                  .contains(String.valueOf(requests))
                  .contains(String.valueOf(requests == 1 ? 2 : 1))
                  .doesNotContain("urn:uuid");
            });
  }

  @Test
  void referencesInTheSourceAreKeptAndNotCounted() {
    Bundle bundle = ChEmedJson.parse(EprFixtures.bytes(PMLC));
    String before = ChEmedJson.context().newJsonParser().encodeResourceToString(bundle);

    ReportCollector report = run(bundle, EprDocumentType.PMLC, references(0));

    assertThat(errors(report)).isEmpty();
    assertThat(ChEmedJson.context().newJsonParser().encodeResourceToString(bundle))
        .isEqualTo(before);
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void pmlcGetsTheLastConsideredDocumentFromItsReference() {
    Bundle bundle = pmlcWithoutReferences();
    List<MedicationStatement> statements = resources(bundle, MedicationStatement.class);

    ReportCollector report = run(bundle, EprDocumentType.PMLC, references(statements.size()));

    assertThat(errors(report)).isEmpty();
    MedicationStatement first = statements.get(0);
    assertThat(((Identifier) first.getExtensionByUrl(RequiredData.EXT_LAST_CONSIDERED).getValue()))
        .satisfies(
            id -> {
              assertThat(id.getValue()).isEqualTo(DOC1);
              assertThat(id.getSystem()).isEqualTo("urn:ietf:rfc:3986");
            });
    assertThat(report.entries())
        .filteredOn(e -> e.message().startsWith("Last considered document"))
        .hasSize(statements.size());
  }

  @Test
  void pmlcNeedsTheAggregationServiceAsAuthor() {
    Bundle card = chEmed(CARD);
    int statements = resources(card, MedicationStatement.class).size();

    ReportCollector report = run(card, EprDocumentType.PMLC, references(statements));

    assertThat(errors(report))
        .extracting(ReportEntry::code, ReportEntry::path)
        .containsExactly(
            tuple(ReasonCode.MISSING_REQUIRED_DATA, "Bundle.entry[0].resource.author"));
  }

  @Test
  void aPractitionerAuthorWithoutRoleNeedsTheOptionAndNoOrganisationIsInvented() {
    Bundle bundle = chEmed(PRESCRIPTION);
    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
    PractitionerRole role = resources(bundle, PractitionerRole.class).get(0);
    composition.getAuthorFirstRep().setReference(role.getPractitioner().getReference());
    bundle.getEntry().removeIf(e -> e.getResource() == role);
    int requests = resources(bundle, MedicationRequest.class).size();
    int entries = bundle.getEntry().size();

    ReportCollector without = run(bundle, EprDocumentType.PRE, references(requests));

    assertThat(errors(without))
        .extracting(ReportEntry::code, ReportEntry::path)
        .containsExactly(
            tuple(ReasonCode.MISSING_REQUIRED_DATA, "Bundle.entry[0].resource.author[0]"));
    assertThat(bundle.getEntry()).hasSize(entries);

    Bundle again = chEmed(PRESCRIPTION);
    Composition c2 = (Composition) again.getEntryFirstRep().getResource();
    PractitionerRole r2 = resources(again, PractitionerRole.class).get(0);
    c2.getAuthorFirstRep().setReference(r2.getPractitioner().getReference());
    again.getEntry().removeIf(e -> e.getResource() == r2);
    ConversionOptions options = references(requests).withAuthor(TestOptions.AUTHOR);

    ReportCollector with = run(again, EprDocumentType.PRE, options);

    assertThat(errors(with)).isEmpty();
    assertThat(resources(again, PractitionerRole.class)).hasSize(1);
    assertThat(c2.getAuthorFirstRep().getReference()).startsWith("urn:uuid:");
    assertThat(with.entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.DERIVED_VALUE)
                    && e.path().equals("Bundle.entry[0].resource.author[0]"));
  }

  @Test
  void aPractitionerAuthorWithItsRoleInTheDocumentUsesTheRole() {
    Bundle bundle = chEmed(PRESCRIPTION);
    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
    PractitionerRole role = resources(bundle, PractitionerRole.class).get(0);
    composition.getAuthorFirstRep().setReference(role.getPractitioner().getReference());
    int requests = resources(bundle, MedicationRequest.class).size();

    ReportCollector report = run(bundle, EprDocumentType.PRE, references(requests));

    assertThat(errors(report)).isEmpty();
    int roleIndex = -1;
    for (int i = 0; i < bundle.getEntry().size(); i++) {
      if (bundle.getEntry().get(i).getResource() == role) {
        roleIndex = i;
      }
    }
    assertThat(RequiredData.index(bundle, composition.getAuthorFirstRep())).isEqualTo(roleIndex);
  }

  @Test
  void patientIdentifierComesFromTheOptionOrIsMissing() {
    Bundle bundle = chEmed(PRESCRIPTION);
    Patient patient = resources(bundle, Patient.class).get(0);
    patient.getIdentifier().clear();
    int requests = resources(bundle, MedicationRequest.class).size();

    ReportCollector without = run(bundle, EprDocumentType.PRE, references(requests));

    assertThat(errors(without))
        .extracting(ReportEntry::code, e -> e.path().endsWith(".resource.identifier"))
        .containsExactly(tuple(ReasonCode.MISSING_REQUIRED_DATA, true));
    assertThat(without.entries()).allSatisfy(e -> assertThat(e.message()).doesNotContain("TEST"));

    Bundle again = chEmed(PRESCRIPTION);
    Patient p2 = resources(again, Patient.class).get(0);
    p2.getIdentifier().clear();
    ReportCollector with =
        run(
            again,
            EprDocumentType.PRE,
            references(requests).withPatientIdentifier(TestOptions.PATIENT_ID));

    assertThat(errors(with)).isEmpty();
    assertThat(p2.getIdentifierFirstRep().getValue()).isEqualTo("TEST-0001");
  }

  @Test
  void confidentialityIsFilledFromTheOptionNeverMissing() {
    Bundle bundle = chEmed(PRESCRIPTION);
    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
    composition.setConfidentiality(null);
    composition.getConfidentialityElement().getExtension().clear();
    int requests = resources(bundle, MedicationRequest.class).size();

    ReportCollector report =
        run(
            bundle,
            EprDocumentType.PRE,
            references(requests).withConfidentiality(Confidentiality.RESTRICTED));

    assertThat(errors(report)).isEmpty();
    assertThat(composition.getConfidentiality().toCode()).isEqualTo("R");
    Extension ext =
        composition.getConfidentialityElement().getExtensionByUrl(RequiredData.EXT_CONFIDENTIALITY);
    assertThat(((CodeableConcept) ext.getValue()).getCodingFirstRep().getCode())
        .isEqualTo(Confidentiality.RESTRICTED.snomedCode());
    assertThat(report.entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.DERIVED_VALUE)
                    && e.path().equals("Bundle.entry[0].resource.confidentiality"));
  }

  @Test
  void treatmentPlansAreNotRequiredForMtp() {
    Bundle bundle = ChEmedJson.parse(EprFixtures.bytes("official/Bundle-DocumentMtp1.json"));
    for (DomainResource r : resources(bundle, MedicationStatement.class)) {
      r.getExtension().removeIf(e -> e.getUrl().equals(RequiredData.EXT_TREATMENT_PLAN));
    }

    ReportCollector report = run(bundle, EprDocumentType.MTP, ConversionOptions.defaults());

    assertThat(errors(report)).isEmpty();
  }

  // ---- feature 005 (T012, research R4, F1; rules M-EPR-28, M-EPR-29) --------------------------

  private static Bundle module(String fixture) {
    return ChEmedJson.parse(EprFixtures.bytes(fixture));
  }

  /** The dispense 1-2 without its treatment-plan reference (3-1 and 3-2 are administrations). */
  private static Bundle dispenseWithoutPlan() {
    Bundle dis = module("chemed/Bundle-1-2-MedicationDispense.json");
    resources(dis, MedicationDispense.class)
        .forEach(
            d ->
                d.getExtension().removeIf(e -> e.getUrl().equals(RequiredData.EXT_TREATMENT_PLAN)));
    return dis;
  }

  private static List<ReportEntry> missingExtensions(ReportCollector report) {
    return errors(report).stream()
        .filter(e -> e.code().equals(ReasonCode.MISSING_REQUIRED_DATA))
        .filter(e -> e.path().endsWith(".extension"))
        .toList();
  }

  @Test
  void aDispenseWithItsTreatmentPlanIsKept() {
    Bundle dis = module("chemed/Bundle-1-2-MedicationDispense.json");
    MedicationDispense dispense = resources(dis, MedicationDispense.class).get(0);
    Extension before = dispense.getExtensionByUrl(RequiredData.EXT_TREATMENT_PLAN).copy();

    ReportCollector report = run(dis, EprDocumentType.DIS, ConversionOptions.defaults());

    assertThat(missingExtensions(report)).isEmpty();
    assertThat(dispense.getExtensionByUrl(RequiredData.EXT_TREATMENT_PLAN).equalsDeep(before))
        .isTrue();
    assertThat(report.entries())
        .noneMatch(
            e -> e.code().equals(ReasonCode.DERIVED_VALUE) && e.path().endsWith(".extension"));
  }

  @Test
  void aDispenseWithoutTreatmentPlanNeedsExactlyOneReference() {
    // without option: missing, no identifier in the message
    Bundle dis = dispenseWithoutPlan();
    ReportCollector report = run(dis, EprDocumentType.DIS, ConversionOptions.defaults());
    assertThat(missingExtensions(report))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.path()).matches("Bundle\\.entry\\[\\d+]\\.resource\\.extension");
              assertThat(e.message()).doesNotContain("urn:uuid");
            });

    // one reference: added and reported as derived value
    dis = dispenseWithoutPlan();
    report = run(dis, EprDocumentType.DIS, references(1));
    assertThat(missingExtensions(report)).isEmpty();
    Extension plan =
        resources(dis, MedicationDispense.class)
            .get(0)
            .getExtensionByUrl(RequiredData.EXT_TREATMENT_PLAN);
    assertThat(value(plan, "externalDocumentId")).isEqualTo(DOC1);
    assertThat(value(plan, "id")).isEqualTo(MS1);
    assertThat(report.entries())
        .filteredOn(
            e -> e.code().equals(ReasonCode.DERIVED_VALUE) && e.path().endsWith(".extension"))
        .hasSize(1);

    // two references for one dispense
    dis = dispenseWithoutPlan();
    report = run(dis, EprDocumentType.DIS, references(2));
    assertThat(errors(report))
        .extracting(ReportEntry::code)
        .contains(ReasonCode.TREATMENT_PLAN_REFERENCES_MISMATCH);
  }

  /** Research F3: CH EMED EPR has no administration; such a dispense document has no DIS. */
  @Test
  void aDispenseDocumentRecordingAnAdministrationIsRefused() {
    for (String name :
        List.of("Bundle-3-1-MedicationDispense.json", "Bundle-3-2-MedicationDispense.json")) {
      Bundle dis = module("chemed/" + name);

      ReportCollector report = run(dis, EprDocumentType.DIS, references(1));

      assertThat(errors(report))
          .as(name)
          .extracting(ReportEntry::code, ReportEntry::path)
          .contains(tuple(ReasonCode.MISSING_REQUIRED_DATA, "Bundle.entry[0].resource.section"));
    }
  }

  @Test
  void aReferenceForADispenseThatHasOneIsAMismatch() {
    Bundle dis = module("chemed/Bundle-1-2-MedicationDispense.json");

    ReportCollector report = run(dis, EprDocumentType.DIS, references(1));

    assertThat(errors(report))
        .extracting(ReportEntry::code)
        .contains(ReasonCode.TREATMENT_PLAN_REFERENCES_MISMATCH);
  }

  @Test
  void anAdviceObservationNeedsNoTreatmentPlan() {
    Bundle padv = module("chemed/Bundle-2-2-PharmaceuticalAdvice.json");
    for (DomainResource r : resources(padv, DomainResource.class)) {
      r.getExtension().removeIf(e -> e.getUrl().equals(RequiredData.EXT_TREATMENT_PLAN));
    }

    ReportCollector report = run(padv, EprDocumentType.PADV, ConversionOptions.defaults());

    assertThat(missingExtensions(report)).isEmpty();
  }

  /**
   * Research F1: the changed MedicationRequest of an advice needs its treatment plan (EPR 1..1).
   */
  @Test
  void theChangedRequestOfAnAdviceTakesItsTreatmentPlanFromTheOption() {
    Bundle padv = module("official/Bundle-DocumentPadv2.json");
    MedicationRequest changed = resources(padv, MedicationRequest.class).get(0);
    changed.getExtension().removeIf(e -> e.getUrl().equals(RequiredData.EXT_TREATMENT_PLAN));

    ReportCollector report = run(padv, EprDocumentType.PADV, ConversionOptions.defaults());
    assertThat(missingExtensions(report)).hasSize(1);

    padv = module("official/Bundle-DocumentPadv2.json");
    changed = resources(padv, MedicationRequest.class).get(0);
    changed.getExtension().removeIf(e -> e.getUrl().equals(RequiredData.EXT_TREATMENT_PLAN));
    report = run(padv, EprDocumentType.PADV, references(1));
    assertThat(missingExtensions(report)).isEmpty();
    assertThat(value(changed.getExtensionByUrl(RequiredData.EXT_TREATMENT_PLAN), "id"))
        .isEqualTo(MS1);
  }

  @Test
  void aListWithADeviceAuthorIsComplete() {
    Bundle pml = module("chemed/Bundle-2-1-MedicationList.json");

    ReportCollector report = run(pml, EprDocumentType.PML, ConversionOptions.defaults());

    assertThat(errors(report)).noneMatch(e -> e.path().equals("Bundle.entry[0].resource.author"));
  }

  @Test
  void aListNeedsTheAggregationServiceAsAuthor() {
    Bundle pml = module("chemed/Bundle-2-1-MedicationList.json");
    Composition composition = (Composition) pml.getEntryFirstRep().getResource();
    PractitionerRole role = resources(pml, PractitionerRole.class).get(0);
    String url =
        pml.getEntry().stream()
            .filter(e -> e.getResource() == role)
            .findFirst()
            .orElseThrow()
            .getFullUrl();
    composition.getAuthor().clear();
    composition.addAuthor(new Reference(url));

    ReportCollector report = run(pml, EprDocumentType.PML, ConversionOptions.defaults());

    assertThat(errors(report))
        .extracting(ReportEntry::code, ReportEntry::path)
        .containsExactly(
            tuple(ReasonCode.MISSING_REQUIRED_DATA, "Bundle.entry[0].resource.author"));
  }

  @Test
  void treatmentPlanReferencesAreNotUsedForAList() {
    Bundle pml = module("chemed/Bundle-2-1-MedicationList.json");
    List<String> before =
        resources(pml, DomainResource.class).stream()
            .filter(r -> !(r instanceof Composition))
            .map(r -> r.getExtension().stream().map(Extension::getUrl).toList().toString())
            .toList();

    ReportCollector report = run(pml, EprDocumentType.PML, references(1));

    assertThat(errors(report)).isEmpty();
    assertThat(
            resources(pml, DomainResource.class).stream()
                .filter(r -> !(r instanceof Composition))
                .map(r -> r.getExtension().stream().map(Extension::getUrl).toList().toString())
                .toList())
        .isEqualTo(before);
    assertThat(report.entries())
        .filteredOn(e -> e.severity() == Severity.WARNING)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.path()).isEqualTo("options.treatmentPlanReferences");
              assertThat(e.message()).contains("not used");
            });
  }
}
