package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.epr.EprReasonCode;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T014/T021/T044 (feature 004): the official PMLC, PRE and MTP examples convert to CH EMED by the
 * profile change alone and validate as CH EMED (SC-001, research F3).
 */
class OfficialExamplesToChEmedIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  static Stream<String> examples() {
    return Stream.of("PMLC", "PRE", "MTP").flatMap(t -> EprFixtures.official(t).stream());
  }

  private static ConversionResult convert(byte[] input) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CH_EMED_EPR, FormatId.CH_EMED, input, ConversionOptions.defaults()));
  }

  private static ConversionResult convert(String name) {
    return convert(EprFixtures.bytes("official/" + name));
  }

  private static List<ReportEntry> errors(ConversionResult result) {
    return result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  @ParameterizedTest
  @MethodSource("examples")
  void convertsToValidChEmedByTheProfileChangeAlone(String name) {
    ConversionResult result = convert(name);

    assertThat(errors(result)).as(name).isEmpty();
    assertThat(result.outcome()).isNotEqualTo(Outcome.FAILED);
    Bundle input = ChEmedJson.parse(EprFixtures.bytes("official/" + name));
    Bundle output = ChEmedJson.parse(result.output().orElseThrow());
    assertThat(output.getEntry()).hasSameSizeAs(input.getEntry());
    assertThat(allProfiles(output)).noneMatch(p -> p.contains("/ch-emed-epr/"));
    // apart from meta.profile, every resource is unchanged
    for (int i = 0; i < input.getEntry().size(); i++) {
      Resource in = input.getEntry().get(i).getResource().copy();
      Resource out = output.getEntry().get(i).getResource().copy();
      in.getMeta().setProfile(List.of());
      out.getMeta().setProfile(List.of());
      assertThat(out.equalsDeep(in)).as(name + " entry " + i).isTrue();
    }
    assertThat(result.report().entries())
        .filteredOn(e -> e.code().equals(EprReasonCode.PROFILE_CHANGED))
        .isNotEmpty()
        .allMatch(e -> e.severity() == Severity.INFORMATION);
  }

  @Test
  void emptyCardBecomesACardWithoutMedications() {
    ConversionResult result = convert("Bundle-DocumentUCSF1CARAPMP004PMLCEmpty.json");

    assertThat(errors(result)).isEmpty();
    Bundle output = ChEmedJson.parse(result.output().orElseThrow());
    assertThat(output.getEntry())
        .noneMatch(e -> e.getResource().getResourceType() == ResourceType.MedicationStatement);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"Bundle-DocumentDis1.json", "Bundle-DocumentPadv1.json", "Bundle-BundlePml1.json"})
  void laterDocumentTypesAreRefused(String name) {
    ConversionResult result = convert(name);

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .containsExactly(ReasonCode.UNSUPPORTED_DOCUMENT_TYPE);
  }

  @Test
  void generalChEmedDeclaredAsEprGetsAHint() {
    byte[] general = convert("Bundle-BundlePmlc1.json").output().orElseThrow();

    ConversionResult result = convert(general);

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR);
              assertThat(e.message()).contains("use source ch-emed");
            });
  }

  @Test
  void invalidEprInputFailsWithTheFindingsAndNoOutput() {
    Bundle broken = ChEmedJson.parse(EprFixtures.bytes("official/Bundle-BundlePmlc1.json"));
    broken.getEntry().removeIf(e -> e.getResource().getResourceType() == ResourceType.Patient);

    ConversionResult result = convert(ChEmedJson.serialize(broken));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(errors(result))
        .isNotEmpty()
        .allMatch(e -> e.code().equals(ReasonCode.FHIR_VALIDATION))
        .allMatch(e -> e.phase() == Phase.VALIDATE_INPUT);
  }

  @Test
  void notADocumentBundleIsAParseError() {
    ConversionResult result =
        convert("{\"resourceType\":\"Bundle\",\"type\":\"collection\"}".getBytes());

    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .containsExactly(ReasonCode.PARSE_ERROR);
  }

  private static List<String> allProfiles(Bundle bundle) {
    return Stream.concat(
            Stream.of((Resource) bundle),
            bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource))
        .flatMap(r -> r.getMeta().getProfile().stream())
        .map(CanonicalType::getValue)
        .toList();
  }
}
