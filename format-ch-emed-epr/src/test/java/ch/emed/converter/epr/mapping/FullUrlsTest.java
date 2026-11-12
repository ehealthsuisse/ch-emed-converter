package ch.emed.converter.epr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

/** Feature 005 (rule M-EPR-05, decision 2026-11-10): urn:uuid full URLs for CH EMED EPR. */
class FullUrlsTest {

  private static Bundle load(String fixture) {
    return ChEmedJson.parse(EprFixtures.bytes(fixture));
  }

  private static Bundle apply(Bundle bundle, EprDocumentType type, ReportCollector report) {
    return new FullUrls()
        .apply(bundle, type, new MappingContext(ConversionOptions.defaults(), report));
  }

  private static <T> T first(Bundle bundle, Class<T> type) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void restfulFullUrlsBecomeUrnUuidsAndReferencesFollow() {
    Bundle input = load("chemed/Bundle-PharmaceuticalAdvice-ChangeDosage.json");
    ReportCollector report = new ReportCollector();

    Bundle output = apply(input, EprDocumentType.PADV, report);

    assertThat(output.getEntry())
        .allSatisfy(e -> assertThat(e.getFullUrl()).matches("urn:uuid:[0-9a-f-]{36}"));
    // the changed statement is found under the urn:uuid of its identifier (EPR padv-mtp-same-id)
    MedicationStatement changed = first(output, MedicationStatement.class);
    Reference ref =
        (Reference)
            first(output, Observation.class)
                .getExtensionByUrl(ReferenceConsistency.STATEMENT_CHANGED)
                .getValue();
    assertThat(ref.getReference()).isEqualTo(changed.getIdentifierFirstRep().getValue());
    // every reference of the Composition resolves to an entry
    String json = new String(ChEmedJson.serialize(output), StandardCharsets.UTF_8);
    assertThat(json)
        .doesNotContain("\"reference\":\"http")
        .doesNotContain("\"reference\":\"Patient/");
    assertThat(report.entries())
        .hasSize(input.getEntry().size())
        .allSatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE);
              assertThat(e.path()).endsWith(".fullUrl");
              assertThat(e.message()).doesNotContain("http").doesNotContain("urn:uuid:");
            });
  }

  @Test
  void theResultIsDeterministic() {
    byte[] first =
        ChEmedJson.serialize(
            apply(
                load("chemed/Bundle-2-1-MedicationList.json"),
                EprDocumentType.PML,
                new ReportCollector()));
    byte[] second =
        ChEmedJson.serialize(
            apply(
                load("chemed/Bundle-2-1-MedicationList.json"),
                EprDocumentType.PML,
                new ReportCollector()));

    assertThat(first).isEqualTo(second);
  }

  @Test
  void urnUuidFullUrlsAreKept() {
    Bundle input = load("official/Bundle-BundlePml1.json");
    ReportCollector report = new ReportCollector();

    Bundle output = apply(input, EprDocumentType.PML, report);

    assertThat(output).isSameAs(input);
    assertThat(report.entries()).isEmpty();
  }
}
