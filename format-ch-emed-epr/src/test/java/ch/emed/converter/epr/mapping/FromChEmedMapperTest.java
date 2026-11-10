package ch.emed.converter.epr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.TreatmentPlanReference;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.Test;

/**
 * T028 (feature 004): CH EMED → CH EMED EPR (M-EPR-21 to M-EPR-27) for a prescription, one fixture
 * per rule, byte for byte against {@code fixtures/expected/from-chemed-prescription.json} (the
 * rules that leave this prescription as the base output) or {@code from-chemed-M-EPR-24.json} and
 * with the report entries of the rule. Without a validator; {@code ChEmedToEprIT} validates.
 * Regenerate the expected files with {@code -Depr.regenerate=true}.
 */
class FromChEmedMapperTest {

  private static final FromChEmedMapper MAPPER = new FromChEmedMapper(UnsupportedElements.load());
  private static final String DOC = "urn:uuid:11111111-1111-4111-8111-111111111111";
  private static final String MS = "urn:uuid:22222222-2222-4222-8222-222222222222";

  /** The CH EMED IG prescription example (it carries its treatment-plan reference). */
  private static Bundle prescription() {
    return ChEmedJson.parse(Fixtures.bytes("chemed/medication-prescription-example.json"));
  }

  private static MedicationRequest request(Bundle bundle) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(MedicationRequest.class::isInstance)
        .map(MedicationRequest.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private static ReportCollector map(
      String rule, String expectedName, Consumer<Bundle> change, ConversionOptions options)
      throws IOException {
    Bundle input = prescription();
    change.accept(input);
    ReportCollector report = new ReportCollector();

    Bundle epr = MAPPER.map(input, EprDocumentType.PRE, new MappingContext(options, report));

    assertThat(epr).as(rule + " " + report.entries()).isNotNull();
    byte[] output = ChEmedJson.serialize(epr);
    String expected = "expected/from-chemed-" + expectedName + ".json";
    if (Boolean.getBoolean("epr.regenerate")) {
      Path file = Path.of("src/test/resources/fixtures/" + expected);
      Files.createDirectories(file.getParent());
      Files.write(file, output);
    }
    assertThat(new String(output, StandardCharsets.UTF_8))
        .as(rule)
        .isEqualTo(new String(EprFixtures.bytes(expected), StandardCharsets.UTF_8));
    return report;
  }

  private static List<String> messages(ReportCollector report, String rule) {
    return report.entries().stream()
        .map(ReportEntry::message)
        .filter(m -> m.contains(rule))
        .toList();
  }

  @Test
  void m21ProfilesBecomeEprProfiles() throws IOException {
    ReportCollector report = map("M-EPR-21", "prescription", b -> {}, ConversionOptions.defaults());

    assertThat(messages(report, "M-EPR-01")).isNotEmpty();
  }

  @Test
  void m22UnsupportedElementsAreRemoved() throws IOException {
    ReportCollector report =
        map(
            "M-EPR-22",
            "prescription",
            b -> request(b).getStatusReason().setText("paused"),
            ConversionOptions.defaults());

    assertThat(report.entries())
        .anyMatch(e -> e.path().endsWith(".statusReason") && e.clinicalLoss());
  }

  @Test
  void m24ReferencesComeFromTheOption() throws IOException {
    ReportCollector report =
        map(
            "M-EPR-24",
            "M-EPR-24",
            b ->
                b.getEntry().stream()
                    .map(Bundle.BundleEntryComponent::getResource)
                    .filter(DomainResource.class::isInstance)
                    .map(DomainResource.class::cast)
                    .forEach(
                        r ->
                            r.getExtension()
                                .removeIf(e -> e.getUrl().equals(RequiredData.EXT_TREATMENT_PLAN))),
            ConversionOptions.defaults()
                .withTreatmentPlanReferences(List.of(new TreatmentPlanReference(DOC, MS))));

    assertThat(report.entries())
        .anyMatch(
            e ->
                e.message().startsWith("Treatment-plan reference taken from the options")
                    || e.message()
                        .startsWith("Treatment-plan reference taken from the conversion options"));
  }

  @Test
  void m25AnOriginalRepresentationThatIsNotPdfAIsRendered() throws IOException {
    ReportCollector report = map("M-EPR-25", "prescription", b -> {}, ConversionOptions.defaults());

    assertThat(report.entries()).anyMatch(e -> e.message().contains("PDF/A"));
  }

  @Test
  void m27SubstitutionAllowedIsLeftOut() throws IOException {
    ReportCollector report = map("M-EPR-27", "prescription", b -> {}, ConversionOptions.defaults());

    assertThat(report.entries()).anyMatch(e -> e.path().endsWith(".substitution"));
  }
}
