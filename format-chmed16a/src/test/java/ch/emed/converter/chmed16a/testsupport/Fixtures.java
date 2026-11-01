package ch.emed.converter.chmed16a.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Loads test fixtures from {@code src/test/resources/fixtures}. */
public final class Fixtures {

  private Fixtures() {}

  public static byte[] bytes(String relativePath) {
    try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + relativePath)) {
      if (in == null) {
        throw new IllegalArgumentException("Fixture not found: " + relativePath);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String text(String relativePath) {
    return new String(bytes(relativePath), StandardCharsets.UTF_8);
  }

  /** Relative paths of all files in a fixture directory (sorted), filtered by suffix. */
  public static List<String> list(String directory, String suffix) {
    URL url = Fixtures.class.getResource("/fixtures/" + directory);
    if (url == null) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
      return files
          .map(p -> directory + "/" + p.getFileName())
          .filter(p -> p.endsWith(suffix))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
