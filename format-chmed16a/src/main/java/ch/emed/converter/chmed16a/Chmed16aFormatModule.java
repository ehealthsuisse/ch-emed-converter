package ch.emed.converter.chmed16a;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader;
import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.DocumentType;
import ch.emed.converter.chmed16a.io.Chmed16aJsonReader;
import ch.emed.converter.chmed16a.io.Chmed16aJsonWriter;
import ch.emed.converter.chmed16a.io.Chmed16aPayloadEncoder;
import ch.emed.converter.chmed16a.io.ErezeptUrlParser;
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
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.FormatModule;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.qr.QrException;
import ch.emed.converter.qr.QrImageReader;
import ch.emed.converter.qr.QrImageWriter;
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
    return List.of("json", "qr-payload", "erezept-url", "qr-image");
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
      case EREZEPT_URL -> readErezept(new String(input, StandardCharsets.UTF_8), ctx);
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
      case EREZEPT_URL -> readErezept(text, ctx);
      default -> fail(ctx, ReasonCode.PARSE_ERROR, "QR code does not hold a CHMED payload");
    };
  }

  /**
   * Signed eRezept URL: the embedded prescription, with the signature data recorded raw and
   * unverified beside the document (research R6).
   */
  @MappingRule("M-URL-04")
  private static Medication readErezept(String url, MappingContext ctx) {
    ErezeptUrlParser.Parsed parsed =
        ErezeptUrlParser.parse(url, ctx.options().maxDecodedSize(), ctx.report());
    if (parsed == null) {
      return null;
    }
    Medication med =
        Chmed16aJsonReader.read(
            parsed.payload().getBytes(StandardCharsets.US_ASCII), ctx.options(), ctx.report());
    if (med == null) {
      return null;
    }
    if (!med.isPrescription()) {
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.UNSUPPORTED_DOCUMENT_TYPE,
                  Phase.PARSE,
                  "/MedType",
                  "A signed eRezept must hold a prescription (MedType 3)"));
      return null;
    }
    ctx.setSignature(parsed.signature());
    ctx.report()
        .add(
            ReportEntry.information(
                ReasonCode.SIGNATURE_NOT_VERIFIED,
                Phase.PARSE,
                "/",
                "The eRezept signature and the prescription status were not verified"));
    return med;
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
    if (doc.type() == DocumentType.MEDICATION_PRESCRIPTION
        && ctx.options().outputForm() == OutputForm.QR_IMAGE) {
      refuseImageOfPrescription(ctx);
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
    if (ctx.report().hasErrors()) {
      return null;
    }
    return write(Chmed16aJsonWriter.write(result), ctx);
  }

  @Override
  public List<OutputForm> outputForms() {
    return List.of(OutputForm.DOCUMENT, OutputForm.QR_PAYLOAD, OutputForm.QR_IMAGE);
  }

  /**
   * A QR image of an unsigned prescription could be mistaken for a signed eRezept; prescriptions
   * are output as unsigned payload only, which the HIN erxproxy signs (clarification 2026-11-01).
   */
  @MappingRule("M-QR-13")
  private static void refuseImageOfPrescription(MappingContext ctx) {
    ctx.report()
        .add(
            ReportEntry.error(
                ReasonCode.QR_IMAGE_NOT_ALLOWED,
                Phase.MAP,
                "/",
                "No QR image for a prescription: prescriptions are output as unsigned payload only,"
                    + " to be signed by the HIN erxproxy"));
  }

  /** Writes the JSON in the requested output form (feature 003); null after an ERROR entry. */
  @MappingRule({"M-QR-10", "M-QR-11", "M-QR-12"})
  private static byte[] write(byte[] json, MappingContext ctx) {
    return switch (ctx.options().outputForm()) {
      case DOCUMENT -> json;
      case QR_PAYLOAD -> Chmed16aPayloadEncoder.encode(json);
      case QR_IMAGE -> {
        String payload = new String(Chmed16aPayloadEncoder.encode(json), StandardCharsets.US_ASCII);
        try {
          yield QrImageWriter.write(payload);
        } catch (QrException e) {
          ctx.report().add(ReportEntry.error(reasonCode(e.kind()), Phase.MAP, "/", e.getMessage()));
          yield null;
        }
      }
    };
  }
}
