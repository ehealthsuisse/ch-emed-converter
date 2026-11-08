package ch.emed.converter.chmed16a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.core.api.SignatureData;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** T029 (feature 003, US2): parsing signed eRezept URLs (research R6). */
class ErezeptUrlParserTest {

  private static final String I = "Erika+Muster+%28HIN%7Cemust1%29";
  private static final String T = "1767225600";
  private static final String S = "3066023100" + "ab".repeat(43);
  private static final SignatureData SIGNATURE = new SignatureData(I, T, S);
  private static final long LIMIT = 5L * 1024 * 1024;

  private final ReportCollector report = new ReportCollector();

  private ErezeptUrlParser.Parsed parse(String fixture) {
    return ErezeptUrlParser.parse(Fixtures.text("erezept/" + fixture), LIMIT, report);
  }

  private static String payloadOf(String fixture) {
    String url = Fixtures.text("erezept/" + fixture).strip();
    String fragment = url.substring(url.indexOf('#') + 1);
    for (String token : fragment.split("&")) {
      if (token.startsWith("CHMED")) {
        return token;
      }
    }
    throw new IllegalStateException("no payload in " + fixture);
  }

  @Test
  void extractsPayloadAndRawSignatureData() {
    ErezeptUrlParser.Parsed parsed = parse("rx-signed.url.txt");

    assertThat(parsed.payload()).isEqualTo(payloadOf("rx-signed.url.txt")).startsWith("CHMED16A1");
    assertThat(parsed.signature()).isEqualTo(SIGNATURE);
    assertThat(parsed.signature().identity()).contains("%28");
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void parameterOrderIsFree() {
    ErezeptUrlParser.Parsed parsed = parse("rx-reordered.url.txt");

    assertThat(parsed.payload()).isEqualTo(payloadOf("rx-signed.url.txt"));
    assertThat(parsed.signature()).isEqualTo(SIGNATURE);
  }

  @Test
  void queryPartIsIgnored() {
    ErezeptUrlParser.Parsed parsed = parse("rx-query.url.txt");

    assertThat(parsed.signature()).isEqualTo(SIGNATURE);
    assertThat(report.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNKNOWN_FIELD_IGNORED);
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
              assertThat(e.message()).doesNotContain("ref=1");
            });
  }

  @Test
  void unknownParameterIsReportedByNameOnly() {
    ErezeptUrlParser.Parsed parsed = parse("rx-unknown-param.url.txt");

    assertThat(parsed.signature()).isEqualTo(SIGNATURE);
    assertThat(report.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNKNOWN_FIELD_IGNORED);
              assertThat(e.message()).contains("'x'").doesNotContain("x=1");
            });
  }

  @ParameterizedTest
  @CsvSource({
    "rx-missing-s.url.txt, s",
    "rx-duplicate-t.url.txt, t",
    "rx-empty-i.url.txt, i",
    "rx-foreign-host.url.txt, host",
    "rx-no-payload.url.txt, payload"
  })
  void invalidUrlsNameWhatIsWrong(String fixture, String named) {
    assertThat(parse(fixture)).isNull();

    assertThat(errors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.EREZEPT_URL_INVALID);
              assertThat(e.message()).contains("'" + named + "'");
            });
  }

  @Test
  void malformedSignatureParametersArePassedOnUnchanged() {
    ErezeptUrlParser.Parsed parsed = parse("rx-malformed-sig.url.txt");

    assertThat(parsed.signature()).isEqualTo(new SignatureData(I, "yesterday", "not-hex"));
    assertThat(report.entries())
        .extracting(ReportEntry::code, ReportEntry::severity)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                ReasonCode.SIGNATURE_FORM_UNRECOGNISED, Severity.INFORMATION),
            org.assertj.core.groups.Tuple.tuple(
                ReasonCode.SIGNATURE_FORM_UNRECOGNISED, Severity.INFORMATION));
  }

  @Test
  void urlLongerThanTheLimitIsRefused() {
    assertThat(ErezeptUrlParser.parse(Fixtures.text("erezept/rx-signed.url.txt"), 100, report))
        .isNull();
    assertThat(errors())
        .extracting(ReportEntry::code)
        .containsExactly(ReasonCode.EREZEPT_URL_INVALID);
  }

  @Test
  void noMessageContainsAParameterValue() {
    for (String fixture :
        List.of(
            "rx-signed.url.txt",
            "rx-missing-s.url.txt",
            "rx-duplicate-t.url.txt",
            "rx-malformed-sig.url.txt",
            "rx-unknown-param.url.txt",
            "rx-foreign-host.url.txt")) {
      parse(fixture);
    }

    assertThat(report.entries())
        .extracting(ReportEntry::message)
        .allSatisfy(
            m ->
                assertThat(m)
                    .doesNotContain("Erika")
                    .doesNotContain(T)
                    .doesNotContain("3066023100")
                    .doesNotContain("yesterday")
                    .doesNotContain("H4sI"));
  }

  private List<ReportEntry> errors() {
    return report.entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }
}
