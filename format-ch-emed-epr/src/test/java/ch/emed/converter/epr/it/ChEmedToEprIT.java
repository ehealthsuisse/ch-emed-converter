package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
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
import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.core.spi.ChEmedValidator;
import ch.emed.converter.epr.mapping.OriginalRepresentation;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T029 (feature 004, spec US2, SC-002): CH EMED → CH EMED EPR end to end through the converter,
 * with the validator of the distribution.
 */
class ChEmedToEprIT {

  private static Converter converter;

  @TempDir Path tmp;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  // ---- helpers -------------------------------------------------------------------------------

  private static ConversionResult convert(
      FormatId source, FormatId target, byte[] input, ConversionOptions options) {
    return converter.convert(new ConversionRequest(source, target, input, options));
  }

  private static <T extends Resource> List<T> resources(byte[] document, Class<T> type) {
    return ChEmedJson.parse(document).getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast)
        .toList();
  }

  /** n references with stable, fictitious identifiers. */
  private static List<TreatmentPlanReference> references(int n) {
    List<TreatmentPlanReference> list = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      list.add(
          new TreatmentPlanReference(
              "urn:uuid:" + UUID.nameUUIDFromBytes(("doc" + i).getBytes()),
              "urn:uuid:" + UUID.nameUUIDFromBytes(("ms" + i).getBytes())));
    }
    return list;
  }

  private static List<String> errors(ConversionResult result) {
    return result.report().entries().stream()
        .filter(e -> e.severity() == Severity.ERROR)
        .map(e -> e.code() + " " + e.path() + " " + e.message())
        .toList();
  }

  private boolean preflight(byte[] pdf) throws Exception {
    Path file = Files.createTempFile(tmp, "or", ".pdf");
    Files.write(file, pdf);
    return PreflightParser.validate(file.toFile()).isValid();
  }

  private void assertPdfAOriginalRepresentation(byte[] epr) throws Exception {
    List<Binary> binaries = resources(epr, Binary.class);
    assertThat(binaries).isNotEmpty();
    for (Binary binary : binaries) {
      assertThat(OriginalRepresentation.isPdfA(binary.getData())).isTrue();
      assertThat(preflight(binary.getData())).isTrue();
    }
  }

  // ---- corpus --------------------------------------------------------------------------------

  /** CH EMED prescriptions: IG example and those made from the CHMED16A prescriptions. */
  static Stream<String> prescriptions() {
    return Stream.of(
        "chemed/medication-prescription-example.json",
        "chmed16a/rx-basic.json",
        "chmed16a/rx-erezept-example.json",
        "chmed16a/rx-idtype4.json",
        "chmed16a/rx-invalid-fields.json",
        "chmed16a/rx-missing-hcperson.json");
  }

  /** CH EMED cards: IG example and those made from the CHMED16A plans. */
  static Stream<String> cards() {
    return Stream.of(
        "chemed/medication-card-example.json",
        "chmed16a/plan-basic.json",
        "chmed16a/plan-cycle.json",
        "chmed16a/plan-empty-meds.json",
        "chmed16a/plan-free-text-med.json",
        "chmed16a/plan-medical-data.json",
        "chmed16a/plan-non-ascii.json",
        "chmed16a/plan-pharmacode.json",
        "chmed16a/plan-reserve.json",
        "chmed16a/plan-taking-times.json");
  }

  /**
   * Fictitious GLN of the feature 001 fixtures; its check digit is wrong (CH EMED does not check
   * it).
   */
  static final String INVALID_GLN = "7601000000001";

  /** Fictitious GLN with a correct check digit, which CH EMED EPR requires (gln-modulus-10). */
  static final String VALID_GLN = "7601000000002";

  /**
   * The CH EMED document of a fixture, or null if feature 001 cannot convert it. The fictitious GLN
   * of the feature 001 fixtures is replaced by one with a valid check digit; {@link
   * #anInvalidGlnOfTheSourceFailsValidation} covers the original.
   */
  private static byte[] chEmed(String fixture) {
    byte[] document = chEmedAsIs(fixture);
    return document == null
        ? null
        : new String(document, StandardCharsets.UTF_8)
            .replace(INVALID_GLN, VALID_GLN)
            .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] chEmedAsIs(String fixture) {
    if (fixture.startsWith("chemed/")) {
      return Fixtures.bytes(fixture);
    }
    ConversionResult result =
        convert(
            FormatId.CHMED16A,
            FormatId.CH_EMED,
            Fixtures.bytes(fixture),
            TestOptions.withAuthorAndPatientId());
    return result.outcome() == Outcome.FAILED ? null : result.output().orElseThrow();
  }

  // ---- tests ---------------------------------------------------------------------------------

  @ParameterizedTest
  @MethodSource("prescriptions")
  void prescriptionsBecomeValidPreWithReferences(String fixture) throws Exception {
    byte[] chEmed = chEmed(fixture);
    if (chEmed == null) {
      return; // invalid input of feature 001, covered there
    }
    int missing =
        (int)
            resources(chEmed, MedicationRequest.class).stream()
                .filter(r -> r.getExtensionByUrl(TREATMENT_PLAN) == null)
                .count();

    ConversionResult without =
        convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, chEmed, ConversionOptions.defaults());
    if (missing > 0) {
      assertThat(without.outcome()).as(fixture).isEqualTo(Outcome.FAILED);
      assertThat(without.report().entries())
          .filteredOn(e -> e.severity() == Severity.ERROR)
          .extracting(ReportEntry::code)
          .as(fixture)
          .containsOnly(ReasonCode.MISSING_REQUIRED_DATA);
    }

    ConversionResult with =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            chEmed,
            TestOptions.withAuthorAndPatientId().withTreatmentPlanReferences(references(missing)));

    assertThat(errors(with)).as(fixture).isEmpty();
    assertThat(with.outcome()).as(fixture).isNotEqualTo(Outcome.FAILED);
    assertPdfAOriginalRepresentation(with.output().orElseThrow());
  }

  @ParameterizedTest
  @MethodSource("cards")
  void cardsWithAPersonAsAuthorDoNotBecomePmlc(String fixture) {
    byte[] chEmed = chEmed(fixture);
    if (chEmed == null) {
      return;
    }
    int entries =
        (int)
            resources(chEmed, MedicationStatement.class).stream()
                .filter(s -> s.getExtensionByUrl(TREATMENT_PLAN) == null)
                .count();

    ConversionResult result =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            chEmed,
            TestOptions.withAuthorAndPatientId().withTreatmentPlanReferences(references(entries)));

    assertThat(result.outcome()).as(fixture).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .filteredOn(e -> e.severity() == Severity.ERROR)
        .extracting(ReportEntry::code, ReportEntry::path)
        .as(fixture)
        .containsExactly(
            tuple(ReasonCode.MISSING_REQUIRED_DATA, "Bundle.entry[0].resource.author"));
  }

  static Stream<String> officialDocuments() {
    return Stream.of("PMLC", "PRE", "MTP").flatMap(t -> EprFixtures.official(t).stream());
  }

  @ParameterizedTest
  @MethodSource("officialDocuments")
  void officialExamplesGoBackToValidEpr(String name) throws Exception {
    ConversionResult chEmed =
        convert(
            FormatId.CH_EMED_EPR,
            FormatId.CH_EMED,
            EprFixtures.bytes("official/" + name),
            ConversionOptions.defaults());
    assertThat(chEmed.outcome()).as(name).isNotEqualTo(Outcome.FAILED);

    ConversionResult epr =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            chEmed.output().orElseThrow(),
            ConversionOptions.defaults());

    assertThat(errors(epr)).as(name).isEmpty();
    assertThat(epr.report().entries()).as(name).noneMatch(ReportEntry::clinicalLoss);
  }

  @Test
  void anInvalidGlnOfTheSourceFailsValidation() {
    byte[] chEmed = chEmedAsIs("chmed16a/rx-basic.json");
    int requests = resources(chEmed, MedicationRequest.class).size();

    ConversionResult result =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            chEmed,
            ConversionOptions.defaults().withTreatmentPlanReferences(references(requests)));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(result.report().entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.FHIR_VALIDATION)
                    && e.phase() == Phase.VALIDATE_OUTPUT
                    && e.message().contains("gln-modulus-10"));
  }

  @Test
  void aWrongNumberOfReferencesIsAMismatch() {
    byte[] chEmed = chEmed("chmed16a/rx-basic.json");
    int requests = resources(chEmed, MedicationRequest.class).size();

    ConversionResult result =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            chEmed,
            ConversionOptions.defaults().withTreatmentPlanReferences(references(requests + 1)));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .contains(ReasonCode.TREATMENT_PLAN_REFERENCES_MISMATCH);
  }

  @Test
  void aSuppliedPdfAIsCarriedExactlyAndANonPdfAIsReported() throws Exception {
    byte[] chEmed = chEmed("chmed16a/rx-basic.json");
    int requests = resources(chEmed, MedicationRequest.class).size();
    byte[] pdfA =
        OriginalRepresentationRenderer.renderPdfA(ChEmedJson.parse(chEmed), new ReportCollector());
    ConversionOptions options =
        ConversionOptions.defaults().withTreatmentPlanReferences(references(requests));

    ConversionResult supplied =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            chEmed,
            options.withOriginalRepresentation(pdfA));

    assertThat(errors(supplied)).isEmpty();
    assertThat(resources(supplied.output().orElseThrow(), Binary.class))
        .singleElement()
        .satisfies(b -> assertThat(b.getData()).isEqualTo(pdfA));

    byte[] plain =
        OriginalRepresentationRenderer.render(ChEmedJson.parse(chEmed), new ReportCollector());
    ConversionResult notPdfA =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            chEmed,
            options.withOriginalRepresentation(plain).withStrict(true));

    assertThat(notPdfA.outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(notPdfA.report().entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT) && e.severity() == Severity.WARNING);
    assertPdfAOriginalRepresentation(notPdfA.output().orElseThrow());
  }

  @Test
  void strictModeFailsOnAClinicalUnsupportedElement() {
    byte[] chEmed = chEmed("chmed16a/rx-basic.json");
    Bundle bundle = ChEmedJson.parse(chEmed);
    bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(MedicationRequest.class::isInstance)
        .map(MedicationRequest.class::cast)
        .forEach(r -> r.getStatusReason().setText("paused"));
    byte[] changed = ChEmedJson.serialize(bundle);
    int requests = resources(changed, MedicationRequest.class).size();

    ConversionResult result =
        convert(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            changed,
            ConversionOptions.defaults()
                .withTreatmentPlanReferences(references(requests))
                .withStrict(true));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .anyMatch(e -> e.clinicalLoss() && e.path().endsWith(".statusReason"));
  }

  @Test
  void aDirectRequestFromChmed16aNamesThePathThroughChEmed() {
    ConversionResult result =
        convert(
            FormatId.CHMED16A,
            FormatId.CH_EMED_EPR,
            Fixtures.bytes("chmed16a/rx-basic.json"),
            TestOptions.withAuthorAndPatientId());

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_CONVERSION);
              assertThat(e.message()).contains("ch-emed");
            });
  }

  @Test
  void anOutputThatFailsValidationIsNotReturned() {
    ChEmedValidator rejecting =
        new ChEmedValidator() {
          private final ChEmedValidator real = ChEmedAssertions.validator();

          @Override
          public List<ReportEntry> validate(Bundle bundle, Phase phase) {
            if (phase == Phase.VALIDATE_OUTPUT) {
              return List.of(
                  ReportEntry.error(ReasonCode.FHIR_VALIDATION, phase, "Bundle", "stub error"));
            }
            return real.validate(bundle, phase);
          }

          @Override
          public String pinnedSpecification() {
            return real.pinnedSpecification();
          }
        };
    Converter stubbed = Converters.create(rejecting);
    byte[] chEmed = chEmed("chmed16a/rx-basic.json");
    int requests = resources(chEmed, MedicationRequest.class).size();

    ConversionResult result =
        stubbed.convert(
            new ConversionRequest(
                FormatId.CH_EMED,
                FormatId.CH_EMED_EPR,
                chEmed,
                ConversionOptions.defaults().withTreatmentPlanReferences(references(requests))));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
  }

  private static final String TREATMENT_PLAN =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-treatmentplan";
}
