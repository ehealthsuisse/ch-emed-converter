package ch.emed.converter.chmed23a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.io.TransmissionFormat;
import ch.emed.converter.chmed23a.testsupport.TestOptions;
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
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** T043, T044 (US2): CHMED23A prescriptions to CH EMED. */
class Chmed23aPrescriptionToChEmedIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(byte[] input) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CHMED23A, FormatId.CH_EMED, input, TestOptions.withAuthorAndPatientId()));
  }

  private static List<ReportEntry> errors(ConversionResult result) {
    return result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  private static List<MedicationRequest> requests(byte[] output) {
    Bundle bundle = ChEmedJson.parse(output);
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(MedicationRequest.class::isInstance)
        .map(MedicationRequest.class::cast)
        .toList();
  }

  @Test
  void example4ConvertsWithTheTaperingSplitIntoFourRequests() {
    ConversionResult result = convert(Fixtures.bytes("chmed23a/official/Example4.json"));

    assertThat(errors(result)).isEmpty();
    byte[] output = result.output().orElseThrow();
    ChEmedAssertions.assertValid(output);
    List<MedicationRequest> requests = requests(output);
    assertThat(requests).hasSize(7);
    assertThat(requests.subList(0, 4))
        .allSatisfy(
            r -> {
              assertThat(r.getDispenseRequest().getValidityPeriod().getEnd()).isNotNull();
              assertThat(r.getMedicationReference().getReference()).isEqualTo("#med");
            });
    assertThat(requests.get(3).getDispenseRequest().getNumberOfRepeatsAllowed()).isZero();
    List<String> entries =
        result.report().entries().stream().map(e -> e.code() + " " + e.path()).toList();
    assertThat(entries)
        .contains(
            "DERIVED_VALUE /meds/0/pos",
            "UNMAPPABLE_ELEMENT /meds/0/autoMed",
            "UNMAPPABLE_ELEMENT /patient/mData");
  }

  @Test
  void payloadChunksAndImageGiveTheSameDocument() throws Exception {
    byte[] pretty = Fixtures.bytes("chmed23a/official/Example4.json");
    byte[] compact = MAPPER.writeValueAsBytes(MAPPER.readTree(pretty));
    String payload = new String(TransmissionFormat.encode(compact), StandardCharsets.US_ASCII);
    String data = payload.substring(TransmissionFormat.PREFIX.length());
    int half = data.length() / 2;
    String chunks =
        "CHMED23A.2/2." + data.substring(half) + "\nCHMED23A.1/2." + data.substring(0, half);

    byte[] expected = convert(pretty).output().orElseThrow();
    for (byte[] input :
        List.of(
            compact,
            payload.getBytes(StandardCharsets.US_ASCII),
            chunks.getBytes(StandardCharsets.US_ASCII),
            QrImageWriter.write(payload))) {
      assertThat(convert(input).output()).contains(expected);
    }
  }

  @Test
  void aPrescriptionByThePatientIsRefused() throws Exception {
    ObjectNode root =
        (ObjectNode) MAPPER.readTree(Fixtures.bytes("chmed23a/official/Example4.json"));
    root.put("auth", 2);
    ConversionResult result = convert(MAPPER.writeValueAsBytes(root));
    assertThat(errors(result))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.CHMED_INVALID_FIELD);
              assertThat(e.path()).isEqualTo("/auth");
            });
  }

  @Test
  void aMedicamentWithoutPosologyGetsTheNote() throws Exception {
    ObjectNode root =
        (ObjectNode) MAPPER.readTree(Fixtures.bytes("chmed23a/official/Example4.json"));
    ((ObjectNode) root.at("/meds/3")).remove("pos");
    ConversionResult result = convert(MAPPER.writeValueAsBytes(root));
    assertThat(errors(result)).isEmpty();
    ChEmedAssertions.assertValid(result.output().orElseThrow());
    assertThat(result.report().entries())
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE);
              assertThat(e.path()).isEqualTo("/meds/3/pos");
            });
  }
}
