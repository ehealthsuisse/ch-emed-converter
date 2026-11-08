package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * T023 (feature 003, US1): a QR code image gives the same CH EMED document and report as the plain
 * form of the same plan, with the same options (FR-002, FR-019, SC-001 for the fixture subset).
 */
class ImageInputIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(String fixture, ConversionOptions options) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CHMED16A, FormatId.CH_EMED, Fixtures.bytes(fixture), options));
  }

  private static void assertSameResult(ConversionResult image, ConversionResult plain) {
    assertThat(image.outcome()).isEqualTo(plain.outcome());
    assertThat(image.report().entries()).isEqualTo(plain.report().entries());
    assertThat(image.output().isPresent()).isEqualTo(plain.output().isPresent());
    image.output().ifPresent(o -> assertThat(o).isEqualTo(plain.output().orElseThrow()));
    assertThat(image.signature()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "qr/plan-basic.png, chmed16a/plan-basic.qr.txt",
    "qr/plan-basic.jpg, chmed16a/plan-basic.qr.txt",
    "qr/readme-example.png, chmed16a/official/readme-example-medication-plan.qr.txt",
    "qr/plan-non-ascii.png, chmed16a/plan-non-ascii.json",
    "qr/plan-non-ascii-plain.png, chmed16a/plan-non-ascii.json"
  })
  void imageGivesTheSameResultAsThePlainForm(String image, String plain) {
    ConversionResult fromImage = convert(image, TestOptions.withAuthorAndPatientId());
    ConversionResult fromPlain = convert(plain, TestOptions.withAuthorAndPatientId());

    assertThat(fromImage.outcome()).isNotEqualTo(Outcome.FAILED);
    assertSameResult(fromImage, fromPlain);
  }

  @Test
  void missingAuthorFailsIdenticallyForImageAndPlainForm() {
    ConversionOptions noAuthor = ConversionOptions.defaults();

    ConversionResult fromImage = convert("qr/plan-basic.png", noAuthor);
    ConversionResult fromPlain = convert("chmed16a/plan-basic.qr.txt", noAuthor);

    assertThat(fromImage.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(fromImage.report().entries())
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA);
              assertThat(e.path()).isEqualTo("/Auth");
            });
    assertSameResult(fromImage, fromPlain);
  }

  @ParameterizedTest
  @CsvSource({
    "qr/no-code.png, QR_NOT_FOUND",
    "qr/two-codes.png, QR_MULTIPLE",
    "qr/url-code.png, PARSE_ERROR",
    "qr/chmed23a.png, UNSUPPORTED_VERSION",
    "qr/not-an-image.gif, IMAGE_UNREADABLE"
  })
  void unusableImagesFailWithoutOutput(String image, String code) {
    ConversionResult result = convert(image, TestOptions.withAuthorAndPatientId());

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(errors(result)).extracting(ReportEntry::code).containsExactly(code);
    assertThat(errors(result))
        .extracting(ReportEntry::message)
        .noneMatch(m -> m.contains("example.org") || m.contains("H4sI"));
  }

  @Test
  void corruptImageIsUnreadable() {
    byte[] png = Fixtures.bytes("qr/plan-basic.png");
    byte[] corrupt = java.util.Arrays.copyOf(png, 200);

    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FormatId.CHMED16A, FormatId.CH_EMED, corrupt, TestOptions.withAuthor()));

    assertThat(errors(result))
        .extracting(ReportEntry::code)
        .containsExactly(ReasonCode.IMAGE_UNREADABLE);
  }

  @Test
  void imageAboveTheByteLimitIsTooLarge() {
    ConversionResult result =
        convert("qr/plan-basic.png", TestOptions.withAuthor().withMaxDecodedSize(1000));

    assertThat(errors(result))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.IMAGE_TOO_LARGE);
              assertThat(e.message()).contains("1000 bytes");
            });
  }

  private static List<ReportEntry> errors(ConversionResult result) {
    return result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }
}
