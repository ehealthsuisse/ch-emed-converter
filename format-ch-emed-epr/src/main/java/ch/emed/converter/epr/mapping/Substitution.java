package ch.emed.converter.epr.mapping;

import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.MappingRule;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Resource;

/**
 * Substitution in CH EMED EPR (mapping rule M-EPR-27, research F9): "If absent, substitution is
 * assumed allowed. If present, the value is fixed to none (not allowed)." A CH EMED statement that
 * substitution is allowed ({@code allowedBoolean} true, or a code other than {@code N}) is left
 * out, since its absence says the same; {@code allowedBoolean} false becomes the code {@code N}.
 * Applies to {@code MedicationRequest.substitution} and to the extension {@code
 * ch-emed-ext-substitution} of card and treatment-plan statements.
 */
public final class Substitution {

  static final String SYSTEM =
      "http://terminology.hl7.org/CodeSystem/v3-substanceAdminSubstitution";
  static final String EXT_SUBSTITUTION =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-substitution";

  /**
   * Adapts the substitution statements in place.
   *
   * @param epr the document
   * @param ctx mapping context
   */
  @MappingRule("M-EPR-27")
  public void apply(Bundle epr, MappingContext ctx) {
    for (int i = 0; i < epr.getEntry().size(); i++) {
      Resource resource = epr.getEntry().get(i).getResource();
      String path = "Bundle.entry[" + i + "].resource";
      if (resource instanceof MedicationRequest request && request.hasSubstitution()) {
        request(request, path + ".substitution", ctx);
      } else if (resource instanceof MedicationStatement statement) {
        Extension ext = statement.getExtensionByUrl(EXT_SUBSTITUTION);
        if (ext != null
            && ext.getValue() instanceof CodeableConcept concept
            && !notAllowed(concept)) {
          statement.getExtension().remove(ext);
          allowedLeftOut(ctx, path + ".extension");
        }
      }
    }
  }

  private static void request(MedicationRequest request, String path, MappingContext ctx) {
    var substitution = request.getSubstitution();
    if (substitution.getAllowed() instanceof BooleanType allowed) {
      if (allowed.booleanValue()) {
        request.setSubstitution(null);
        allowedLeftOut(ctx, path);
      } else {
        substitution.setAllowed(none());
        ctx.report()
            .add(
                ReportEntry.information(
                    ReasonCode.DERIVED_VALUE,
                    Phase.MAP,
                    path + ".allowed",
                    "Substitution not allowed expressed as code N, as CH EMED EPR requires"));
      }
    } else if (substitution.getAllowed() instanceof CodeableConcept concept
        && !notAllowed(concept)) {
      request.setSubstitution(null);
      allowedLeftOut(ctx, path);
    }
  }

  private static boolean notAllowed(CodeableConcept concept) {
    return concept.getCoding().stream()
        .anyMatch(c -> SYSTEM.equals(c.getSystem()) && "N".equals(c.getCode()));
  }

  private static CodeableConcept none() {
    return new CodeableConcept(new Coding(SYSTEM, "N", "none"));
  }

  private static void allowedLeftOut(MappingContext ctx, String path) {
    ctx.report()
        .add(
            ReportEntry.information(
                ReasonCode.UNMAPPABLE_ELEMENT,
                Phase.MAP,
                path,
                "Substitution allowed is left out: in CH EMED EPR its absence means allowed"));
  }
}
