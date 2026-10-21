package ch.emed.converter.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Resolves FHIR R4 core profiles referenced with a version suffix, e.g. {@code
 * http://hl7.org/fhir/StructureDefinition/SimpleQuantity|4.0.1}. The IG packages reference core
 * profiles this way (for example {@code MedicationRequest.dispenseRequest.quantity}), while the
 * core definitions of {@code DefaultProfileValidationSupport} are registered without version. The
 * definitions are the official R4 ones; nothing is added or changed.
 */
final class VersionedCoreProfileSupport implements IValidationSupport {

  private static final String CORE_PREFIX = "http://hl7.org/fhir/StructureDefinition/";
  private static final String CORE_VERSION = "4.0.1";

  private final FhirContext context;
  private final IValidationSupport core;

  VersionedCoreProfileSupport(FhirContext context, IValidationSupport core) {
    this.context = context;
    this.core = core;
  }

  @Override
  public FhirContext getFhirContext() {
    return context;
  }

  @Override
  public IBaseResource fetchStructureDefinition(String url) {
    if (url == null || !url.startsWith(CORE_PREFIX)) {
      return null;
    }
    int bar = url.indexOf('|');
    if (bar < 0 || !CORE_VERSION.equals(url.substring(bar + 1))) {
      return null;
    }
    return core.fetchStructureDefinition(url.substring(0, bar));
  }
}
