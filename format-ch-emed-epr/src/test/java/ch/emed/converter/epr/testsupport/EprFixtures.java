package ch.emed.converter.epr.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fixtures of this module ({@code src/test/resources/fixtures}). The feature 001 corpus is read
 * with {@code ch.emed.converter.chmed16a.testsupport.Fixtures} from the test jar of
 * format-chmed16a.
 */
public final class EprFixtures {

  private EprFixtures() {}

  public static byte[] bytes(String relativePath) {
    try (InputStream in = EprFixtures.class.getResourceAsStream("/fixtures/" + relativePath)) {
      if (in == null) {
        throw new IllegalArgumentException("Fixture not found: " + relativePath);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** File names in a fixture directory of this module that start with a prefix, sorted. */
  public static List<String> names(String directory, String prefix) {
    URL url = EprFixtures.class.getResource("/fixtures/" + directory);
    if (url == null || !"file".equals(url.getProtocol())) {
      throw new IllegalStateException("Fixture directory not found: " + directory);
    }
    try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
      return files
          .map(p -> p.getFileName().toString())
          .filter(n -> n.startsWith(prefix) && n.endsWith(".json"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The official examples of one document type: PMLC, PRE or MTP. */
  public static List<String> official(String type) {
    return switch (type) {
      case "PMLC" ->
          List.of(
              "Bundle-BundlePmlc1.json",
              "Bundle-DocumentPmlcCARAPMP004AxapharmParacetamol.json",
              "Bundle-DocumentPmlcCARAPMP004DafalganEffParacetamol.json",
              "Bundle-DocumentUCSF1CARAPMP004PMLCEmpty.json",
              "Bundle-DocumentUCSF3CARAPMP004PMLCDafalganSelfMedication.json",
              "Bundle-DocumentUCSF5CARAPMP004PMLCDafalganSelfMedication.json");
      case "PRE" ->
          List.of(
              "Bundle-DocumentPre1.json",
              "Bundle-DocumentPreCARAPMP004MarcoumarFreeTextDosage.json",
              "Bundle-DocumentPreParacetamolAxapharmCARAPMP004.json",
              "Bundle-DocumentPreParacetamolDafalganCARAPMP004.json",
              "Bundle-DocumentUCSF7CARAPMP004PREDafalgan.json");
      case "MTP" ->
          List.of(
              "Bundle-DocumentMtp1.json",
              "Bundle-DocumentMtpCARAPMP004MarcoumarFreeTextDosage.json",
              "Bundle-DocumentMtpParacetamolCARAPMP004.json",
              "Bundle-DocumentMtpParacetamolDafalganCARAPMP004.json",
              "Bundle-DocumentUCSF2CARAPMP004MTPParacetamol.json",
              "Bundle-DocumentUCSF6CARAPMP004MTPIbuprofen.json");
      default -> throw new IllegalArgumentException(type);
    };
  }
}
