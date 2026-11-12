package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.ClinicalEquivalence;
import ch.emed.converter.chmed16a.testsupport.ClinicalEquivalence.Difference;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.TreatmentPlanReference;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.mapping.UnsupportedElements;
import ch.emed.converter.epr.testsupport.EprFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T053 (feature 004, FR-010, SC-003): round trips through CH EMED EPR differ from the original only
 * by the changes the mapping specification documents, listed below per rule id.
 */
class RoundTripIT {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String LOINC_ORIGINAL = "55108-5";
  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(
      FormatId from, FormatId to, byte[] input, ConversionOptions options) {
    return converter.convert(new ConversionRequest(from, to, input, options));
  }

  private static List<String> errors(ConversionResult result) {
    return result.report().entries().stream()
        .filter(e -> e.severity() == Severity.ERROR)
        .map(e -> e.code() + " " + e.path() + " " + e.message())
        .toList();
  }

  // ---- allowed differences, per rule id of docs/mapping/ch-emed-epr.md -------------------------

  /** M-EPR-01: profiles with a FHIR core base (device, body weight) do not come back. */
  private static void coreBaseProfiles(ObjectNode bundle) {
    for (JsonNode entry : bundle.path("entry")) {
      JsonNode meta = entry.path("resource").path("meta");
      if (meta instanceof ObjectNode m && m.path("profile") instanceof ArrayNode profiles) {
        for (Iterator<JsonNode> it = profiles.iterator(); it.hasNext(); ) {
          String p = it.next().asText();
          if (p.endsWith("/ch-emed-epr-device")
              || p.endsWith("/ch-emed-epr-observation-body-weight")) {
            it.remove();
          }
        }
        if (profiles.isEmpty()) {
          m.remove("profile");
        }
        if (m.isEmpty()) {
          ((ObjectNode) entry.path("resource")).remove("meta");
        }
      }
    }
  }

  /** M-EPR-22: unsupported elements are removed (and reported). */
  private static void unsupported(ObjectNode bundle, EprDocumentType type) {
    UnsupportedElements.load().remove(bundle, type, new ReportCollector());
  }

  /**
   * M-EPR-25: the original representation of the examples is a placeholder, so PRE and PMLC get a
   * rendered PDF/A; section and Binary are compared without it.
   */
  private static void originalRepresentation(ObjectNode bundle) {
    ArrayNode entries = (ArrayNode) bundle.path("entry");
    for (int i = entries.size() - 1; i >= 0; i--) {
      if ("Binary".equals(entries.get(i).path("resource").path("resourceType").asText())) {
        entries.remove(i);
      }
    }
    JsonNode sections = entries.get(0).path("resource").path("section");
    if (sections instanceof ArrayNode array) {
      for (int i = array.size() - 1; i >= 0; i--) {
        if (LOINC_ORIGINAL.equals(
            array.get(i).path("code").path("coding").path(0).path("code").asText())) {
          array.remove(i);
        }
      }
    }
  }

  private static ObjectNode normalized(byte[] epr, EprDocumentType type) throws IOException {
    ObjectNode bundle = (ObjectNode) JSON.readTree(ChEmedJson.serialize(ChEmedJson.parse(epr)));
    coreBaseProfiles(bundle);
    unsupported(bundle, type);
    originalRepresentation(bundle);
    return bundle;
  }

  static Stream<String> officialExamples() {
    return Stream.of("PMLC", "PRE", "MTP", "DIS", "PADV", "PML")
        .flatMap(t -> EprFixtures.official(t).stream().map(n -> t + " " + n));
  }

  @ParameterizedTest
  @MethodSource("officialExamples")
  void eprToChEmedAndBackGivesTheOriginal(String example) throws IOException {
    EprDocumentType type = EprDocumentType.valueOf(example.substring(0, example.indexOf(' ')));
    byte[] original = EprFixtures.bytes("official/" + example.substring(example.indexOf(' ') + 1));

    ConversionResult chEmed =
        convert(FormatId.CH_EMED_EPR, FormatId.CH_EMED, original, ConversionOptions.defaults());
    assertThat(errors(chEmed)).as(example).isEmpty();
    ConversionResult back =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            chEmed.output().orElseThrow(),
            ConversionOptions.defaults());
    assertThat(errors(back)).as(example).isEmpty();
    assertThat(back.report().entries()).as(example).noneMatch(ReportEntry::clinicalLoss);

    assertThat(normalized(back.output().orElseThrow(), type))
        .as(example)
        .isEqualTo(normalized(original, type));
  }

  // ---- CH EMED → EPR → CH EMED ---------------------------------------------------------------

  static Stream<String> prescriptions() {
    return Stream.of(
        "chemed/medication-prescription-example.json",
        "chmed16a/rx-basic.json",
        "chmed16a/rx-erezept-example.json",
        "chmed16a/rx-idtype4.json",
        // feature 005 (T026): CH EMED dispenses, advice and lists of this module
        "epr:chemed/Bundle-1-2-MedicationDispense.json",
        "epr:chemed/Bundle-2-4-MedicationDispense.json",
        "epr:chemed/Bundle-2-2-PharmaceuticalAdvice.json",
        "epr:chemed/Bundle-PharmaceuticalAdvice-ChangeDosage.json",
        "epr:chemed/Bundle-2-1-MedicationList.json",
        "epr:synthetic/Bundle-DispenseWithoutTreatmentPlan.json");
  }

  private static byte[] chEmed(String fixture) {
    if (fixture.startsWith("epr:")) {
      return EprFixtures.bytes(fixture.substring(4));
    }
    if (fixture.startsWith("chemed/")) {
      return Fixtures.bytes(fixture);
    }
    byte[] out =
        convert(
                FormatId.CHMED16A,
                FormatId.CH_EMED,
                Fixtures.bytes(fixture),
                TestOptions.withAuthorAndPatientId())
            .output()
            .orElse(null);
    if (out == null) {
      return null;
    }
    return new String(out, StandardCharsets.UTF_8)
        .replace(ChEmedToEprIT.INVALID_GLN, ChEmedToEprIT.VALID_GLN)
        .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Allowed clinical differences after CH EMED → EPR → CH EMED: elements removed and reported as
   * UNMAPPABLE_ELEMENT (M-EPR-22 unsupported, M-EPR-27 substitution "allowed"). A difference is
   * allowed if a reported path ends with the same element name.
   */
  @ParameterizedTest
  @MethodSource("prescriptions")
  void chEmedToEprAndBackDiffersOnlyByReportedElements(String fixture) {
    byte[] original = chEmed(fixture);
    if (original == null) {
      return; // feature 001 does not convert this fixture with the test options
    }
    int missing =
        (int)
            ChEmedJson.parse(original).getEntry().stream()
                .filter(
                    e ->
                        (e.getResource() instanceof MedicationRequest
                                || e.getResource()
                                    instanceof org.hl7.fhir.r4.model.MedicationDispense)
                            && ((org.hl7.fhir.r4.model.DomainResource) e.getResource())
                                    .getExtensionByUrl(
                                        "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-treatmentplan")
                                == null)
                .count();
    List<TreatmentPlanReference> refs = new ArrayList<>();
    for (int i = 0; i < missing; i++) {
      refs.add(
          new TreatmentPlanReference(
              "urn:uuid:" + UUID.nameUUIDFromBytes(("d" + i).getBytes()),
              "urn:uuid:" + UUID.nameUUIDFromBytes(("s" + i).getBytes())));
    }

    ConversionResult epr =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            original,
            ConversionOptions.defaults().withTreatmentPlanReferences(refs));
    assertThat(errors(epr)).as(fixture).isEmpty();
    ConversionResult back =
        convert(
            FormatId.CH_EMED_EPR,
            FormatId.CH_EMED,
            epr.output().orElseThrow(),
            ConversionOptions.defaults());
    assertThat(errors(back)).as(fixture).isEmpty();

    List<String> removed =
        epr.report().entries().stream()
            .filter(e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT))
            .map(ReportEntry::path)
            .toList();
    List<Difference> unexplained = new ArrayList<>();
    for (Difference d : ClinicalEquivalence.chEmed(original, back.output().orElseThrow())) {
      String path = d.path();
      String last = path.substring(path.lastIndexOf('/') + 1);
      String element = last.matches("\\d+") ? path.substring(0, path.lastIndexOf('/')) : path;
      String name = element.substring(element.lastIndexOf('/') + 1);
      boolean reported =
          removed.stream()
              .anyMatch(
                  p ->
                      p.endsWith("." + name)
                          || p.contains("." + name + "[")
                          // a parent left empty by a reported removal is pruned with it
                          || p.contains("." + name + "."));
      if (!reported) {
        unexplained.add(d);
      }
    }
    assertThat(unexplained).as(fixture + ", removed " + removed).isEmpty();
  }
}
