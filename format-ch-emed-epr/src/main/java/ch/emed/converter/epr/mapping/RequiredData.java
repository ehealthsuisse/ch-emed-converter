package ch.emed.converter.epr.mapping;

import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.Confidentiality;
import ch.emed.converter.core.api.OrganizationDetails;
import ch.emed.converter.core.api.PatientIdentifier;
import ch.emed.converter.core.api.TreatmentPlanReference;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.MappingRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.DocumentConfidentiality;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.Resource;

/**
 * Data CH EMED EPR requires beyond CH EMED (mapping rules M-EPR-23 and M-EPR-24, research R9, R15,
 * F4 and F8, data model §6). Missing data is taken only from the conversion options and reported as
 * {@code DERIVED_VALUE}; otherwise {@code MISSING_REQUIRED_DATA} names the element path, never a
 * value. Nothing is invented: no organisation, no device, no reference.
 */
public final class RequiredData {

  static final String EXT_TREATMENT_PLAN =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-treatmentplan";
  static final String EXT_LAST_CONSIDERED =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-last-considered-document";
  static final String EXT_CONFIDENTIALITY =
      "http://fhir.ch/ig/ch-core/StructureDefinition/ch-ext-epr-confidentialitycode";
  static final String URI = "urn:ietf:rfc:3986";
  static final String GLN = "urn:oid:2.51.1.3";
  static final String SNOMED = "http://snomed.info/sct";

  /**
   * Completes the EPR document in place.
   *
   * @param epr the document with CH EMED EPR profiles
   * @param type its document type
   * @param ctx mapping context with options and report
   * @return false if required data is missing (ERROR entries added)
   */
  @MappingRule({"M-EPR-23", "M-EPR-24"})
  public boolean complete(Bundle epr, EprDocumentType type, MappingContext ctx) {
    Composition composition = (Composition) epr.getEntryFirstRep().getResource();
    boolean ok = patient(epr, composition, ctx);
    ok &= author(epr, composition, type, ctx);
    confidentiality(composition, ctx);
    if (type != EprDocumentType.MTP) {
      ok &= treatmentPlans(epr, composition, type, ctx);
    }
    return ok;
  }

  private static boolean patient(Bundle epr, Composition composition, MappingContext ctx) {
    int index = index(epr, composition.getSubject());
    if (index < 0 || !(epr.getEntry().get(index).getResource() instanceof Patient patient)) {
      missing(ctx, "Bundle.entry[0].resource.subject", "The patient is required by CH EMED EPR");
      return false;
    }
    boolean identified =
        patient.getIdentifier().stream().anyMatch(i -> i.hasSystem() && i.hasValue());
    if (identified) {
      return true;
    }
    String path = "Bundle.entry[" + index + "].resource.identifier";
    Optional<PatientIdentifier> option = ctx.options().patientIdentifier();
    if (option.isEmpty()) {
      missing(
          ctx,
          path,
          "A patient identifier with system and value is required by CH EMED EPR; supply it as"
              + " option patientIdentifier");
      return false;
    }
    patient.addIdentifier().setSystem(option.get().system()).setValue(option.get().value());
    derived(ctx, path, "Patient identifier taken from the conversion options");
    return true;
  }

  private static boolean author(
      Bundle epr, Composition composition, EprDocumentType type, MappingContext ctx) {
    String path = "Bundle.entry[0].resource.author";
    if (type == EprDocumentType.PMLC) {
      // F8 (decision 2026-11-09): the author of a PMLC is the community's aggregation service
      boolean device =
          composition.getAuthor().size() == 1
              && resource(epr, composition.getAuthorFirstRep()) instanceof Device;
      if (!device) {
        missing(
            ctx,
            path,
            "The author of a CH EMED EPR medication card (PMLC) is the aggregation service of the"
                + " community, a Device; the converter does not create it");
      }
      return device;
    }
    boolean ok = true;
    List<Reference> authors = new ArrayList<>(composition.getAuthor());
    for (int i = 0; i < authors.size(); i++) {
      Resource author = resource(epr, authors.get(i));
      if (author instanceof Practitioner practitioner) {
        Reference role = roleOf(epr, practitioner, ctx, path + "[" + i + "]");
        if (role == null) {
          ok = false;
        } else {
          composition.getAuthor().set(i, role);
        }
      } else if (author instanceof Organization organization) {
        Reference role = personFor(epr, organization, ctx, path + "[" + i + "]");
        if (role == null) {
          ok = false;
        } else {
          composition.getAuthor().set(i, role);
        }
      }
    }
    boolean person =
        composition.getAuthor().stream()
            .map(a -> resource(epr, a))
            .anyMatch(
                r ->
                    r instanceof PractitionerRole
                        || r instanceof Patient
                        || r instanceof RelatedPerson);
    if (ok && !person) {
      missing(
          ctx,
          path,
          "A person author (PractitionerRole, Patient or RelatedPerson) is required by CH EMED"
              + " EPR; supply it as option author");
      return false;
    }
    return ok;
  }

  /** A Practitioner author becomes its PractitionerRole: from the document, or the option. */
  private static Reference roleOf(
      Bundle epr, Practitioner practitioner, MappingContext ctx, String path) {
    String practitionerUrl = fullUrl(epr, practitioner);
    for (Bundle.BundleEntryComponent entry : epr.getEntry()) {
      if (entry.getResource() instanceof PractitionerRole role
          && practitionerUrl != null
          && practitionerUrl.equals(referenceUrl(epr, role.getPractitioner()))) {
        derived(ctx, path, "Practitioner author replaced by its PractitionerRole of the document");
        return new Reference(entry.getFullUrl());
      }
    }
    Optional<OrganizationDetails> org =
        ctx.options().author().map(AuthorDetails::organization).filter(RequiredData::complete);
    if (org.isEmpty()) {
      missing(
          ctx,
          path,
          "CH EMED EPR requires the author as PractitionerRole; supply the organisation as option"
              + " author");
      return null;
    }
    String orgUrl = add(epr, organization(org.get()), ctx, "/Author/Organization");
    PractitionerRole role = new PractitionerRole();
    role.getMeta().addProfile(ProfileMap.EPR + "ch-emed-epr-practitionerrole");
    role.setPractitioner(new Reference(practitionerUrl));
    role.setOrganization(new Reference(orgUrl));
    String roleUrl = add(epr, role, ctx, "/Author/PractitionerRole");
    derived(ctx, path, "Author PractitionerRole built with the organisation of the options");
    return new Reference(roleUrl);
  }

  /** An Organization author gets the practitioner of the option. */
  private static Reference personFor(
      Bundle epr, Organization organization, MappingContext ctx, String path) {
    Optional<AuthorDetails> option =
        ctx.options().author().filter(a -> present(a.gln()) && present(a.family()));
    if (option.isEmpty()) {
      missing(
          ctx,
          path,
          "CH EMED EPR requires a person author; supply the practitioner (GLN, name) as option"
              + " author");
      return null;
    }
    Practitioner practitioner = new Practitioner();
    practitioner.getMeta().addProfile(ProfileMap.EPR + "ch-emed-epr-practitioner");
    practitioner.addIdentifier().setSystem(GLN).setValue(option.get().gln());
    var name = practitioner.addName().setFamily(option.get().family());
    if (present(option.get().given())) {
      name.addGiven(option.get().given());
    }
    String practitionerUrl = add(epr, practitioner, ctx, "/Author/Practitioner");
    PractitionerRole role = new PractitionerRole();
    role.getMeta().addProfile(ProfileMap.EPR + "ch-emed-epr-practitionerrole");
    role.setPractitioner(new Reference(practitionerUrl));
    role.setOrganization(new Reference(fullUrl(epr, organization)));
    String roleUrl = add(epr, role, ctx, "/Author/PractitionerRole");
    derived(ctx, path, "Author PractitionerRole built with the practitioner of the options");
    return new Reference(roleUrl);
  }

  private static Organization organization(OrganizationDetails details) {
    Organization organization = new Organization();
    organization.getMeta().addProfile(ProfileMap.EPR + "ch-emed-epr-organization");
    organization.setName(details.name());
    var address = organization.addAddress().addLine(details.street());
    address.setPostalCode(details.zip()).setCity(details.city());
    if (present(details.country())) {
      address.setCountry(details.country());
    }
    return organization;
  }

  private static boolean complete(OrganizationDetails o) {
    return o != null
        && present(o.name())
        && present(o.street())
        && present(o.zip())
        && present(o.city());
  }

  /** Confidentiality: never missing; filled from the option (default NORMAL), as M-DOC-09. */
  private static void confidentiality(Composition composition, MappingContext ctx) {
    Confidentiality option = ctx.options().confidentiality();
    boolean changed = false;
    if (!composition.hasConfidentiality()) {
      composition.setConfidentiality(DocumentConfidentiality.fromCode(option.hl7Code()));
      changed = true;
    }
    if (composition.getConfidentialityElement().getExtensionByUrl(EXT_CONFIDENTIALITY) == null) {
      composition
          .getConfidentialityElement()
          .addExtension(
              new Extension(
                  EXT_CONFIDENTIALITY,
                  new CodeableConcept(
                      new Coding(SNOMED, option.snomedCode(), option.snomedDisplay()))));
      changed = true;
    }
    if (changed) {
      derived(
          ctx,
          "Bundle.entry[0].resource.confidentiality",
          "Document confidentiality set from the conversion options");
    }
  }

  /** R15: treatment-plan references and, for PMLC, the last considered document. */
  private static boolean treatmentPlans(
      Bundle epr, Composition composition, EprDocumentType type, MappingContext ctx) {
    List<DomainResource> entries = new ArrayList<>();
    List<String> paths = new ArrayList<>();
    for (Composition.SectionComponent section : composition.getSection()) {
      for (Reference ref : section.getEntry()) {
        int index = index(epr, ref);
        Resource r = index < 0 ? null : epr.getEntry().get(index).getResource();
        boolean entry =
            type == EprDocumentType.PRE
                ? r instanceof MedicationRequest
                : r instanceof MedicationStatement;
        if (entry) {
          entries.add((DomainResource) r);
          paths.add("Bundle.entry[" + index + "].resource");
        }
      }
    }
    List<Integer> without = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).getExtensionByUrl(EXT_TREATMENT_PLAN) == null) {
        without.add(i);
      }
    }
    List<TreatmentPlanReference> references = ctx.options().treatmentPlanReferences();
    if (!without.isEmpty() && references.isEmpty()) {
      for (int i : without) {
        missing(
            ctx,
            paths.get(i) + ".extension",
            "CH EMED EPR requires a reference to the treatment plan (MTP) of this entry; supply"
                + " it as option treatmentPlanReferences");
      }
      return false;
    }
    if (!references.isEmpty() && references.size() != without.size()) {
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.TREATMENT_PLAN_REFERENCES_MISMATCH,
                  Phase.MAP,
                  "Bundle.entry[0].resource.section",
                  references.size()
                      + " treatment-plan reference(s) given for "
                      + without.size()
                      + " entr"
                      + (without.size() == 1 ? "y" : "ies")
                      + " without one"));
      return false;
    }
    for (int k = 0; k < without.size(); k++) {
      TreatmentPlanReference reference = references.get(k);
      Extension extension = new Extension(EXT_TREATMENT_PLAN);
      extension.addExtension("id", identifier(reference.statementId()));
      extension.addExtension("externalDocumentId", identifier(reference.documentId()));
      entries.get(without.get(k)).getExtension().add(0, extension);
      derived(
          ctx,
          paths.get(without.get(k)) + ".extension",
          "Treatment-plan reference taken from the conversion options");
    }
    if (type == EprDocumentType.PMLC) {
      for (int i = 0; i < entries.size(); i++) {
        DomainResource entry = entries.get(i);
        if (entry.getExtensionByUrl(EXT_LAST_CONSIDERED) != null) {
          continue;
        }
        Extension plan = entry.getExtensionByUrl(EXT_TREATMENT_PLAN);
        Extension document = plan.getExtensionByUrl("externalDocumentId");
        if (document == null) {
          missing(ctx, paths.get(i) + ".extension", "The last considered document is missing");
          return false;
        }
        Identifier id = ((Identifier) document.getValue()).copy();
        entry.addExtension(new Extension(EXT_LAST_CONSIDERED, id));
        derived(
            ctx,
            paths.get(i) + ".extension",
            "Last considered document set to the document of the treatment-plan reference");
      }
    }
    return true;
  }

  private static Identifier identifier(String urn) {
    return new Identifier().setSystem(URI).setValue(urn);
  }

  private static String add(Bundle epr, Resource resource, MappingContext ctx, String path) {
    String documentId = epr.getIdentifier().getValue();
    String id = ctx.options().idStrategy().id(documentId == null ? "" : documentId, path);
    resource.setId(id);
    String url = "urn:uuid:" + id;
    epr.addEntry().setFullUrl(url).setResource(resource);
    return url;
  }

  private static Resource resource(Bundle bundle, Reference ref) {
    int index = index(bundle, ref);
    return index < 0 ? null : bundle.getEntry().get(index).getResource();
  }

  private static String referenceUrl(Bundle bundle, Reference ref) {
    int index = index(bundle, ref);
    return index < 0 ? null : bundle.getEntry().get(index).getFullUrl();
  }

  private static String fullUrl(Bundle bundle, Resource resource) {
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() == resource) {
        return entry.getFullUrl();
      }
    }
    return null;
  }

  /** Index of the entry a reference points to, by full URL or {@code Type/id}; -1 if none. */
  static int index(Bundle bundle, Reference ref) {
    if (ref == null || !ref.hasReference()) {
      return -1;
    }
    String target = ref.getReference();
    for (int i = 0; i < bundle.getEntry().size(); i++) {
      Bundle.BundleEntryComponent entry = bundle.getEntry().get(i);
      Resource resource = entry.getResource();
      if (target.equals(entry.getFullUrl())
          || (entry.hasFullUrl() && entry.getFullUrl().endsWith("/" + target))
          || (resource != null
              && target.equals(resource.fhirType() + "/" + resource.getIdPart()))) {
        return i;
      }
    }
    return -1;
  }

  private static boolean present(String s) {
    return s != null && !s.isBlank();
  }

  private static void missing(MappingContext ctx, String path, String message) {
    ctx.report().add(ReportEntry.error(ReasonCode.MISSING_REQUIRED_DATA, Phase.MAP, path, message));
  }

  private static void derived(MappingContext ctx, String path, String message) {
    ctx.report().add(ReportEntry.information(ReasonCode.DERIVED_VALUE, Phase.MAP, path, message));
  }
}
