package ch.emed.converter.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReportEntry;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T009 (feature 004, research R3, FR-013): loading the CH EMED EPR package does not change the
 * validation of general CH EMED documents. The full corpus of features 001–003 is covered by their
 * expected-output tests, which run with the package loaded.
 */
class EprPackageRegressionTest {

  private static final String EPR = "ch.fhir.ig.ch-emed-epr";

  private static HapiChEmedValidator withEpr;
  private static HapiChEmedValidator withoutEpr;

  @BeforeAll
  static void createValidators() {
    withEpr = new HapiChEmedValidator();
    withoutEpr = new HapiChEmedValidator(ref -> !ref.name().equals(EPR));
  }

  @Test
  void eprPackageIsPinnedAndLoaded() {
    assertThat(PackageLoader.loadManifest("packages"))
        .filteredOn(ref -> ref.name().equals(EPR))
        .singleElement()
        .satisfies(ref -> assertThat(ref.version()).isEqualTo("3.0.0"));
  }

  @Test
  void manifestRootStaysChEmed() {
    assertThat(withEpr.pinnedSpecification()).isEqualTo("ch.fhir.ig.ch-emed#6.0.0");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"Bundle-2-7-MedicationCard.json", "Bundle-2-6-MedicationPrescription.json"})
  void chEmedDocumentsValidateAsBefore(String name) throws IOException {
    for (Phase phase : List.of(Phase.VALIDATE_INPUT, Phase.VALIDATE_OUTPUT)) {
      List<ReportEntry> expected = withoutEpr.validate(example(name), phase);
      List<ReportEntry> actual = withEpr.validate(example(name), phase);

      assertThat(actual).isEqualTo(expected);
    }
  }

  private static org.hl7.fhir.r4.model.Bundle example(String name) throws IOException {
    try (InputStream in = EprPackageRegressionTest.class.getResourceAsStream("/examples/" + name)) {
      return ChEmedJson.parse(in.readAllBytes());
    }
  }
}
