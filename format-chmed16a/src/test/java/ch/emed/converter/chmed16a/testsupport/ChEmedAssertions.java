package ch.emed.converter.chmed16a.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.determinism.NameBasedIdStrategy;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.validation.HapiChEmedValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Bundle;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/** Assertions on CH EMED output: official validation and comparison with expected documents. */
public final class ChEmedAssertions {

  private static final Pattern ID_PLACEHOLDER = Pattern.compile("\\{\\{id:([^}]+)}}");
  private static volatile HapiChEmedValidator validator;

  private ChEmedAssertions() {}

  /** Shared validator (expensive to build). */
  public static HapiChEmedValidator validator() {
    if (validator == null) {
      synchronized (ChEmedAssertions.class) {
        if (validator == null) {
          validator = new HapiChEmedValidator();
        }
      }
    }
    return validator;
  }

  /** Asserts that the document validates against CH EMED 6.0.0 with zero errors. */
  public static void assertValid(byte[] chEmedJson) {
    export(chEmedJson);
    Bundle bundle = ChEmedJson.parse(chEmedJson);
    List<ReportEntry> errors =
        validator().validate(bundle, Phase.VALIDATE_OUTPUT).stream()
            .filter(e -> e.severity() == Severity.ERROR)
            .toList();
    assertThat(errors).as("CH EMED validation errors").isEmpty();
  }

  /**
   * Writes a validated output to {@code target/it-outputs/<hash>.json} for the cross-check with the
   * official HL7 validator ({@code tools/validate-outputs.sh}, task T115).
   */
  private static void export(byte[] json) {
    try {
      byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(json);
      String name = java.util.HexFormat.of().formatHex(hash, 0, 8) + ".json";
      java.nio.file.Path directory = java.nio.file.Path.of("target/it-outputs");
      java.nio.file.Files.createDirectories(directory);
      java.nio.file.Files.write(directory.resolve(name), json);
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("Cannot export the output for the cross-check", e);
    }
  }

  /**
   * Resolves {@code {{id:<path>}}} placeholders of an expected document with {@link
   * NameBasedIdStrategy} for the given source document id.
   */
  public static String resolvePlaceholders(String expected, String documentId) {
    NameBasedIdStrategy ids = new NameBasedIdStrategy();
    Matcher m = ID_PLACEHOLDER.matcher(expected);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(ids.id(documentId, m.group(1))));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * Strict JSON comparison against an expected fixture with id placeholders resolved. The PDF of
   * the original representation (Binary.data) is replaced by {@code {{pdf}}} before comparing; it
   * is verified by the renderer tests.
   */
  public static void assertMatchesExpected(
      byte[] actual, String expectedFixture, String documentId) {
    String expected = resolvePlaceholders(Fixtures.text(expectedFixture), documentId);
    try {
      JSONAssert.assertEquals(expected, maskPdf(actual), JSONCompareMode.STRICT);
    } catch (JSONException e) {
      throw new AssertionError("Invalid JSON in " + expectedFixture, e);
    }
  }

  private static String maskPdf(byte[] actual) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      JsonNode root = mapper.readTree(actual);
      for (JsonNode entry : root.path("entry")) {
        JsonNode resource = entry.path("resource");
        if ("Binary".equals(resource.path("resourceType").asText())) {
          ((ObjectNode) resource).put("data", "{{pdf}}");
        }
      }
      return mapper.writeValueAsString(root);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
