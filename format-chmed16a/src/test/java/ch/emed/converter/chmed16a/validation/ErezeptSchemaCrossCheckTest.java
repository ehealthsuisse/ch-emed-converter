package ch.emed.converter.chmed16a.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.Chmed16aJsonReader;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.ErezeptSchema;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cross-check of {@link ErezeptPrescriptionRules} against the JSON schema published by E-Rezept
 * Schweiz: both must agree on the revision 3 prescription fixtures.
 */
class ErezeptSchemaCrossCheckTest {

  @ParameterizedTest
  @CsvSource({"rx-basic.json, true", "rx-idtype4.json, false", "rx-invalid-fields.json, false"})
  void rulesAndSchemaAgree(String fixture, boolean valid) {
    byte[] json = Fixtures.bytes("chmed16a/" + fixture);
    ReportCollector report = new ReportCollector();
    Medication med = Chmed16aJsonReader.read(json, ConversionOptions.defaults(), report);
    ErezeptPrescriptionRules.validate(med, report, Phase.VALIDATE_OUTPUT);

    boolean rulesOk = report.entries().stream().noneMatch(e -> e.severity() == Severity.ERROR);

    assertThat(ErezeptSchema.validate(json).isEmpty()).as("schema: " + fixture).isEqualTo(valid);
    assertThat(rulesOk).as("rules: " + fixture).isEqualTo(valid);
  }

  @Test
  void theSchemaAcceptsTheMinimalPrescriptionOfTheDocumentation() {
    assertThat(ErezeptSchema.validate(Fixtures.bytes("chmed16a/rx-erezept-example.json")))
        .isEmpty();
  }

  @Test
  void theSchemaRequiresHealthcareObjectsFromRevisionTwo() {
    byte[] json =
        Fixtures.text("chmed16a/rx-erezept-example.json")
            .replace("\"MedType\":3", "\"MedType\":3,\"rev\":3")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    assertThat(ErezeptSchema.validate(json)).isNotEmpty();
  }
}
