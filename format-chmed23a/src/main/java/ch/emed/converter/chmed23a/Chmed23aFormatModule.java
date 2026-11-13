package ch.emed.converter.chmed23a;

import ch.emed.converter.chmed23a.io.Chmed23aJsonReader;
import ch.emed.converter.chmed23a.io.InputFormDetector;
import ch.emed.converter.chmed23a.io.TransmissionFormat;
import ch.emed.converter.chmed23a.mapping.Documents;
import ch.emed.converter.chmed23a.mapping.MappingRule;
import ch.emed.converter.chmed23a.mapping.MappingScope;
import ch.emed.converter.chmed23a.mapping.MedicationCardBuilder;
import ch.emed.converter.chmed23a.mapping.MedicationPrescriptionBuilder;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.validation.Chmed23aValidator;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
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

/**
 * CHMED23A format module (eMediplan, deprecated by IG eMediplan; feature 006). Stateless and
 * thread-safe.
 */
public final class Chmed23aFormatModule implements FormatModule {

  /** Pinned CHMED23A specification (docs/specifications.md, research R1). */
  public static final String PINNED_SPECIFICATION =
      "CHMED23A (ig-emediplan/specification@0b579aa6)";

  @Override
  public FormatId format() {
    return FormatId.CHMED23A;
  }

  @Override
  public String pinnedSpecification() {
    return PINNED_SPECIFICATION;
  }

  @Override
  public boolean deprecated() {
    return true;
  }

  @Override
  public List<String> inputForms() {
    return List.of("json", "qr-payload", "qr-image");
  }

  @Override
  public List<OutputForm> outputForms() {
    return List.of(OutputForm.DOCUMENT, OutputForm.QR_PAYLOAD, OutputForm.QR_IMAGE);
  }

  @Override
  public List<String> documentTypes() {
    return List.of("MP", "Rx");
  }

  @Override
  public Bundle toChEmed(byte[] input, MappingContext ctx) {
    byte[] json = json(input, ctx);
    if (json == null) {
      return null;
    }
    Medication med = Chmed23aJsonReader.read(json, ctx.report());
    if (med == null || ctx.report().hasErrors()) {
      return null;
    }
    Chmed23aValidator.validate(med, ctx.report());
    if (ctx.report().hasErrors()) {
      return null;
    }
    MappingScope scope = new MappingScope(Documents.documentId(med, ctx), ctx);
    Bundle bundle =
        med.isPlan()
            ? MedicationCardBuilder.build(med, scope)
            : MedicationPrescriptionBuilder.build(med, scope);
    return ctx.report().hasErrors() ? null : bundle;
  }

  /** The CHMED23A JSON of any input form (M23-IN-01); null after an ERROR entry. */
  private static byte[] json(byte[] input, MappingContext ctx) {
    return switch (InputFormDetector.detect(input)) {
      case IMAGE -> image(input, ctx);
      case UNSUPPORTED_IMAGE -> fail(ctx, ReasonCode.IMAGE_UNREADABLE, "Unsupported image format");
      case PAYLOAD ->
          TransmissionFormat.decode(
              InputFormDetector.text(input), ctx.options().maxDecodedSize(), ctx.report());
      case CHMED16A ->
          fail(ctx, ReasonCode.UNSUPPORTED_VERSION, "Input is CHMED16A; use source chmed16a");
      case FHIR_JSON ->
          fail(ctx, ReasonCode.PARSE_ERROR, "Input is a FHIR resource; use source ch-emed");
      case JSON -> InputFormDetector.text(input).getBytes(StandardCharsets.UTF_8);
      case UNKNOWN ->
          fail(
              ctx, ReasonCode.PARSE_ERROR, "Input is neither CHMED23A JSON nor a CHMED23A payload");
    };
  }

  /** QR image: one code holding a whole CHMED23A payload (M23-QR-01..04). */
  @MappingRule({"M23-QR-01", "M23-QR-02", "M23-QR-03", "M23-QR-04"})
  private static byte[] image(byte[] image, MappingContext ctx) {
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
              .toString()
              .strip();
    } catch (CharacterCodingException e) {
      return fail(ctx, ReasonCode.PARSE_ERROR, "QR code content is not UTF-8 text");
    }
    return switch (InputFormDetector.detectText(text)) {
      case PAYLOAD ->
          TransmissionFormat.isChunk(text)
              ? fail(
                  ctx,
                  ReasonCode.CHUNK_INVALID,
                  "The QR code holds a chunk; chunks are read as text only, an image must hold"
                      + " the whole document")
              : TransmissionFormat.decode(text, ctx.options().maxDecodedSize(), ctx.report());
      case CHMED16A ->
          fail(
              ctx,
              ReasonCode.UNSUPPORTED_VERSION,
              "QR code holds a CHMED16A payload; use source chmed16a");
      default -> fail(ctx, ReasonCode.PARSE_ERROR, "QR code does not hold a CHMED23A payload");
    };
  }

  static String reasonCode(QrException.Kind kind) {
    return switch (kind) {
      case UNREADABLE -> ReasonCode.IMAGE_UNREADABLE;
      case TOO_LARGE -> ReasonCode.IMAGE_TOO_LARGE;
      case NOT_FOUND -> ReasonCode.QR_NOT_FOUND;
      case MULTIPLE -> ReasonCode.QR_MULTIPLE;
      case CAPACITY -> ReasonCode.QR_CAPACITY_EXCEEDED;
    };
  }

  private static <T> T fail(MappingContext ctx, String code, String message) {
    ctx.report().add(ReportEntry.error(code, Phase.PARSE, "/", message));
    return null;
  }

  @Override
  public byte[] fromChEmed(Bundle document, MappingContext ctx) {
    return fail(ctx, ReasonCode.UNSUPPORTED_DOCUMENT_TYPE, "Writing CHMED23A is not yet supported");
  }
}
