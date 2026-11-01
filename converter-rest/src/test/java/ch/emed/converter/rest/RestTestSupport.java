package ch.emed.converter.rest;

import ch.emed.converter.cli.CliMain;
import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.OrganizationDetails;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Fixtures, request messages and the in-process CLI for the REST tests. */
public final class RestTestSupport {

  /** Feature 001 reference corpus (module {@code format-chmed16a}). */
  public static final Path FIXTURES =
      Path.of("..", "format-chmed16a", "src", "test", "resources", "fixtures");

  public static final Path CHMED16A = FIXTURES.resolve("chmed16a");
  public static final Path CH_EMED = FIXTURES.resolve("chemed");

  /** Contract of feature 002, the source of truth of the API (research R8). */
  public static final String OPENAPI =
      Path.of("..", "specs", "002-rest-conversion-service", "contracts", "openapi.yaml")
          .toAbsolutePath()
          .normalize()
          .toString();

  public static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Validates responses only. Failure tests send requests that violate the contract on purpose, so
   * request validation is switched off for them.
   */
  public static final OpenApiInteractionValidator RESPONSE_CONTRACT =
      OpenApiInteractionValidator.createForSpecificationUrl(OPENAPI)
          .withLevelResolver(
              LevelResolver.create()
                  .withLevel("validation.request", ValidationReport.Level.IGNORE)
                  .build())
          .build();

  /**
   * Synthetic author details. CHMED16A plans carry only {@code Auth}; CH EMED needs practitioner
   * name and organisation (feature 001 FR-023), so conversions to CH EMED need this option.
   */
  public static final String AUTHOR_JSON =
      "{\"gln\":\"7601000000001\",\"given\":\"Erika\",\"family\":\"Beispiel\","
          + "\"organization\":{\"name\":\"Praxis Beispiel\",\"street\":\"Musterweg 2\","
          + "\"zip\":\"3000\",\"city\":\"Bern\",\"country\":\"CH\"}}";

  /** {@link #AUTHOR_JSON} as library value. */
  public static final AuthorDetails AUTHOR =
      new AuthorDetails(
          "7601000000001",
          "Erika",
          "Beispiel",
          new OrganizationDetails("Praxis Beispiel", "Musterweg 2", "3000", "Bern", "CH"));

  private RestTestSupport() {}

  /** Request options {@code {"author": …}} plus further option members (JSON object members). */
  public static JsonNode withAuthor(String moreMembers) {
    return json(
        "{\"author\":" + AUTHOR_JSON + (moreMembers.isEmpty() ? "" : "," + moreMembers) + "}");
  }

  /** Writes {@link #AUTHOR_JSON} to a file for the CLI option {@code --author}. */
  public static Path authorFile(Path directory) {
    try {
      return Files.writeString(directory.resolve("author.json"), AUTHOR_JSON);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static byte[] read(Path file) {
    try {
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Fixture of this module ({@code src/test/resources/fixtures}). */
  public static Path restFixture(String name) {
    try {
      return Path.of(RestTestSupport.class.getResource("/fixtures/" + name).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Every input document of a corpus directory (README files excluded), sorted. */
  public static List<Path> corpus(Path directory) {
    try (var files = Files.walk(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(p -> !p.getFileName().toString().equals("README.md"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Request message {@code {document, options}}; {@code options} may be null. */
  public static String requestMessage(byte[] document, JsonNode options) {
    ObjectNode message = JSON.createObjectNode();
    message.put("document", Base64.getEncoder().encodeToString(document));
    if (options != null) {
      message.set("options", options);
    }
    return message.toString();
  }

  public static JsonNode json(String text) {
    try {
      return JSON.readTree(text);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static JsonNode json(byte[] bytes) {
    try {
      return JSON.readTree(bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Result of one in-process CLI run. */
  public record CliRun(int exitCode, byte[] output, byte[] report) {}

  /**
   * Runs {@code convert} of the CLI in-process with the given converter, JSON report.
   *
   * @param extraArgs further CLI options, e.g. {@code --strict}
   */
  public static CliRun cli(
      Converter converter, Path input, String from, String to, List<String> extraArgs) {
    try {
      Path report = Files.createTempFile("cli-report", ".json");
      try {
        List<String> args = new ArrayList<>();
        args.addAll(List.of("convert", "--from", from, "--to", to, "--report-format", "json"));
        args.addAll(List.of("--report", report.toString()));
        args.addAll(extraArgs);
        args.add(input.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit =
            CliMain.execute(
                () -> converter,
                args.toArray(String[]::new),
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        return new CliRun(exit, out.toByteArray(), Files.readAllBytes(report));
      } finally {
        Files.deleteIfExists(report);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
