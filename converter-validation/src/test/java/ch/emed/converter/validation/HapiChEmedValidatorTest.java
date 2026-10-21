package ch.emed.converter.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.support.IValidationSupport;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HapiChEmedValidatorTest {

  private static HapiChEmedValidator validator;

  @BeforeAll
  static void createValidator() {
    validator = new HapiChEmedValidator();
  }

  private static Bundle example(String name) throws IOException {
    try (InputStream in = HapiChEmedValidatorTest.class.getResourceAsStream("/examples/" + name)) {
      return ChEmedJson.parse(in.readAllBytes());
    }
  }

  private static List<ReportEntry> errors(List<ReportEntry> entries) {
    return entries.stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  @Test
  void officialMedicationCardExampleHasNoErrors() throws IOException {
    List<ReportEntry> entries =
        validator.validate(example("Bundle-2-7-MedicationCard.json"), Phase.VALIDATE_OUTPUT);

    assertThat(errors(entries)).isEmpty();
  }

  @Test
  void officialMedicationPrescriptionExampleHasNoErrors() throws IOException {
    List<ReportEntry> entries =
        validator.validate(
            example("Bundle-2-6-MedicationPrescription.json"), Phase.VALIDATE_OUTPUT);

    assertThat(errors(entries)).isEmpty();
  }

  @Test
  void bundleWithoutCompositionYieldsErrorsInRequestedPhase() throws IOException {
    Bundle broken = example("Bundle-2-7-MedicationCard.json");
    broken.getEntry().removeIf(e -> e.getResource().getResourceType() == ResourceType.Composition);

    List<ReportEntry> asInput = validator.validate(broken, Phase.VALIDATE_INPUT);

    assertThat(errors(asInput)).isNotEmpty();
    assertThat(asInput)
        .allSatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.FHIR_VALIDATION);
              assertThat(e.phase()).isEqualTo(Phase.VALIDATE_INPUT);
            });
  }

  @Test
  void terminologyLimitationsAreReportedAsInformation() throws IOException {
    List<ReportEntry> entries =
        validator.validate(example("Bundle-2-7-MedicationCard.json"), Phase.VALIDATE_OUTPUT);

    assertThat(entries)
        .filteredOn(e -> e.severity() == Severity.WARNING)
        .noneMatch(
            e ->
                HapiChEmedValidator.TERMINOLOGY_LIMITATION_IDS.stream()
                    .anyMatch(id -> e.message().startsWith(id + ":")));
    assertThat(entries).anyMatch(e -> e.severity() == Severity.INFORMATION);
  }

  @Test
  void noRemoteTerminologyServerIsConfigured() {
    for (IValidationSupport support : validator.supportChain().getValidationSupports()) {
      assertThat(support).isNotInstanceOf(RemoteTerminologyServiceValidationSupport.class);
    }
  }

  @Test
  void exposesPinnedSpecification() {
    assertThat(validator.pinnedSpecification()).isEqualTo("ch.fhir.ig.ch-emed#6.0.0");
  }
}
