package ch.emed.converter.core.spi;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.SignatureData;
import ch.emed.converter.core.report.ReportCollector;
import java.util.Objects;
import java.util.Optional;

/**
 * Context of one conversion passed to format modules. Not thread-safe; one instance per request.
 */
public final class MappingContext {

  private final ConversionOptions options;
  private final ReportCollector report;
  private final ChEmedValidator validator;
  private SignatureData signature;

  /**
   * Creates a context without a validator.
   *
   * @param options conversion options (incl. id strategy and optional clock)
   * @param report collector for report entries
   */
  public MappingContext(ConversionOptions options, ReportCollector report) {
    this(options, report, null);
  }

  /**
   * Creates a context.
   *
   * @param options conversion options (incl. id strategy and optional clock)
   * @param report collector for report entries
   * @param validator the converter's validator, or null if none is configured
   */
  public MappingContext(
      ConversionOptions options, ReportCollector report, ChEmedValidator validator) {
    this.options = Objects.requireNonNull(options, "options");
    this.report = Objects.requireNonNull(report, "report");
    this.validator = validator;
  }

  public ConversionOptions options() {
    return options;
  }

  public ReportCollector report() {
    return report;
  }

  /**
   * The converter's validator, if one is configured (feature 004, research R3). Format modules
   * whose format is FHIR validate their input and output with it; others ignore it.
   *
   * @return the validator
   */
  public Optional<ChEmedValidator> validator() {
    return Optional.ofNullable(validator);
  }

  /**
   * Records the signature data of a signed input (feature 003); at most once per conversion.
   *
   * @param value raw signature data
   */
  public void setSignature(SignatureData value) {
    Objects.requireNonNull(value, "value");
    if (signature != null) {
      throw new IllegalStateException("Signature data already set");
    }
    signature = value;
  }

  /**
   * Signature data of a signed input, if any.
   *
   * @return the signature data
   */
  public Optional<SignatureData> signature() {
    return Optional.ofNullable(signature);
  }
}
