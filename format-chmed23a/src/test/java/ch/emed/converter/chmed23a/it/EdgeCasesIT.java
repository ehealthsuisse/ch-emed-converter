package ch.emed.converter.chmed23a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.io.TransmissionFormat;
import ch.emed.converter.chmed23a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.qr.QrImageWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** T033: edge cases of the spec on the read side. */
class EdgeCasesIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(FormatId source, FormatId target, byte[] input) {
    return converter.convert(
        new ConversionRequest(source, target, input, TestOptions.withAuthorAndPatientId()));
  }

  private static ConversionResult convert(byte[] input) {
    return convert(FormatId.CHMED23A, FormatId.CH_EMED, input);
  }

  private static ReportEntry singleError(ConversionResult result) {
    List<ReportEntry> errors =
        result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
    assertThat(errors).hasSize(1);
    assertThat(result.output()).isEmpty();
    return errors.get(0);
  }

  private static byte[] example(String name, java.util.function.Consumer<ObjectNode> change)
      throws Exception {
    ObjectNode root = (ObjectNode) MAPPER.readTree(Fixtures.bytes("chmed23a/official/" + name));
    change.accept(root);
    return MAPPER.writeValueAsBytes(root);
  }

  @Test
  void polymedicationCheckIsRefusedNamingTheSupportedTypes() throws Exception {
    ReportEntry e = singleError(convert(example("Example1.json", r -> r.put("medType", 2))));
    assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_DOCUMENT_TYPE);
    assertThat(e.message()).contains("MP").contains("Rx");
  }

  @Test
  void chmed16aDeclaredAsChmed23aNamesTheOtherSource() {
    for (byte[] input :
        List.of(
            "{\"Patient\":{\"FName\":\"A\"},\"Medicaments\":[]}".getBytes(StandardCharsets.UTF_8),
            "CHMED16A1H4sIAAAA".getBytes(StandardCharsets.US_ASCII))) {
      ReportEntry e = singleError(convert(input));
      assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_VERSION);
      assertThat(e.message()).contains("use source chmed16a");
    }
  }

  @Test
  void fhirInputIsAParseError() {
    ReportEntry e =
        singleError(convert("{\"resourceType\":\"Bundle\"}".getBytes(StandardCharsets.UTF_8)));
    assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR);
    assertThat(e.message()).contains("ch-emed");
  }

  @Test
  void aChunkInAnImageIsRefused() throws Exception {
    String payload =
        new String(
            TransmissionFormat.encode(
                MAPPER.writeValueAsBytes(
                    MAPPER.readTree(Fixtures.bytes("chmed23a/official/Example3.json")))),
            StandardCharsets.US_ASCII);
    String chunk = "CHMED23A.1/2." + payload.substring(9, 400);
    ReportEntry e = singleError(convert(QrImageWriter.write(chunk)));
    assertThat(e.code()).isEqualTo(ReasonCode.CHUNK_INVALID);
    assertThat(e.message()).doesNotContain(payload.substring(9, 40));
  }

  @Test
  void nonConformingInputNamesPathsNotValues() throws Exception {
    ConversionResult result =
        convert(example("Example1.json", r -> ((ObjectNode) r.at("/patient")).put("bdt", "Alice")));
    ReportEntry e = singleError(result);
    assertThat(e.code()).isEqualTo(ReasonCode.CHMED_INVALID_FIELD);
    assertThat(e.path()).isEqualTo("/patient/bdt");
    assertThat(e.message()).doesNotContain("Alice");
  }

  @Test
  void directConversionsToOtherFormatsAreRefusedNamingTheHub() {
    for (FormatId target : List.of(FormatId.CHMED16A, FormatId.CH_EMED_EPR)) {
      ConversionResult result =
          convert(FormatId.CHMED23A, target, Fixtures.bytes("chmed23a/official/Example1.json"));
      assertThat(result.output()).isEmpty();
      assertThat(result.report().entries())
          .anySatisfy(
              e -> {
                assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_CONVERSION);
                assertThat(e.message()).containsIgnoringCase("CH_EMED");
              });
    }
  }

  @Test
  void nonAsciiTextIsPreserved() throws Exception {
    byte[] input =
        example(
            "Example1.json",
            r -> {
              ((ObjectNode) r.at("/patient")).put("fName", "Zoë").put("lName", "Müller-Frère");
              ((ObjectNode) r.at("/meds/0")).put("rsn", "Douleur à l'épaule, dolore");
            });
    ConversionResult result = convert(input);
    String output = new String(result.output().orElseThrow(), StandardCharsets.UTF_8);
    assertThat(output).contains("Zoë", "Müller-Frère", "Douleur à l'épaule, dolore");
  }

  @Test
  void anInvalidSystemIdentifierIsNotRepaired() {
    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FormatId.CHMED23A,
                FormatId.CH_EMED,
                Fixtures.bytes("chmed23a/official/Example3.json"),
                ConversionOptions.defaults()));
    assertThat(result.report().entries())
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.path()).isEqualTo("/patient/ids/0/sId");
            })
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA);
              assertThat(e.path()).isEqualTo("/patient/ids");
            });
  }

  @Test
  void unknownFieldsAreReportedAndIgnored() throws Exception {
    ConversionResult result = convert(example("Example1.json", r -> r.put("vendorField", "x")));
    assertThat(result.output()).isPresent();
    assertThat(result.report().entries())
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNKNOWN_FIELD_IGNORED);
              assertThat(e.path()).isEqualTo("/vendorField");
            });
  }

  @Test
  void aUuidDocumentIdIsKept() throws Exception {
    String uuid = "0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e23";
    Bundle bundle =
        ChEmedJson.parse(
            convert(example("Example1.json", r -> r.put("id", uuid))).output().orElseThrow());
    assertThat(bundle.getIdentifier().getValue()).isEqualTo("urn:uuid:" + uuid);
    assertThat(bundle.getEntry().get(1).getResource()).isInstanceOf(Patient.class);
  }
}
