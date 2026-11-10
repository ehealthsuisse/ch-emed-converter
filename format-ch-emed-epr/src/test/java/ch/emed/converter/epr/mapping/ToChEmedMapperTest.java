package ch.emed.converter.epr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.EprReasonCode;
import ch.emed.converter.epr.testsupport.EprFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * T020 and T043 (feature 004, M-EPR-01, M-EPR-12): CH EMED EPR → CH EMED changes the declared
 * profiles only, for PMLC, PRE and MTP, and since feature 005 (T009) DIS, PADV and PML. Regenerate
 * the expected files with {@code -Depr.regenerate=true}.
 */
class ToChEmedMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Removes every {@code meta.profile}, and {@code meta} left empty, anywhere in the tree. */
  private static JsonNode withoutProfiles(JsonNode node) {
    JsonNode copy = node.deepCopy();
    strip(copy);
    return copy;
  }

  private static void strip(JsonNode node) {
    if (node instanceof ObjectNode object) {
      if (object.get("meta") instanceof ObjectNode meta) {
        meta.remove("profile");
        if (meta.isEmpty()) {
          object.remove("meta");
        }
      }
      object.forEach(ToChEmedMapperTest::strip);
    } else if (node.isArray()) {
      node.forEach(ToChEmedMapperTest::strip);
    }
  }

  private static int declaringEpr(JsonNode node) {
    int count = 0;
    if (node.isObject()) {
      for (JsonNode p : node.path("meta").path("profile")) {
        if (p.asText().startsWith(ProfileMap.EPR)) {
          count++;
          break;
        }
      }
      for (JsonNode child : node) {
        count += declaringEpr(child);
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        count += declaringEpr(child);
      }
    }
    return count;
  }

  @ParameterizedTest
  @CsvSource({
    "PMLC, Bundle-DocumentPmlcCARAPMP004AxapharmParacetamol",
    "PRE, Bundle-DocumentPreParacetamolAxapharmCARAPMP004",
    "MTP, Bundle-DocumentMtpParacetamolCARAPMP004",
    "DIS, Bundle-DocumentDis1",
    "PADV, Bundle-DocumentPadvChangeParacetamolCARAPMP004",
    "PML, Bundle-BundlePml1"
  })
  void onlyTheProfilesChange(EprDocumentType type, String name) throws IOException {
    byte[] input = EprFixtures.bytes("official/" + name + ".json");
    ReportCollector report = new ReportCollector();

    Bundle chEmed =
        new ToChEmedMapper()
            .map(
                ChEmedJson.parse(input),
                type,
                new MappingContext(ConversionOptions.defaults(), report));

    byte[] output = ChEmedJson.serialize(chEmed);
    JsonNode in = JSON.readTree(ChEmedJson.serialize(ChEmedJson.parse(input)));
    JsonNode out = JSON.readTree(output);
    assertThat(declaringEpr(out)).isZero();
    assertThat(withoutProfiles(out)).isEqualTo(withoutProfiles(in));
    assertThat(report.entries())
        .hasSize(declaringEpr(in))
        .allSatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(EprReasonCode.PROFILE_CHANGED);
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
            })
        .extracting(ReportEntry::path)
        .allMatch(p -> p.endsWith(".meta.profile"));

    String expected = "expected/to-chemed-" + name + ".json";
    if (Boolean.getBoolean("epr.regenerate")) {
      Path file = Path.of("src/test/resources/fixtures/" + expected);
      Files.createDirectories(file.getParent());
      Files.write(file, output);
    }
    assertThat(new String(output, StandardCharsets.UTF_8))
        .isEqualTo(new String(EprFixtures.bytes(expected), StandardCharsets.UTF_8));
  }
}
