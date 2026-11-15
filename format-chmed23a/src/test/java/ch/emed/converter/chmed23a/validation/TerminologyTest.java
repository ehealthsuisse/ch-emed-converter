package ch.emed.converter.chmed23a.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** T015: the vendored code tables match terminology.md of the pinned commit (research R7). */
class TerminologyTest {

  private static int codes(String section) {
    String text = Fixtures.text("chmed23a/terminology.md");
    int start = text.indexOf("\n## " + section + "\n");
    int end = text.indexOf("\n## ", start + 4);
    String body = text.substring(start, end < 0 ? text.length() : end);
    Matcher m = Pattern.compile("(?m)^\\| `[^`]*` \\|").matcher(body);
    int count = 0;
    while (m.find()) {
      count++;
    }
    return count;
  }

  @Test
  void everyCodeOfTerminologyMdIsVendored() {
    assertThat(Terminology.sizes().get("unit")).isEqualTo(codes("Unit"));
    assertThat(Terminology.sizes().get("route")).isEqualTo(codes("Route of Administration"));
    assertThat(Terminology.sizes().get("method")).isEqualTo(codes("Method of Administration"));
    assertThat(Terminology.sizes().get("risk")).isEqualTo(codes("Risks"));
  }

  @Test
  void unitsAreComparedCaseInsensitively() {
    assertThat(Terminology.isUnit("Stk")).isTrue();
    assertThat(Terminology.isUnit("STK")).isTrue();
    assertThat(Terminology.unitCode("STK")).isEqualTo("Stk");
    assertThat(Terminology.isUnit("TABL")).isFalse();
  }

  @Test
  void codesAndRiskCategories() {
    assertThat(Terminology.isRoute("20053000")).isTrue();
    assertThat(Terminology.isMethod("19")).isTrue();
    assertThat(Terminology.isMethod("20")).isFalse();
    assertThat(Terminology.riskCategory(577)).isEqualTo(1);
    assertThat(Terminology.riskCategory(780)).isEqualTo(7);
    assertThat(Terminology.riskCategory(503)).isEqualTo(6);
    assertThat(Terminology.riskCategory(999_999)).isNull();
  }
}
