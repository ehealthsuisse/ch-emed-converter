package ch.emed.converter.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.FormatId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportJsonWriterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static ConversionReport sampleReport() {
    List<ReportEntry> unordered =
        List.of(
            ReportEntry.of(
                    Severity.ERROR,
                    ReasonCode.FHIR_VALIDATION,
                    Phase.VALIDATE_OUTPUT,
                    "Bundle.entry[0]",
                    "Validation \"message\" with quotes\nand newline")
                .withClinicalLoss(false),
            ReportEntry.of(
                    Severity.WARNING,
                    ReasonCode.DOSAGE_FREE_TEXT_FALLBACK,
                    Phase.MAP,
                    "/Medicaments/1/Pos/0",
                    "Posology carried as free text")
                .withClinicalLoss(true)
                .withTargetPath("MedicationStatement.dosage[0].text"),
            ReportEntry.of(
                Severity.INFORMATION,
                ReasonCode.UNKNOWN_FIELD_IGNORED,
                Phase.PARSE,
                "/PFields/0",
                "Private field ignored"),
            ReportEntry.of(
                Severity.WARNING,
                ReasonCode.UNMAPPABLE_ELEMENT,
                Phase.MAP,
                "/Medicaments/0/Unit",
                "Unit not representable"));
    return ConversionReport.of(
        "1.0.0-TEST",
        FormatId.CHMED16A,
        FormatId.CH_EMED,
        Map.of("CH_EMED", "ch.fhir.ig.ch-emed#6.0.0", "CHMED16A", "CHMED16A1"),
        Outcome.FAILED,
        unordered);
  }

  @Test
  void serializedReportValidatesAgainstSchema() throws Exception {
    byte[] json = ReportJsonWriter.write(sampleReport());

    JsonSchema schema;
    try (InputStream in =
        getClass().getResourceAsStream("/schemas/conversion-report.schema.json")) {
      schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in);
    }
    Set<ValidationMessage> errors = schema.validate(MAPPER.readTree(json));
    assertThat(errors).isEmpty();
  }

  @Test
  void entriesAreOrderedByPhaseThenPath() throws Exception {
    JsonNode entries = MAPPER.readTree(ReportJsonWriter.write(sampleReport())).get("entries");

    assertThat(entries).hasSize(4);
    assertThat(entries.get(0).get("phase").asText()).isEqualTo("PARSE");
    assertThat(entries.get(1).get("path").asText()).isEqualTo("/Medicaments/0/Unit");
    assertThat(entries.get(2).get("path").asText()).isEqualTo("/Medicaments/1/Pos/0");
    assertThat(entries.get(3).get("phase").asText()).isEqualTo("VALIDATE_OUTPUT");
  }

  @Test
  void escapesSpecialCharacters() throws Exception {
    JsonNode entries = MAPPER.readTree(ReportJsonWriter.write(sampleReport())).get("entries");
    assertThat(entries.get(3).get("message").asText())
        .isEqualTo("Validation \"message\" with quotes\nand newline");
  }

  @Test
  void sameReportSerializesToIdenticalBytes() {
    byte[] first = ReportJsonWriter.write(sampleReport());
    byte[] second = ReportJsonWriter.write(sampleReport());
    assertThat(new String(first, StandardCharsets.UTF_8))
        .isEqualTo(new String(second, StandardCharsets.UTF_8));
  }
}
