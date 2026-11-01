package ch.emed.converter.chmed16a.testsupport;

import ch.emed.converter.chmed16a.testsupport.ClinicalEquivalence.Difference;
import ch.emed.converter.chmed16a.testsupport.ClinicalEquivalence.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The machine-readable capability-gap list ({@code capability-gaps.json}), a mirror of the gap
 * sections of the mapping specification: a difference after a round trip is acceptable only if a
 * gap of the same direction describes it (SC-004).
 */
public final class CapabilityGaps {

  /** Direction of a round trip. */
  public static final String CHMED16A = "chmed16a-roundtrip";

  public static final String CHEMED = "chemed-roundtrip";

  /** One documented gap. */
  public record Gap(String rule, String direction, List<Kind> kinds, Pattern path, String reason) {
    boolean matches(String direction, Difference difference) {
      return this.direction.equals(direction)
          && kinds.contains(difference.kind())
          && path.matcher(difference.path()).matches();
    }
  }

  private static final List<Gap> GAPS = load();

  private CapabilityGaps() {}

  private static List<Gap> load() {
    try (InputStream in = CapabilityGaps.class.getResourceAsStream("/capability-gaps.json")) {
      JsonNode root = new ObjectMapper().readTree(in);
      List<Gap> gaps = new ArrayList<>();
      for (JsonNode g : root.path("gaps")) {
        List<Kind> kinds = new ArrayList<>();
        g.path("kinds").forEach(k -> kinds.add(Kind.valueOf(k.asText())));
        gaps.add(
            new Gap(
                g.path("rule").asText(),
                g.path("direction").asText(),
                kinds,
                Pattern.compile(g.path("path").asText()),
                g.path("reason").asText()));
      }
      return gaps;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static List<Gap> all() {
    return GAPS;
  }

  /** The differences no documented gap explains. */
  public static List<Difference> unexplained(String direction, List<Difference> differences) {
    return differences.stream()
        .filter(d -> GAPS.stream().noneMatch(g -> g.matches(direction, d)))
        .toList();
  }
}
