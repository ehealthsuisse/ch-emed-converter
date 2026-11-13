package ch.emed.converter.chmed23a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.io.Chmed23aJsonReader;
import ch.emed.converter.chmed23a.io.TransmissionFormat;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.PosologyDetail;
import ch.emed.converter.chmed23a.testsupport.Corpus;
import ch.emed.converter.chmed23a.testsupport.TestOptions;
import ch.emed.converter.chmed23a.validation.Chmed23aValidator;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.qr.QrImageReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T052-T056 (US3): CH EMED to CHMED23A, output forms, round trips (FR-011, SC-002) and determinism
 * (FR-012).
 */
class WriteChmed23aIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(
      FormatId source, FormatId target, byte[] input, ConversionOptions options) {
    return converter.convert(new ConversionRequest(source, target, input, options));
  }

  private static byte[] toChEmed(byte[] chmed23a) {
    ConversionResult result =
        convert(
            FormatId.CHMED23A, FormatId.CH_EMED, chmed23a, TestOptions.withAuthorAndPatientId());
    return result.output().orElseThrow(() -> new AssertionError(result.report().entries()));
  }

  private static ConversionResult toChmed23a(byte[] chEmed, OutputForm form) {
    return convert(
        FormatId.CH_EMED,
        FormatId.CHMED23A,
        chEmed,
        TestOptions.withAuthorAndPatientId().withOutputForm(form));
  }

  private static byte[] toChmed23a(byte[] chEmed) {
    ConversionResult result = toChmed23a(chEmed, OutputForm.DOCUMENT);
    return result.output().orElseThrow(() -> new AssertionError(errors(result)));
  }

  private static List<ReportEntry> errors(ConversionResult result) {
    return result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  private static Medication read(byte[] json) {
    ReportCollector report = new ReportCollector();
    Medication med = Chmed23aJsonReader.read(json, report);
    Chmed23aValidator.validate(med, report, Phase.VALIDATE_OUTPUT);
    assertThat(report.hasErrors()).as(report.entries().toString()).isFalse();
    return med;
  }

  /** The document with every UUID replaced by its order of first appearance and the PDF masked. */
  private static String normalized(byte[] chEmed) {
    String text =
        new String(chEmed, StandardCharsets.UTF_8)
            .replaceAll("\"data\":\"[^\"]*\"", "\"data\":\"{{pdf}}\"");
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            .matcher(text);
    java.util.Map<String, String> ids = new java.util.LinkedHashMap<>();
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String id = ids.computeIfAbsent(m.group(), k -> "{{id" + ids.size() + "}}");
      m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(id));
    }
    m.appendTail(out);
    return out.toString();
  }

  static Stream<String> corpus() {
    return Corpus.conforming().stream();
  }

  /** T053, T055: every document returns as conforming CHMED23A, and the round trip is stable. */
  @ParameterizedTest
  @MethodSource("corpus")
  void roundTripIsConformingAndStable(String fixture) {
    byte[] chEmed1 = toChEmed(Fixtures.bytes(fixture));
    byte[] chmed2 = toChmed23a(chEmed1);
    read(chmed2);
    byte[] chEmed2 = toChEmed(chmed2);
    byte[] chmed3 = toChmed23a(chEmed2);

    // ids derive from JSON paths; a medicament with several posologies returns as several
    // medicaments (capability gap), so its resources get other ids with equal content
    assertThat(normalized(chEmed2))
        .as(fixture + ": CH EMED after the round trip")
        .isEqualTo(normalized(chEmed1));
    assertThat(new String(chmed3, StandardCharsets.UTF_8))
        .as(fixture + ": CHMED23A after a second round trip")
        .isEqualTo(new String(chmed2, StandardCharsets.UTF_8));
  }

  /** T055: the documented differences of Example 1 (capability gaps). */
  @Test
  void example1DiffersOnlyByTheDocumentedGaps() {
    Medication original = read(Fixtures.bytes("chmed23a/official/Example1.json"));
    Medication back = read(toChmed23a(toChEmed(Fixtures.bytes("chmed23a/official/Example1.json"))));

    assertThat(back.patient().mData()).isNull();
    assertThat(back.id()).isNotNull();
    assertThat(back.meds()).hasSameSizeAs(original.meds());
    assertThat(back.meds()).extracting(m -> m.prscbBy()).containsOnlyNulls();
    assertThat(back.meds().get(4).pos().get(0).po()).isInstanceOf(PosologyDetail.FreeText.class);
    assertThat(back.meds().get(0).pos().get(0).po())
        .isEqualTo(original.meds().get(0).pos().get(0).po());
    assertThat(back.meds().get(0).pos().get(0).relMeal()).isEqualTo(3);
    assertThat(back.hcPerson()).isEqualTo(original.hcPerson());
    assertThat(back.patient().fName()).isEqualTo(original.patient().fName());
    assertThat(back.patient().ids()).isEqualTo(original.patient().ids());
  }

  /** T052: the prescription with tapering returns as four medicaments (research R10). */
  @Test
  void example4ReturnsWithOneMedicamentPerRequest() {
    Medication back = read(toChmed23a(toChEmed(Fixtures.bytes("chmed23a/official/Example4.json"))));
    assertThat(back.isPrescription()).isTrue();
    assertThat(back.meds()).hasSize(7);
    assertThat(back.meds().get(0).reps())
        .isEqualTo(new ch.emed.converter.chmed23a.model.Repetition.Duration(6, 6));
    assertThat(back.meds().get(5).reps())
        .isEqualTo(new ch.emed.converter.chmed23a.model.Repetition.Number(0));
    assertThat(back.meds().get(4).isNotSub()).isTrue();
  }

  /** T052: CH EMED documents of the CHMED16A tests also convert to CHMED23A. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "chemed/medication-card-example.json",
        "chemed/medication-prescription-example.json",
        "chemed/rx-substitution-repeats.json",
        "chemed/card-complex-timing.json"
      })
  void chEmedDocumentsOfOtherSourcesConvert(String fixture) {
    ConversionResult result = toChmed23a(Fixtures.bytes(fixture), OutputForm.DOCUMENT);
    assertThat(errors(result)).as(fixture).isEmpty();
    read(result.output().orElseThrow());
  }

  /** T054: payload and image; a prescription image is allowed (clarification 2026-11-12). */
  @ParameterizedTest
  @ValueSource(strings = {"Example1.json", "Example4.json"})
  void payloadAndImage(String example) throws Exception {
    byte[] chEmed = toChEmed(Fixtures.bytes("chmed23a/official/" + example));
    byte[] json = toChmed23a(chEmed);
    String payload =
        new String(
            toChmed23a(chEmed, OutputForm.QR_PAYLOAD).output().orElseThrow(),
            StandardCharsets.US_ASCII);
    assertThat(payload).startsWith("CHMED23A.");
    assertThat(TransmissionFormat.decode(payload, 1_000_000, new ReportCollector()))
        .isEqualTo(json);
    byte[] png = toChmed23a(chEmed, OutputForm.QR_IMAGE).output().orElseThrow();
    assertThat(
            new String(
                QrImageReader.read(png, QrImageReader.DEFAULT_MAX_PIXELS),
                StandardCharsets.US_ASCII))
        .isEqualTo(payload);
  }

  /** T054: a document too large for one code fails for the image only (M23-QR-12). */
  @Test
  void aLargeDocumentExceedsTheQrCapacity() {
    byte[] chEmed = toChEmed(LargePlan.json(40));
    ConversionResult image = toChmed23a(chEmed, OutputForm.QR_IMAGE);
    assertThat(errors(image))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.QR_CAPACITY_EXCEEDED);
              assertThat(e.message()).contains("2331");
            });
    assertThat(toChmed23a(chEmed, OutputForm.QR_PAYLOAD).output()).isPresent();
  }

  /** T056: equal input and options give equal bytes in every form. */
  @Test
  void outputIsDeterministic() {
    byte[] chEmed = toChEmed(Fixtures.bytes("chmed23a/official/Example2.json"));
    for (OutputForm form :
        List.of(OutputForm.DOCUMENT, OutputForm.QR_PAYLOAD, OutputForm.QR_IMAGE)) {
      assertThat(toChmed23a(chEmed, form).output().orElseThrow())
          .as(form.name())
          .isEqualTo(toChmed23a(chEmed, form).output().orElseThrow());
    }
    assertThat(toChEmed(Fixtures.bytes("chmed23a/official/Example2.json"))).isEqualTo(chEmed);
  }

  /** T052: other CH EMED document types are refused naming the supported ones. */
  @Test
  void otherDocumentTypesAreRefused() {
    ConversionResult result =
        toChmed23a(Fixtures.bytes("chemed/card-unsupported-type.json"), OutputForm.DOCUMENT);
    assertThat(errors(result))
        .extracting(ReportEntry::code)
        .containsExactly(ReasonCode.UNSUPPORTED_DOCUMENT_TYPE);
  }
}
