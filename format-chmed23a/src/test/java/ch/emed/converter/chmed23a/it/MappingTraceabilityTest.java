package ch.emed.converter.chmed23a.it;

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
 * T025: every rule id of {@code docs/mapping/chmed23a-ch-emed.md} is implemented by annotated code,
 * and every {@code @MappingRule} id of the code exists in the specification.
 */
class MappingTraceabilityTest {

  private static final Path SOURCES = Path.of("src/main/java/ch/emed/converter/chmed23a");
  private static final Path SPECIFICATION = Path.of("../docs/mapping/chmed23a-ch-emed.md");
  private static final Pattern ANNOTATION =
      Pattern.compile("@MappingRule\\(\\s*(\\{[^}]*}|\"[^\"]*\")\\s*\\)");
  private static final Pattern ID = Pattern.compile("\"((?:C23|M23)-[A-Z]+-\\d+)\"");
  private static final Pattern TABLE_ID =
      Pattern.compile("(?m)^\\|\\s*((?:C23|M23)-[A-Z]+-\\d+)\\s*\\|");

  /**
   * Rules of later user stories not implemented yet (tasks.md phases 4-6); must be empty at the end
   * of phase 7 (task T074).
   */
  private static final Set<String> PENDING = Set.of();

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
    unimplemented.removeAll(PENDING);
    assertThat(unimplemented).as("rule ids in the specification without annotated code").isEmpty();
  }

  @Test
  void pendingRulesAreNotImplementedYet() throws IOException {
    Set<String> done = new TreeSet<>(PENDING);
    done.retainAll(codeIds());
    assertThat(done).as("implemented rules still listed as pending").isEmpty();
  }
}
