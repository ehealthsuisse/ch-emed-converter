package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.PhiScanner;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.ReportJsonWriter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Privacy scan (FR-017, SC-006, constitution IV): runs the whole fixture corpus in both directions
 * and verifies that no patient value, raw QR payload or eRezept signature parameter appears in any
 * report or log message. Findings name the fixture and the logger, never the value.
 */
class PhiLeakTest {

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

  private static List<String> chmed16aFixtures() {
    List<String> all = new ArrayList<>();
    for (String suffix : List.of(".json", ".txt")) {
      all.addAll(Fixtures.list("chmed16a", suffix));
      all.addAll(Fixtures.list("chmed16a/official", suffix));
    }
    return all;
  }

  private static String text(ILoggingEvent event) {
    String thrown =
        event.getThrowableProxy() == null
            ? ""
            : String.valueOf(event.getThrowableProxy().getMessage());
    return event.getFormattedMessage() + "\n" + thrown;
  }

  /**
   * Events a configured deployment writes: everything of this project, and third-party events at
   * INFO and above (the shipped logback.xml of the CLI). HAPI and the HL7 validator log resource
   * content at DEBUG; that level must stay off in production (docs/mapping, "Logging").
   */
  private static boolean relevant(ILoggingEvent event) {
    return event.getLoggerName().startsWith("ch.emed")
        || event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.INFO);
  }

  /** Scans the report and the log events since the last call. */
  private static void scan(
      String fixture, PhiScanner scanner, ConversionResult result, List<String> findings) {
    String report = new String(ReportJsonWriter.write(result.report()), StandardCharsets.UTF_8);
    for (String value : scanner.findIn(report)) {
      findings.add(
          "report of " + fixture + " contains a collected value (" + value.length() + " chars)");
    }
    for (ILoggingEvent event : List.copyOf(logs.list)) {
      if (!relevant(event)) {
        continue;
      }
      for (String value : scanner.findIn(text(event))) {
        findings.add(
            "log of "
                + event.getLoggerName()
                + " ("
                + event.getLevel()
                + ")"
                + " after "
                + fixture
                + " contains a collected value ("
                + value.length()
                + " chars)");
      }
    }
    logs.list.clear();
  }

  @Test
  void noPatientValueQrPayloadOrSignatureParameterReachesAReportOrALog() {
    List<String> findings = new ArrayList<>();
    int conversions = 0;
    logs.list.clear();

    for (String fixture : chmed16aFixtures()) {
      String text = Fixtures.text(fixture);
      PhiScanner scanner = new PhiScanner().addChmed16aJson(text).addRawPayload(text);
      ConversionResult result =
          converter.convert(
              new ConversionRequest(
                  FormatId.CHMED16A,
                  FormatId.CH_EMED,
                  Fixtures.bytes(fixture),
                  TestOptions.withAuthorAndPatientId()));
      conversions++;
      scan(fixture, scanner, result, findings);
    }
    for (String fixture : Fixtures.list("chemed", ".json")) {
      PhiScanner scanner = new PhiScanner().addChEmedJson(Fixtures.text(fixture));
      ConversionResult result =
          converter.convert(
              new ConversionRequest(
                  FormatId.CH_EMED,
                  FormatId.CHMED16A,
                  Fixtures.bytes(fixture),
                  ConversionOptions.defaults()));
      conversions++;
      scan(fixture, scanner, result, findings);
    }

    assertThat(conversions).as("corpus size").isGreaterThan(30);
    assertThat(findings).as("leaks (the values themselves are never printed)").isEmpty();
  }

  @Test
  void theScannerFindsValuesThatAreThere() {
    PhiScanner scanner =
        new PhiScanner().addChmed16aJson(Fixtures.text("chmed16a/plan-basic.json"));

    assertThat(scanner.findIn("... Muster ... 1950-03-21 ..."))
        .containsExactlyInAnyOrder("Muster", "1950-03-21");
    assertThat(scanner.findIn("no patient data here")).isEmpty();
  }

  @Test
  void theQrAndErezeptFixturesAreCoveredByTheCorpus() {
    assertThat(chmed16aFixtures())
        .contains("chmed16a/erezept-url.txt", "chmed16a/plan-basic.qr.txt");
    PhiScanner scanner = new PhiScanner().addRawPayload(Fixtures.text("chmed16a/erezept-url.txt"));
    assertThat(scanner.values()).isNotEmpty();
  }
}
