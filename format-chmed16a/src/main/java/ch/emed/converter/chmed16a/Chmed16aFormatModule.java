package ch.emed.converter.chmed16a;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader;
import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.DocumentType;
import ch.emed.converter.chmed16a.io.Chmed16aJsonReader;
import ch.emed.converter.chmed16a.io.Chmed16aJsonWriter;
import ch.emed.converter.chmed16a.io.InputFormDetector;
import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.mapping.MappingScope;
import ch.emed.converter.chmed16a.mapping.MedicationCardBuilder;
import ch.emed.converter.chmed16a.mapping.MedicationPrescriptionBuilder;
import ch.emed.converter.chmed16a.mapping.reverse.MedicationPlanBuilder;
import ch.emed.converter.chmed16a.mapping.reverse.PrescriptionBuilder;
import ch.emed.converter.chmed16a.mapping.reverse.ReverseScope;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.validation.Chmed16aValidator;
import ch.emed.converter.chmed16a.validation.ErezeptPrescriptionRules;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.FormatModule;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.qr.QrException;
import ch.emed.converter.qr.QrImageReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;

/** CHMED16A format module (eMediplan). Stateless and thread-safe. */
public final class Chmed16aFormatModule implements FormatModule {

  /** Pinned CHMED16A specification (docs/specifications.md). */
  public static final String PINNED_SPECIFICATION =
      "CHMED16A1 (ig-emediplan/specification@0b579aa6)";

  @Override
  public FormatId format() {
    return FormatId.CHMED16A;
  }

  @Override
  public String pinnedSpecification() {
    return PINNED_SPECIFICATION;
  }

  @Override
  public List<String> inputForms() {
    return List.of("json", "qr-payload", "qr-image");
  }

  @Override
  public Bundle toChEmed(byte[] input, MappingContext ctx) {
    Medication med = read(input, ctx);
    if (med == null) {
      return null;
    }
    Chmed16aValidator.validate(med, ctx.report());
    if (ctx.report().hasErrors()) {
      return null;
    }
    if (med.isPrescription()) {
      ErezeptPrescriptionRules.validate(med, ctx.report(), Phase.VALIDATE_INPUT);
      Bundle bundle = MedicationPrescriptionBuilder.build(med, new MappingScope(med.id(), ctx));
      return ctx.report().hasErrors() ? null : bundle;
    }
    Bundle bundle = MedicationCardBuilder.build(med, new MappingScope(med.id(), ctx));
    return ctx.report().hasErrors() ? null : bundle;
  }

  /** Reads every input form of data-model.md §1 into the model; null after an ERROR entry. */
  @MappingRule("M-IN-07")
  private static Medication read(byte[] input, MappingContext ctx) {
    return switch (InputFormDetector.detect(input)) {
      case IMAGE -> readImage(input, ctx);
      case UNSUPPORTED_IMAGE -> fail(ctx, ReasonCode.IMAGE_UNREADABLE, "Unsupported image format");
      case EREZEPT_URL ->
          fail(ctx, ReasonCode.PARSE_ERROR, "Signed eRezept URLs are not yet supported");
      case FHIR_JSON ->
          fail(ctx, ReasonCode.PARSE_ERROR, "Input is a FHIR resource; use source ch-emed");
      case PAYLOAD, JSON -> Chmed16aJsonReader.read(input, ctx.options(), ctx.report());
    };
  }

  /** QR code image: one code, content read as UTF-8, then a payload. */
  @MappingRule({"M-QR-01", "M-QR-02", "M-QR-03", "M-QR-04"})
  private static Medication readImage(byte[] image, MappingContext ctx) {
    long maxBytes = ctx.options().maxDecodedSize();
    if (image.length > maxBytes) {
      return fail(
          ctx,
          ReasonCode.IMAGE_TOO_LARGE,
          "Image exceeds the size limit of " + maxBytes + " bytes");
    }
    byte[] content;
    try {
      content = QrImageReader.read(image, QrImageReader.DEFAULT_MAX_PIXELS);
    } catch (QrException e) {
      return fail(ctx, reasonCode(e.kind()), e.getMessage());
    }
    String text;
    try {
      text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(content))
              .toString();
    } catch (CharacterCodingException e) {
      return fail(ctx, ReasonCode.PARSE_ERROR, "QR code content is not UTF-8 text");
    }
    return switch (InputFormDetector.detectText(text)) {
      case PAYLOAD ->
          Chmed16aJsonReader.read(
              text.getBytes(StandardCharsets.UTF_8), ctx.options(), ctx.report());
      case EREZEPT_URL ->
          fail(ctx, ReasonCode.PARSE_ERROR, "Signed eRezept URLs are not yet supported");
      default -> fail(ctx, ReasonCode.PARSE_ERROR, "QR code does not hold a CHMED payload");
    };
  }

  private static String reasonCode(QrException.Kind kind) {
    return switch (kind) {
      case UNREADABLE -> ReasonCode.IMAGE_UNREADABLE;
      case TOO_LARGE -> ReasonCode.IMAGE_TOO_LARGE;
      case NOT_FOUND -> ReasonCode.QR_NOT_FOUND;
      case MULTIPLE -> ReasonCode.QR_MULTIPLE;
      case CAPACITY -> ReasonCode.QR_CAPACITY_EXCEEDED;
    };
  }

  private static Medication fail(MappingContext ctx, String code, String message) {
    ctx.report().add(ReportEntry.error(code, Phase.PARSE, "/", message));
    return null;
  }

  @Override
  public byte[] fromChEmed(Bundle document, MappingContext ctx) {
    ChEmedDocument doc = ChEmedDocumentReader.open(document, ctx.report());
    if (doc == null) {
      return null;
    }
    Medication result;
    if (doc.type() == DocumentType.MEDICATION_PRESCRIPTION) {
      result = PrescriptionBuilder.build(new ReverseScope(doc, ctx));
      if (result != null) {
        ErezeptPrescriptionRules.validate(result, ctx.report(), Phase.VALIDATE_OUTPUT);
      }
    } else {
      result = MedicationPlanBuilder.build(new ReverseScope(doc, ctx));
    }
    if (result == null) {
      return null;
    }
    Chmed16aValidator.validate(result, ctx.report(), Phase.VALIDATE_OUTPUT);
    return ctx.report().hasErrors() ? null : Chmed16aJsonWriter.write(result);
  }
}
