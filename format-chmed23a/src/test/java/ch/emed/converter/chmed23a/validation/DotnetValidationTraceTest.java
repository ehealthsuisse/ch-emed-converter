package ch.emed.converter.chmed23a.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * T017 (research R7.4): every validation test of the .NET reference implementation is traced to a
 * conformance rule of the mapping specification, or marked "not applied" with a reason.
 */
class DotnetValidationTraceTest {

  private static final Path SPECIFICATION = Path.of("../docs/mapping/chmed23a-ch-emed.md");

  /** Test methods of Tests.Unit/Validation at the pinned commit 0b579aa6. */
  private static final int DOTNET_TESTS = 176;

  private static final Pattern RULE_ROW = Pattern.compile("(?m)^\\|\\s*(C23-[A-Z]+-\\d+)\\s*\\|");
  private static final Pattern TRACE_ROW =
      Pattern.compile("(?m)^\\| `([A-Za-z]+Tests\\.[A-Za-z0-9_]+)` \\| ([^|]+) \\| ([^|]*) \\|$");

  @Test
  void everyDotnetValidationTestIsTraced() throws IOException {
    String text = Files.readString(SPECIFICATION, StandardCharsets.UTF_8);
    Set<String> rules = new TreeSet<>();
    Matcher r = RULE_ROW.matcher(text);
    while (r.find()) {
      rules.add(r.group(1));
    }
    List<String> problems = new ArrayList<>();
    Set<String> tests = new TreeSet<>();
    Matcher m = TRACE_ROW.matcher(text);
    while (m.find()) {
      tests.add(m.group(1));
      String rule = m.group(2).trim();
      String note = m.group(3).trim();
      if (rule.equals("not applied")) {
        if (note.isEmpty()) {
          problems.add(m.group(1) + ": not applied without a reason");
        }
      } else if (!rules.contains(rule)) {
        problems.add(m.group(1) + ": unknown rule " + rule);
      }
    }
    assertThat(tests).hasSize(DOTNET_TESTS);
    assertThat(problems).isEmpty();
  }
}
