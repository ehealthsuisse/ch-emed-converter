package ch.emed.converter.chmed16a;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader;
import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.DocumentType;
import ch.emed.converter.chmed16a.io.Chmed16aJsonReader;
import ch.emed.converter.chmed16a.io.Chmed16aJsonWriter;
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
import ch.emed.converter.core.spi.FormatModule;
import ch.emed.converter.core.spi.MappingContext;
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
  public Bundle toChEmed(byte[] input, MappingContext ctx) {
    Medication med = Chmed16aJsonReader.read(input, ctx.options(), ctx.report());
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
