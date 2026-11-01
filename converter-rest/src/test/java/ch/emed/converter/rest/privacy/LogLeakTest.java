package ch.emed.converter.rest.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * T037 (SC-004, FR-005, FR-014): after the whole corpus has run through the service with every
 * option set, the log contains no patient value, document fragment, base64 document, option value
 * or eRezept signature data, and the service has written no file.
 */
@ExtendWith(OutputCaptureExtension.class)
class LogLeakTest extends AbstractRestTest {

  /** Patient, author and option values of the fixtures and of the options sent below. */
  private static final String[] SENSITIVE = {
    // patients of the corpus (plan-basic, medication-card-example, readme example)
    "Muster",
    "1950-03-21",
    "Testweg",
    "80756000000000000001",
    "Monika",
    "Wegm",
    "1943-05-15",
    "Wiesenstr",
    // option values
    "urn:oid:2.999.1",
    "Geheim-4711",
    "Erika",
    "Beispiel",
    "Praxis Beispiel",
    "Musterweg",
    // eRezept signature data (identity, timestamp, signature) and URL
    "TestArzt",
    "20261001T093000",
    "c2lnbmF0dXJlLXRlc3QtMDAwMDAw",
    "eprescription",
    // document fragments
    "\"Medicaments\"",
    "\"resourceType\"",
    "MedicationStatement/",
    "CHMED16A1H4sI"
  };

  @Test
  void corpusLeavesNoContentInLogsOrFiles(CapturedOutput output) throws Exception {
    Set<Path> filesBefore = moduleFiles();
    String options =
        "\"strict\":false,\"language\":\"fr-CH\",\"confidentiality\":\"restricted\","
            + "\"patientIdentifier\":{\"system\":\"urn:oid:2.999.1\",\"value\":\"Geheim-4711\"}";
    List<String> base64Prefixes =
        Stream.concat(
                RestTestSupport.corpus(RestTestSupport.CHMED16A).stream(),
                RestTestSupport.corpus(RestTestSupport.CH_EMED).stream())
            .map(p -> Base64.getEncoder().encodeToString(RestTestSupport.read(p)).substring(0, 24))
            .toList();

    for (Path input : RestTestSupport.corpus(RestTestSupport.CHMED16A)) {
      mvc.perform(
          convert(
              "chmed16a",
              "ch-emed",
              RestTestSupport.read(input),
              RestTestSupport.withAuthor(options)));
    }
    for (Path input : RestTestSupport.corpus(RestTestSupport.CH_EMED)) {
      mvc.perform(convert("ch-emed", "chmed16a", RestTestSupport.read(input), null));
    }

    String log = output.getAll();
    assertThat(log).contains("request timestamp=").doesNotContain(SENSITIVE);
    assertThat(log).doesNotContain(base64Prefixes.toArray(String[]::new));
    assertThat(moduleFiles()).as("files written by the service").isEqualTo(filesBefore);
  }

  /** Files of the module outside {@code target/} (build output). */
  private static Set<Path> moduleFiles() {
    Path module = Path.of("").toAbsolutePath();
    try (Stream<Path> files = Files.walk(module)) {
      return files
          .filter(Files::isRegularFile)
          .filter(p -> !module.relativize(p).startsWith("target"))
          .collect(Collectors.toSet());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
