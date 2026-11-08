package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.Chmed16aPayloadEncoder;
import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.qr.QrImageWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T054 (feature 003, SC-001): every CHMED16A JSON fixture of feature 001, plans and prescriptions,
 * gives the same CH EMED document and report through a QR image as from plain JSON, with the same
 * options; this includes the failures. The images are made in the test with the converter's encoder
 * and writer (prescriptions too, which the library refuses to output as image).
 */
class CorpusImageIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  static Stream<String> corpus() {
    List<String> all = new ArrayList<>(Fixtures.list("chmed16a", ".json"));
    all.addAll(Fixtures.list("chmed16a/official", ".json"));
    return all.stream().sorted();
  }

  private static ConversionResult convert(byte[] input, ConversionOptions options) {
    return converter.convert(
        new ConversionRequest(FormatId.CHMED16A, FormatId.CH_EMED, input, options));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corpus")
  void imageGivesTheSameResultAsJson(String fixture) throws Exception {
    byte[] json = Fixtures.bytes(fixture);
    byte[] image =
        QrImageWriter.write(
            new String(Chmed16aPayloadEncoder.encode(json), StandardCharsets.US_ASCII));

    for (ConversionOptions options :
        List.of(ConversionOptions.defaults(), TestOptions.withAuthorAndPatientId())) {
      ConversionResult fromJson = convert(json, options);
      ConversionResult fromImage = convert(image, options);

      assertThat(fromImage.outcome()).as(fixture).isEqualTo(fromJson.outcome());
      assertThat(fromImage.report().entries()).as(fixture).isEqualTo(fromJson.report().entries());
      assertThat(fromImage.output().isPresent()).isEqualTo(fromJson.output().isPresent());
      fromJson.output().ifPresent(o -> assertThat(fromImage.output().orElseThrow()).isEqualTo(o));
    }
  }
}
