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
import ch.emed.converter.core.api.SignatureData;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * T030 (feature 003, US2): a signed eRezept URL, or a QR image holding one, gives the document of
 * the embedded prescription and the signature data raw and unverified beside it (FR-003, FR-004,
 * FR-005, FR-019, research R7, SC-003).
 */
class ErezeptInputIT {

  private static final SignatureData SIGNATURE =
      new SignatureData(
          "Erika+Muster+%28HIN%7Cemust1%29", "1767225600", "3066023100" + "ab".repeat(43));
  private static final String[] SECRET_VALUES = {"Erika", "1767225600", "3066023100", "emust1"};

  private static Converter converter;
  private static ListAppender<ILoggingEvent> logs;
  private static Logger rootLogger;

  @BeforeAll
  static void setUp() {
    converter = Converters.create(ChEmedAssertions.validator());
    rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    logs = new ListAppender<>();
    logs.start();
    rootLogger.addAppender(logs);
  }

  @AfterAll
  static void tearDown() {
    rootLogger.detachAppender(logs);
  }

  private static ConversionResult convert(String fixture, ConversionOptions options) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CHMED16A, FormatId.CH_EMED, Fixtures.bytes(fixture), options));
  }

  private static List<ReportEntry> without(ConversionResult result, String code) {
    return result.report().entries().stream().filter(e -> !e.code().equals(code)).toList();
  }

  private static List<String> codes(ConversionResult result) {
    return result.report().entries().stream().map(ReportEntry::code).toList();
  }

  @Test
  void signedUrlGivesTheDocumentOfTheEmbeddedPrescription() {
    ConversionResult fromUrl = convert("erezept/rx-signed.url.txt", ConversionOptions.defaults());
    ConversionResult fromJson = convert("chmed16a/rx-basic.json", ConversionOptions.defaults());

    assertThat(fromUrl.outcome()).isEqualTo(fromJson.outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(fromUrl.output().orElseThrow()).isEqualTo(fromJson.output().orElseThrow());
    assertThat(without(fromUrl, ReasonCode.SIGNATURE_NOT_VERIFIED))
        .isEqualTo(fromJson.report().entries());
    assertThat(fromJson.signature()).isEmpty();
  }

  @Test
  void signatureDataIsReturnedRawAndNotVerified() {
    ConversionResult result = convert("erezept/rx-signed.url.txt", ConversionOptions.defaults());

    assertThat(result.signature()).contains(SIGNATURE);
    assertThat(result.signature().orElseThrow().verified()).isFalse();
    assertThat(result.report().entries())
        .filteredOn(e -> e.code().equals(ReasonCode.SIGNATURE_NOT_VERIFIED))
        .singleElement()
        .satisfies(e -> assertThat(e.severity()).isEqualTo(Severity.INFORMATION));
  }

  @Test
  void signatureDataIsNeverInTheDocumentReportOrLogs() {
    logs.list.clear();
    ConversionResult result = convert("erezept/rx-signed.url.txt", ConversionOptions.defaults());

    String document =
        new String(result.output().orElseThrow(), java.nio.charset.StandardCharsets.UTF_8);
    String report = result.report().entries().toString();
    String logText = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList().toString();
    for (String value : SECRET_VALUES) {
      assertThat(document).as("document").doesNotContain(value);
      assertThat(report).as("report").doesNotContain(value);
      assertThat(logText).as("logs").doesNotContain(value);
    }
    assertThat(logText).doesNotContain("eprescription.hin.ch").doesNotContain("CHMED16A1");
  }

  @Test
  void qrImageOfASignedUrlGivesTheSameResultAsTheUrl() {
    ConversionResult fromImage = convert("erezept/rx-signed.png", ConversionOptions.defaults());
    ConversionResult fromUrl = convert("erezept/rx-signed.url.txt", ConversionOptions.defaults());

    assertThat(fromImage.output().orElseThrow()).isEqualTo(fromUrl.output().orElseThrow());
    assertThat(fromImage.report().entries()).isEqualTo(fromUrl.report().entries());
    assertThat(fromImage.signature()).isEqualTo(fromUrl.signature()).contains(SIGNATURE);
  }

  @Test
  void signedMedicationPlanIsRefused() {
    ConversionResult result =
        convert("erezept/plan-signed.url.txt", TestOptions.withAuthorAndPatientId());

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.signature()).isEmpty();
    assertThat(codes(result)).contains(ReasonCode.UNSUPPORTED_DOCUMENT_TYPE);
  }

  @Test
  void missingPatientIdentifierFailsWithoutDocumentOrSignature() {
    ConversionResult result =
        convert("erezept/rx-signed-no-patient-id.url.txt", ConversionOptions.defaults());

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(result.signature()).isEmpty();
    assertThat(result.report().entries())
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA);
              assertThat(e.path()).isEqualTo("/Patient/Ids");
            });
  }

  @Test
  void patientIdentifierFromOptionsIsMarkedAsNotCoveredBySignature() {
    ConversionResult result =
        convert(
            "erezept/rx-signed-no-patient-id.url.txt",
            ConversionOptions.defaults().withPatientIdentifier(TestOptions.PATIENT_ID));

    assertThat(result.outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(result.signature()).contains(SIGNATURE);
    assertThat(result.report().entries())
        .filteredOn(e -> e.code().equals(ReasonCode.NOT_COVERED_BY_SIGNATURE))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.path()).isEqualTo("/Patient/Ids");
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
            });
  }

  @Test
  void authorFromOptionsIsMarkedAsNotCoveredBySignature() {
    ConversionResult without =
        convert("erezept/rx-signed-erezept-example.url.txt", ConversionOptions.defaults());
    ConversionResult with =
        convert("erezept/rx-signed-erezept-example.url.txt", TestOptions.withAuthor());

    assertThat(without.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(codes(without)).contains(ReasonCode.MISSING_REQUIRED_DATA);
    assertThat(with.outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(with.report().entries())
        .filteredOn(e -> e.code().equals(ReasonCode.NOT_COVERED_BY_SIGNATURE))
        .extracting(ReportEntry::path)
        .containsExactlyInAnyOrder("/HcPerson", "/HcOrg");
  }

  @Test
  void optionsForUnsignedInputAreNotMarked() {
    ConversionResult result =
        convert(
            "erezept/rx-no-patient-id.json",
            ConversionOptions.defaults().withPatientIdentifier(TestOptions.PATIENT_ID));

    assertThat(codes(result)).doesNotContain(ReasonCode.NOT_COVERED_BY_SIGNATURE);
  }

  @Test
  void malformedSignatureParametersStillConvert() {
    ConversionResult result =
        convert("erezept/rx-malformed-sig.url.txt", ConversionOptions.defaults());

    assertThat(result.outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(result.signature().orElseThrow().timestamp()).isEqualTo("yesterday");
    assertThat(codes(result)).contains(ReasonCode.SIGNATURE_FORM_UNRECOGNISED);
  }

  @Test
  void invalidUrlFailsWithoutSignature() {
    ConversionResult result = convert("erezept/rx-missing-s.url.txt", ConversionOptions.defaults());

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.signature()).isEmpty();
    assertThat(codes(result)).containsExactly(ReasonCode.EREZEPT_URL_INVALID);
  }
}
