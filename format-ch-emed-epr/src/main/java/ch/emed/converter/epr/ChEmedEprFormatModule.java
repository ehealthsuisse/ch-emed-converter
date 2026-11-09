package ch.emed.converter.epr;

import ca.uhn.fhir.parser.DataFormatException;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.ChEmedValidator;
import ch.emed.converter.core.spi.FormatModule;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.mapping.ProfileMap;
import ch.emed.converter.epr.mapping.ToChEmedMapper;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;

/**
 * CH EMED EPR (CARA EMED) documents to and from CH EMED (feature 004). Input and output are
 * validated with the converter's validator, reached through the mapping context (research R3).
 * Stateless and thread-safe.
 */
public final class ChEmedEprFormatModule implements FormatModule {

  /** Pinned specification (research R2). */
  public static final String PINNED_SPECIFICATION = "ch.fhir.ig.ch-emed-epr#3.0.0";

  private final ToChEmedMapper toChEmed = new ToChEmedMapper();

  @Override
  public FormatId format() {
    return FormatId.CH_EMED_EPR;
  }

  @Override
  public String pinnedSpecification() {
    return PINNED_SPECIFICATION;
  }

  @Override
  public List<String> inputForms() {
    return List.of("json");
  }

  @Override
  public List<OutputForm> outputForms() {
    return List.of(OutputForm.DOCUMENT, OutputForm.TREATMENT_PLANS);
  }

  @MappingRule({"M-EPR-02", "M-EPR-11"})
  @Override
  public Bundle toChEmed(byte[] input, MappingContext ctx) {
    Bundle epr = parse(input, ctx);
    if (epr == null) {
      return null;
    }
    Optional<EprDocumentType> type = EprDocumentType.of(epr);
    if (type.isEmpty()) {
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.UNSUPPORTED_DOCUMENT_TYPE,
                  Phase.PARSE,
                  "Bundle.entry[0].resource.type",
                  EprDocumentType.unsupportedMessage(epr)));
      return null;
    }
    if (!ProfileMap.declaresEpr(epr)) {
      // research R11: general CH EMED declared as CH EMED EPR would pass the validator, which
      // checks the declared profiles only
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.PARSE_ERROR,
                  Phase.PARSE,
                  "Bundle.meta.profile",
                  "Bundle does not declare a CH EMED EPR document profile; probably general CH"
                      + " EMED; use source ch-emed"));
      return null;
    }
    if (!validate(epr, Phase.VALIDATE_INPUT, ctx)) {
      return null;
    }
    return toChEmed.map(epr, type.get(), ctx);
  }

  @Override
  public byte[] fromChEmed(Bundle document, MappingContext ctx) {
    ctx.report()
        .add(
            ReportEntry.error(
                ReasonCode.UNSUPPORTED_CONVERSION,
                Phase.MAP,
                "/",
                "CH EMED to CH EMED EPR is not yet supported"));
    return null;
  }

  /**
   * Validates a document with the converter's validator and adds the findings to the report.
   *
   * @return true if the validation found no errors
   */
  @MappingRule("M-EPR-03")
  static boolean validate(Bundle bundle, Phase phase, MappingContext ctx) {
    Optional<ChEmedValidator> validator = ctx.validator();
    if (validator.isEmpty()) {
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.VALIDATION_INCOMPLETE,
                  phase,
                  "/",
                  "CH EMED EPR documents are always validated, but no validator is configured"));
      return false;
    }
    List<ReportEntry> entries = validator.get().validate(bundle, phase);
    ctx.report().addAll(entries);
    return entries.stream()
        .noneMatch(e -> e.severity() == ch.emed.converter.core.report.Severity.ERROR);
  }

  private static Bundle parse(byte[] input, MappingContext ctx) {
    try {
      Bundle bundle = ChEmedJson.parse(input);
      if (bundle.getType() == Bundle.BundleType.DOCUMENT) {
        return bundle;
      }
    } catch (DataFormatException | ClassCastException e) {
      // reported below, without content
    }
    ctx.report()
        .add(
            ReportEntry.error(
                ReasonCode.PARSE_ERROR,
                Phase.PARSE,
                "/",
                "Input is not a FHIR JSON document Bundle"));
    return null;
  }
}
