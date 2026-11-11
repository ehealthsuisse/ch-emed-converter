package ch.emed.converter.epr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.testsupport.EprFixtures;
import ch.emed.converter.epr.testsupport.UnsupportedElementExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T025 (feature 004, research R5 and F7, data model §4): the pinned list of unsupported elements
 * matches the vendored package, and removal reports every removed element.
 *
 * <p>Regenerate the list with {@code -Depr.regenerate=true} (T030); the test then writes {@code
 * src/main/resources/epr/unsupported-elements.json}.
 */
class UnsupportedElementsTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String EPR = "http://fhir.ch/ig/ch-emed-epr/StructureDefinition/";
  private static final String PMLC =
      "official/Bundle-DocumentPmlcCARAPMP004AxapharmParacetamol.json";
  private static final String PRE = "official/Bundle-DocumentPreParacetamolAxapharmCARAPMP004.json";

  private static JsonNode pinned;
  private static JsonNode extracted;

  @BeforeAll
  static void load() throws IOException {
    extracted = UnsupportedElementExtractor.fromVendoredPackage().extract();
    if (Boolean.getBoolean("epr.regenerate")) {
      Files.writeString(
          Path.of("src/main/resources" + UnsupportedElements.RESOURCE),
          JSON.writer(new com.fasterxml.jackson.core.util.DefaultPrettyPrinter())
                  .with(SerializationFeature.INDENT_OUTPUT)
                  .writeValueAsString(extracted)
                  .replace("\r\n", "\n")
              + "\n",
          StandardCharsets.UTF_8);
    }
    try (InputStream in =
        UnsupportedElements.class.getResourceAsStream(UnsupportedElements.RESOURCE)) {
      pinned = JSON.readTree(in);
    }
  }

  private static Set<String> keys(JsonNode list) {
    Set<String> keys = new TreeSet<>();
    for (JsonNode p : list.path("profiles")) {
      String profile = p.path("profile").asText().replace(EPR, "");
      for (JsonNode e : p.path("elements")) {
        keys.add(
            profile
                + " "
                + e.path("path").asText()
                + " "
                + e.path("scope").asText()
                + " clinical="
                + e.path("clinical").asText());
      }
      for (JsonNode s : p.path("slices")) {
        keys.add(profile + " slice " + s.path("path").asText() + " " + s.path("match"));
      }
    }
    return keys;
  }

  @Test
  void pinnedListMatchesThePackageInBothDirections() {
    Set<String> missing = new TreeSet<>(keys(extracted));
    missing.removeAll(keys(pinned));
    Set<String> extra = new TreeSet<>(keys(pinned));
    extra.removeAll(keys(extracted));

    assertThat(missing).as("in the package, not in the pinned list").isEmpty();
    assertThat(extra).as("in the pinned list, not in the package").isEmpty();
    assertThat(pinned).isEqualTo(extracted);
    assertThat(pinned.path("package").asText()).isEqualTo("ch.fhir.ig.ch-emed-epr#3.0.0");
  }

  @Test
  void everyEntryHasAClinicalFlagAndEverySliceAKnownMatcher() {
    for (JsonNode p : pinned.path("profiles")) {
      for (JsonNode e : p.path("elements")) {
        assertThat(e.path("clinical").isBoolean()).as(e.toString()).isTrue();
        assertThat(e.path("scope").asText()).isIn("all", "pmlc");
      }
      for (JsonNode s : p.path("slices")) {
        assertThat(s.path("match").has("unsupportedDiscriminator")).as(s.toString()).isFalse();
        assertThat(s.path("match").path("resourceType").asText()).isNotEqualTo("Resource");
      }
    }
  }

  @Test
  void flagsFollowTheShortMarkerNotTheDraftStatusAlone() {
    Set<String> keys = keys(pinned);
    // F7: Dosage.text of the base dosage is draft but required; of a split dosage not supported
    assertThat(keys)
        .noneMatch(k -> k.contains("MedicationStatement.dosage:baseEntry.text "))
        .anyMatch(
            k ->
                k.startsWith("ch-emed-epr-medicationstatement-card ")
                    && k.contains(
                        "MedicationStatement.dosage:additionalEntry.text all clinical=true"))
        .contains("ch-emed-epr-patient Patient.generalPractitioner pmlc clinical=false")
        .contains("ch-emed-epr-document-medicationcard Bundle.meta.lastUpdated all clinical=false")
        .anyMatch(
            k ->
                k.startsWith("ch-emed-epr-medicationstatement-card ")
                    && k.contains(
                        "MedicationStatement.dosage:baseEntry.timing.repeat.dayOfWeek all"
                            + " clinical=true"));
  }

  private static ObjectNode document(String fixture) throws IOException {
    return (ObjectNode) JSON.readTree(EprFixtures.bytes(fixture));
  }

  private static ObjectNode resource(ObjectNode bundle, int index) {
    return (ObjectNode) bundle.path("entry").path(index).path("resource");
  }

  private static ReportEntry entry(ReportCollector report, String path) {
    return report.entries().stream()
        .filter(e -> e.path().equals(path))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("no report entry for " + path + ": " + report.entries()));
  }

  @Test
  void removesFromACardAndReportsEachElement() throws IOException {
    ObjectNode bundle = document(PMLC);
    ((ObjectNode) bundle.path("meta")).put("lastUpdated", "2024-01-01T00:00:00Z");
    ObjectNode patient = resource(bundle, 1);
    patient.putArray("generalPractitioner").addObject().put("reference", "Organization/x");
    ObjectNode statement = resource(bundle, 4);
    ArrayNode dosage = (ArrayNode) statement.path("dosage");
    ((ObjectNode) dosage.get(0).path("timing").path("repeat")).putArray("dayOfWeek").add("mon");
    ObjectNode split = dosage.addObject();
    split.put("text", "split");
    split.putObject("timing").putObject("repeat").putArray("dayOfWeek").add("tue");
    ReportCollector report = new ReportCollector();

    UnsupportedElements.load().remove(bundle, EprDocumentType.PMLC, report);

    assertThat(bundle.path("meta").has("lastUpdated")).isFalse();
    assertThat(bundle.path("meta").has("profile")).isTrue();
    assertThat(patient.has("generalPractitioner")).isFalse();
    assertThat(dosage.get(0).path("timing").path("repeat").has("dayOfWeek")).isFalse();
    assertThat(dosage.get(0).has("text")).as("base dosage text is kept (F7)").isTrue();
    assertThat(dosage.get(1).has("text")).isFalse();
    assertThat(dosage.get(1).path("timing").path("repeat").path("dayOfWeek").get(0).asText())
        .isEqualTo("tue");

    ReportEntry meta = entry(report, "Bundle.meta.lastUpdated");
    assertThat(meta.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
    assertThat(meta.severity()).isEqualTo(Severity.INFORMATION);
    assertThat(meta.clinicalLoss()).isFalse();
    assertThat(entry(report, "Bundle.entry[1].resource.generalPractitioner").clinicalLoss())
        .isFalse();
    ReportEntry weekday =
        entry(report, "Bundle.entry[4].resource.dosage[0].timing.repeat.dayOfWeek");
    assertThat(weekday.severity()).isEqualTo(Severity.WARNING);
    assertThat(weekday.clinicalLoss()).isTrue();
    assertThat(entry(report, "Bundle.entry[4].resource.dosage[1].text").clinicalLoss()).isTrue();
    assertThat(report.entries()).allSatisfy(e -> assertThat(e.message()).doesNotContain("mon"));
  }

  @Test
  void pmlcScopeIsKeptForOtherDocumentsAndSlicesMatchTheirSection() throws IOException {
    ObjectNode bundle = document(PRE);
    ObjectNode composition = resource(bundle, 0);
    ObjectNode patient = resource(bundle, 1);
    patient.putArray("generalPractitioner").addObject().put("reference", "Organization/x");
    ArrayNode sections = (ArrayNode) composition.path("section");
    ObjectNode original = null;
    ObjectNode other = null;
    for (JsonNode s : sections) {
      boolean isOriginal =
          "55108-5".equals(s.path("code").path("coding").path(0).path("code").asText());
      if (isOriginal) {
        original = (ObjectNode) s;
      } else {
        other = (ObjectNode) s;
      }
    }
    original.putObject("emptyReason").put("text", "x");
    String otherBefore = other.toString();
    ReportCollector report = new ReportCollector();

    UnsupportedElements.load().remove(bundle, EprDocumentType.PRE, report);

    assertThat(patient.has("generalPractitioner")).isTrue();
    assertThat(original.has("emptyReason")).isFalse();
    assertThat(other.toString()).isEqualTo(otherBefore);
    assertThat(report.entries())
        .extracting(ReportEntry::path)
        .anyMatch(
            p -> p.startsWith("Bundle.entry[0].resource.section[") && p.endsWith("].emptyReason"))
        .noneMatch(p -> p.contains("generalPractitioner"));
  }

  @Test
  void officialExamplesLoseNothingClinical() throws IOException {
    for (String type : new String[] {"PMLC", "PRE", "MTP", "DIS", "PADV", "PML"}) {
      for (String name : EprFixtures.official(type)) {
        ObjectNode bundle = document("official/" + name);
        ReportCollector report = new ReportCollector();

        UnsupportedElements.load().remove(bundle, EprDocumentType.valueOf(type), report);

        // the examples carry a few technical elements such as Bundle.id
        assertThat(report.entries()).as(name).noneMatch(ReportEntry::clinicalLoss);
      }
    }
  }

  // ---- feature 005 (T013, FR-006) --------------------------------------------------------------

  private static int indexOf(ObjectNode bundle, String resourceType) {
    JsonNode entries = bundle.path("entry");
    for (int i = 0; i < entries.size(); i++) {
      if (resourceType.equals(entries.get(i).path("resource").path("resourceType").asText())) {
        return i;
      }
    }
    throw new AssertionError("no " + resourceType);
  }

  @Test
  void removesFromTheChangedRequestOfAnAdviceAsEntryAndContained() throws IOException {
    // as Bundle entry
    ObjectNode bundle = document("official/Bundle-DocumentPadv2.json");
    int i = indexOf(bundle, "MedicationRequest");
    ObjectNode request = resource(bundle, i);
    request.putObject("statusReason").put("text", "changed");
    ReportCollector report = new ReportCollector();

    UnsupportedElements.load().remove(bundle, EprDocumentType.PADV, report);

    assertThat(request.has("statusReason")).isFalse();
    ReportEntry reason = entry(report, "Bundle.entry[" + i + "].resource.statusReason");
    assertThat(reason.clinicalLoss()).isTrue();
    assertThat(reason.message()).doesNotContain("changed");

    // contained in the observation
    bundle = document("official/Bundle-DocumentPadv2.json");
    i = indexOf(bundle, "MedicationRequest");
    int o = indexOf(bundle, "Observation");
    ObjectNode contained = resource(bundle, i).deepCopy();
    contained.putObject("statusReason").put("text", "changed");
    resource(bundle, o).putArray("contained").add(contained);
    report = new ReportCollector();

    UnsupportedElements.load().remove(bundle, EprDocumentType.PADV, report);

    assertThat(contained.has("statusReason")).isFalse();
    assertThat(
            entry(report, "Bundle.entry[" + o + "].resource.contained[0].statusReason")
                .clinicalLoss())
        .isTrue();
  }

  @Test
  void removesFromADispenseAndItsContainedMedication() throws IOException {
    ObjectNode bundle = document("official/Bundle-DocumentDis1.json");
    int i = indexOf(bundle, "MedicationDispense");
    ObjectNode dispense = resource(bundle, i);
    dispense.putArray("partOf").addObject().put("reference", "Procedure/x");
    ObjectNode medication = (ObjectNode) dispense.path("contained").path(0);
    ((ObjectNode) medication.path("meta")).put("lastUpdated", "2024-01-01T00:00:00Z");
    ReportCollector report = new ReportCollector();

    UnsupportedElements.load().remove(bundle, EprDocumentType.DIS, report);

    assertThat(dispense.has("partOf")).isFalse();
    assertThat(medication.path("meta").has("lastUpdated")).isFalse();
    assertThat(entry(report, "Bundle.entry[" + i + "].resource.partOf").clinicalLoss()).isFalse();
    assertThat(entry(report, "Bundle.entry[" + i + "].resource.contained[0].meta.lastUpdated"))
        .isNotNull();
  }

  /** Research F2: CH EMED EPR does not support the advice reference of a dispense. */
  @Test
  void theAdviceReferenceOfADispenseIsRemovedAndReported() throws IOException {
    ObjectNode bundle = document("official/Bundle-DocumentDis1.json");
    int i = indexOf(bundle, "MedicationDispense");
    ObjectNode dispense = resource(bundle, i);
    ObjectNode advice = ((ArrayNode) dispense.path("extension")).addObject();
    advice.put(
        "url", "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-pharmaceuticaladvice");
    ArrayNode parts = advice.putArray("extension");
    for (String part : new String[] {"id", "externalDocumentId"}) {
      ObjectNode p = parts.addObject().put("url", part);
      p.putObject("valueIdentifier")
          .put("system", "urn:ietf:rfc:3986")
          .put("value", "urn:uuid:00000000-0000-4000-8000-000000000009");
    }
    ReportCollector report = new ReportCollector();

    UnsupportedElements.load().remove(bundle, EprDocumentType.DIS, report);

    assertThat(dispense.path("extension").toString()).doesNotContain("pharmaceuticaladvice");
    assertThat(report.entries())
        .anyMatch(e -> e.path().startsWith("Bundle.entry[" + i + "].resource.extension"))
        .allSatisfy(e -> assertThat(e.message()).doesNotContain("urn:uuid"));
  }
}
