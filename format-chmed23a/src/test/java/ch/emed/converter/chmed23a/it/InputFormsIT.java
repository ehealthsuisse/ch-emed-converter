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
import ch.emed.converter.qr.QrImageWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T032 (US1.2, SC-003): plain JSON, payload, shuffled chunks and QR image of the same plan give
 * byte-identical CH EMED documents and reports.
 */
class InputFormsIT {

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

  private static List<String> chunks(String payload, int n) {
    String data = payload.substring(TransmissionFormat.PREFIX.length());
    int size = (data.length() + n - 1) / n;
    List<String> chunks = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      chunks.add(
          "CHMED23A."
              + (i + 1)
              + "/"
              + n
              + "."
              + data.substring(
                  Math.min(i * size, data.length()), Math.min((i + 1) * size, data.length())));
    }
    return chunks;
  }

  private static String report(ConversionResult result) {
    return result.report().entries().toString();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Example1.json", "Example2.json", "Example3.json"})
  void everyFormGivesTheSameDocument(String example) throws Exception {
    byte[] pretty = Fixtures.bytes("chmed23a/official/" + example);
    byte[] compact = MAPPER.writeValueAsBytes(MAPPER.readTree(pretty));
    String payload = new String(TransmissionFormat.encode(compact), StandardCharsets.US_ASCII);

    ConversionResult reference = convert(pretty);
    byte[] expected = reference.output().orElseThrow();
    ChEmedAssertions.assertValid(expected);

    List<byte[]> inputs = new ArrayList<>();
    inputs.add(compact);
    inputs.add(payload.getBytes(StandardCharsets.US_ASCII));
    for (int n : new int[] {2, 3, 4}) {
      List<String> chunks = new ArrayList<>(chunks(payload, n));
      Collections.reverse(chunks);
      inputs.add(String.join("\n", chunks).getBytes(StandardCharsets.US_ASCII));
    }
    inputs.add(QrImageWriter.write(payload));

    for (byte[] input : inputs) {
      ConversionResult result = convert(input);
      assertThat(result.output()).as(example).contains(expected);
      assertThat(report(result)).as(example).isEqualTo(report(reference));
    }
  }
}
