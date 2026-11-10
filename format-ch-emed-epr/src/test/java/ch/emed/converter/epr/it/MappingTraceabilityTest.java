package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * T018 (feature 004): every rule id of {@code docs/mapping/ch-emed-epr.md} is implemented by
 * annotated code of this module, and every {@code @MappingRule} id of the code is in the
 * specification (constitution II, FR-012). Same check as feature 001 for its own specification.
 */
class MappingTraceabilityTest {

  private static final Path SOURCES = Path.of("src/main/java/ch/emed/converter/epr");
  private static final Path SPECIFICATION = Path.of("../docs/mapping/ch-emed-epr.md");
  private static final Pattern ANNOTATION =
      Pattern.compile("@MappingRule\\(\\s*(\\{[^}]*}|\"[^\"]*\")\\s*\\)");
  private static final Pattern ID = Pattern.compile("\"(M-EPR-\\d+)\"");
  private static final Pattern TABLE_ID = Pattern.compile("(?m)^\\|\\s*(M-EPR-\\d+)\\s*\\|");

  private static Set<String> codeIds() throws IOException {
    Set<String> ids = new TreeSet<>();
    try (Stream<Path> files = Files.walk(SOURCES)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        Matcher annotation = ANNOTATION.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (annotation.find()) {
          Matcher id = ID.matcher(annotation.group(1));
          while (id.find()) {
            ids.add(id.group(1));
          }
        }
      }
    }
    return ids;
  }

  private static Set<String> specificationIds() throws IOException {
    Set<String> ids = new TreeSet<>();
    Matcher m = TABLE_ID.matcher(Files.readString(SPECIFICATION, StandardCharsets.UTF_8));
    while (m.find()) {
      ids.add(m.group(1));
    }
    return ids;
  }

  @Test
  void everyRuleOfTheCodeIsInTheMappingSpecification() throws IOException {
    Set<String> unknown = new TreeSet<>(codeIds());
    unknown.removeAll(specificationIds());

    assertThat(unknown).as("rule ids used in code but missing in the specification").isEmpty();
  }

  @Test
  void everyRuleOfTheMappingSpecificationIsImplementedByAnnotatedCode() throws IOException {
    Set<String> unimplemented = new TreeSet<>(specificationIds());
    unimplemented.removeAll(codeIds());

    assertThat(unimplemented).as("rule ids in the specification without annotated code").isEmpty();
  }

  @Test
  void theSpecificationHasRules() throws IOException {
    assertThat(specificationIds()).isNotEmpty();
  }
}
