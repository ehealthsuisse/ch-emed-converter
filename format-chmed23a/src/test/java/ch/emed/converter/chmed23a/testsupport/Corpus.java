package ch.emed.converter.chmed23a.testsupport;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The CHMED23A test corpus (research R13). */
public final class Corpus {

  /** posology.md fragments with draft property names (finding F8): refused as non-conforming. */
  public static final Set<String> DRAFT_NAMES =
      Set.of(
          "chmed23a/posology/Single-1.json",
          "chmed23a/posology/Cyclic-1.json",
          "chmed23a/posology/Sequence-1.json");

  private Corpus() {}

  public static List<String> official() {
    return Fixtures.list("chmed23a/official", ".json");
  }

  /** Every fixture of the specification that conforms (official, posology, .NET). */
  public static List<String> conforming() {
    List<String> all = new ArrayList<>(official());
    Fixtures.list("chmed23a/posology", ".json").stream()
        .filter(f -> !DRAFT_NAMES.contains(f))
        .forEach(all::add);
    all.addAll(Fixtures.list("chmed23a/dotnet", ".json"));
    return all;
  }

  /** The medication plans of {@link #conforming()}. */
  public static List<String> plans() {
    return conforming().stream().filter(f -> !f.endsWith("Example4.json")).toList();
  }
}
