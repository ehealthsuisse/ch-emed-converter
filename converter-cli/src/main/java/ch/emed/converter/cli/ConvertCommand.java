package ch.emed.converter.cli;

import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.Confidentiality;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionPair;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OrganizationDetails;
import ch.emed.converter.core.api.PatientIdentifier;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportJsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** {@code convert} command (contracts/cli.md). Output validation is always on. */
@Command(name = "convert", description = "Convert a document.")
final class ConvertCommand implements Callable<Integer> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CliContext context;

  @Spec CommandSpec spec;

  @Option(names = "--from", required = true, description = "Source format, e.g. chmed16a")
  String from;

  @Option(names = "--to", required = true, description = "Target format, e.g. ch-emed")
  String to;

  @Parameters(index = "0", arity = "0..1", defaultValue = "-", description = "Input file or -")
  String input;

  @Option(
      names = {"-o", "--output"},
      description = "Write the output document to this file (default: stdout)")
  Path output;

  @Option(names = "--report", description = "Write the JSON conversion report to this file")
  Path report;

  @Option(
      names = "--report-format",
      defaultValue = "text",
      description = "Report format on stderr: json or text")
  String reportFormat;

  @Option(
      names = "--language",
      description = "Document language if the source has none: de-CH, fr-CH, it-CH, en-CH")
  String language;

  @Option(
      names = "--confidentiality",
      description = "Document confidentiality: normal, restricted, secret")
  String confidentiality;

  @Option(
      names = "--patient-id",
      description = "Patient identifier if the source has none: <system>|<value>")
  String patientId;

  @Option(names = "--author", description = "JSON file with author details")
  Path author;

  @Option(
      names = "--strict",
      description = "Fail if clinically relevant information cannot be represented (FR-016)")
  boolean strict;

  ConvertCommand(CliContext context) {
    this.context = context;
  }

  @Override
  public Integer call() throws IOException {
    Converter converter = context.converter();
    FormatId source = format(from, converter);
    FormatId target = format(to, converter);
    ConversionOptions options = options();
    if (!reportFormat.equals("json") && !reportFormat.equals("text")) {
      throw usage("--report-format must be json or text");
    }
    byte[] data =
        input.equals("-") ? context.in.readAllBytes() : Files.readAllBytes(Path.of(input));

    ConversionResult result =
        converter.convert(new ConversionRequest(source, target, data, options));

    byte[] reportJson = ReportJsonWriter.write(result.report());
    if (report != null) {
      Files.write(report, reportJson);
    }
    if (reportFormat.equals("json")) {
      context.err.println(new String(reportJson, StandardCharsets.UTF_8));
    } else {
      context.err.print(TextReportRenderer.render(result.report(), strict));
    }
    if (result.outcome() == Outcome.FAILED) {
      // validation incomplete under system load: not an invalid input, the caller should retry
      boolean incomplete =
          result.report().entries().stream()
              .anyMatch(e -> ReasonCode.VALIDATION_INCOMPLETE.equals(e.code()));
      return incomplete ? CliMain.EXIT_INTERNAL : CliMain.EXIT_FAILED;
    }
    byte[] document = result.output().orElseThrow();
    if (output != null) {
      Files.write(output, document);
    } else {
      context.out.write(document);
      context.out.flush();
    }
    return CliMain.EXIT_OK;
  }

  private FormatId format(String name, Converter converter) {
    FormatId id;
    try {
      id = FormatId.fromExternalName(name);
    } catch (IllegalArgumentException e) {
      throw usage("Unknown format: " + name);
    }
    boolean known =
        id.isChEmed()
            || converter.supportedConversions().stream()
                .map(ConversionPair::source)
                .anyMatch(id::equals);
    if (!known) {
      throw usage("Unknown format: " + name);
    }
    return id;
  }

  private ConversionOptions options() throws IOException {
    ConversionOptions options = ConversionOptions.defaults().withStrict(strict);
    if (language != null) {
      if (!ConversionOptions.LANGUAGES.contains(language)) {
        throw usage("--language must be one of " + ConversionOptions.LANGUAGES);
      }
      options = options.withDefaultLanguage(language);
    }
    if (confidentiality != null) {
      try {
        options =
            options.withConfidentiality(
                Confidentiality.valueOf(confidentiality.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException e) {
        throw usage("--confidentiality must be normal, restricted or secret");
      }
    }
    if (patientId != null) {
      int sep = patientId.indexOf('|');
      if (sep <= 0 || sep == patientId.length() - 1) {
        throw usage("--patient-id must be <system>|<value>");
      }
      options =
          options.withPatientIdentifier(
              new PatientIdentifier(patientId.substring(0, sep), patientId.substring(sep + 1)));
    }
    if (author != null) {
      options = options.withAuthor(readAuthor(author));
    }
    return options;
  }

  private AuthorDetails readAuthor(Path file) throws IOException {
    JsonNode n;
    try {
      n = MAPPER.readTree(file.toFile());
    } catch (IOException e) {
      throw usage("--author file is not valid JSON");
    }
    JsonNode org = n.path("organization");
    return new AuthorDetails(
        text(n, "gln"),
        text(n, "given"),
        text(n, "family"),
        org.isMissingNode()
            ? null
            : new OrganizationDetails(
                text(org, "name"),
                text(org, "street"),
                text(org, "zip"),
                text(org, "city"),
                text(org, "country")));
  }

  private static String text(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }

  private ParameterException usage(String message) {
    return new ParameterException(spec.commandLine(), message);
  }
}
