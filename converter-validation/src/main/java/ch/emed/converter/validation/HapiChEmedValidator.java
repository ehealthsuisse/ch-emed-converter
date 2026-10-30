package ch.emed.converter.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.core.spi.ChEmedValidator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offline CH EMED validation with HAPI's wrapper of the official HL7 validator and the vendored IG
 * packages (research R4). No remote terminology server. The context is built once; instances are
 * thread-safe and should be reused.
 */
public final class HapiChEmedValidator implements ChEmedValidator {

  private static final Logger LOG = LoggerFactory.getLogger(HapiChEmedValidator.class);
  private static final String PACKAGE_DIRECTORY = "packages";

  /**
   * Validator message ids caused by the documented offline limitation (no terminology server,
   * research R4): code systems such as LOINC, SNOMED CT or GTIN cannot be expanded locally. These
   * WARNINGs are reported as INFORMATION; ERRORs are never downgraded. Listed in the mapping
   * specification, section "Validation limitations".
   */
  static final Set<String> TERMINOLOGY_LIMITATION_IDS =
      Set.of(
          "Terminology_PassThrough_TX_Message",
          "TERMINOLOGY_TX_SYSTEM_UNSUPPORTED",
          "Terminology_TX_NoValid_2_CC");

  /** Message id of the HL7 validator when a pattern check exceeds its fixed time limit. */
  static final String REGEX_TIMEOUT_ID = "TYPE_SPECIFIC_CHECKS_DT_PRIMITIVE_REGEX_EXCEPTION";

  /** Pauses before the retries of a validation run that hit the time limit. */
  static final long[] RETRY_PAUSES_MILLIS = {200, 1000};

  private final ValidationSupportChain chain;
  private final FhirValidator validator;
  private final String rootPackage;

  public HapiChEmedValidator() {
    long start = System.nanoTime();
    FhirContext ctx = ChEmedJson.context();
    NpmPackageValidationSupport npm = new NpmPackageValidationSupport(ctx);
    for (PackageLoader.PackageRef ref : PackageLoader.loadManifest(PACKAGE_DIRECTORY)) {
      try {
        npm.loadPackageFromClasspath("classpath:" + PACKAGE_DIRECTORY + "/" + ref.file());
      } catch (IOException e) {
        throw new IllegalStateException("Cannot load package " + ref.coordinates(), e);
      }
    }
    DefaultProfileValidationSupport core = new DefaultProfileValidationSupport(ctx);
    this.chain =
        new ValidationSupportChain(
            core,
            new VersionedCoreProfileSupport(ctx, core),
            npm,
            new CommonCodeSystemsTerminologyService(ctx),
            new InMemoryTerminologyServerValidationSupport(ctx),
            new SnapshotGeneratingValidationSupport(ctx));
    FhirInstanceValidator module = new FhirInstanceValidator(new CachingValidationSupport(chain));
    this.validator = ctx.newValidator().registerValidatorModule(module);
    this.rootPackage = PackageLoader.rootPackage(PACKAGE_DIRECTORY);
    LOG.info("validation context ready in {} ms", (System.nanoTime() - start) / 1_000_000);
  }

  @Override
  public List<ReportEntry> validate(Bundle bundle, Phase phase) {
    if (phase != Phase.VALIDATE_INPUT && phase != Phase.VALIDATE_OUTPUT) {
      throw new IllegalArgumentException("Validation phase expected, got " + phase);
    }
    return validateWithRetry(
        () -> validator.validateWithResult(bundle).getMessages(), phase, Thread::sleep);
  }

  /**
   * Runs the validation and repeats it, after a pause, while a run hit the HL7 validator's pattern
   * time limit ({@value #REGEX_TIMEOUT_ID}): the limit is a fixed 500 ms of wall-clock time, so
   * under CPU load a valid document gets errors (plus consequential slicing errors) that depend on
   * timing, not on the input (constitution V). If every run is affected, the result is a single
   * {@code VALIDATION_INCOMPLETE} error instead of the unreliable messages.
   */
  static List<ReportEntry> validateWithRetry(
      Supplier<List<SingleValidationMessage>> run, Phase phase, Pause pause) {
    List<SingleValidationMessage> messages = run.get();
    for (int retry = 0; hitTimeLimit(messages) && retry < RETRY_PAUSES_MILLIS.length; retry++) {
      LOG.warn(
          "validation hit the HL7 pattern time limit (system load); retry {} of {}",
          retry + 1,
          RETRY_PAUSES_MILLIS.length);
      try {
        pause.sleep(RETRY_PAUSES_MILLIS[retry]);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return List.of(incomplete(phase));
      }
      messages = run.get();
    }
    if (hitTimeLimit(messages)) {
      LOG.warn("validation incomplete: HL7 pattern time limit hit in every run");
      return List.of(incomplete(phase));
    }
    return entries(messages, phase);
  }

  /** Pause between validation runs; replaceable in tests. */
  @FunctionalInterface
  interface Pause {
    void sleep(long millis) throws InterruptedException;
  }

  private static boolean hitTimeLimit(List<SingleValidationMessage> messages) {
    return messages.stream().anyMatch(m -> REGEX_TIMEOUT_ID.equals(m.getMessageId()));
  }

  private static ReportEntry incomplete(Phase phase) {
    return ReportEntry.of(
        Severity.ERROR,
        ReasonCode.VALIDATION_INCOMPLETE,
        phase,
        "Bundle",
        "The CH EMED validation could not be completed under the current system load (pattern"
            + " checks of the HL7 validator timed out); retry later.");
  }

  private static List<ReportEntry> entries(List<SingleValidationMessage> messages, Phase phase) {
    List<ReportEntry> entries = new ArrayList<>();
    for (SingleValidationMessage message : messages) {
      Severity severity = severity(message.getSeverity());
      if (severity == Severity.WARNING
          && TERMINOLOGY_LIMITATION_IDS.contains(message.getMessageId())) {
        severity = Severity.INFORMATION;
      }
      entries.add(
          ReportEntry.of(
              severity,
              ReasonCode.FHIR_VALIDATION,
              phase,
              message.getLocationString() == null ? "Bundle" : message.getLocationString(),
              ValidationMessageSanitizer.sanitize(message.getMessageId(), message.getMessage())));
    }
    return entries;
  }

  private static Severity severity(ResultSeverityEnum severity) {
    return switch (severity) {
      case FATAL, ERROR -> Severity.ERROR;
      case WARNING -> Severity.WARNING;
      case INFORMATION -> Severity.INFORMATION;
    };
  }

  @Override
  public String pinnedSpecification() {
    return rootPackage;
  }

  /** Validation support chain, exposed for tests (no remote terminology server). */
  ValidationSupportChain supportChain() {
    return chain;
  }
}
