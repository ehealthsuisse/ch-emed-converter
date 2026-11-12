package ch.emed.converter.epr.mapping;

import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.MappingRule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Consistency of the references to other documents within one document (mapping rule M-EPR-04,
 * feature 005 research R5, data-model §3). Contradictions are reported as WARNING {@code
 * REFERENCE_INCONSISTENT} with the paths of the extensions concerned, never their identifiers; the
 * document is not changed. Nothing outside the document is resolved.
 *
 * <ul>
 *   <li>C1: one entry id appears with only one document id (covers C2: an entry of a list refers to
 *       its own document by its own reference extension);
 *   <li>C3: the changed statement or request of an advice belongs to the treatment plan or
 *       prescription the observation references;
 *   <li>C4: a dispense of a list and the prescription it references name the same treatment plan.
 * </ul>
 */
@MappingRule("M-EPR-04")
public final class ReferenceConsistency {

  static final String BASE = "http://fhir.ch/ig/ch-emed/StructureDefinition/";
  static final String TREATMENT_PLAN = BASE + "ch-emed-ext-treatmentplan";
  static final String PRESCRIPTION = BASE + "ch-emed-ext-prescription";
  static final String DISPENSE = BASE + "ch-emed-ext-dispense";
  static final String ADVICE = BASE + "ch-emed-ext-pharmaceuticaladvice";
  static final String STATEMENT_CHANGED = BASE + "ch-emed-ext-medicationstatement-changed";
  static final String REQUEST_CHANGED = BASE + "ch-emed-ext-medicationrequest-changed";

  private static final List<String> REFERENCES =
      List.of(TREATMENT_PLAN, PRESCRIPTION, DISPENSE, ADVICE);

  /** One reference extension found in the document. */
  private record Ref(String url, String id, String document, String path, DomainResource owner) {}

  /**
   * Checks the references of a document and reports contradictions.
   *
   * @param bundle CH EMED or CH EMED EPR document
   * @param type its document type
   * @param report receives one WARNING per contradiction
   */
  public void check(Bundle bundle, EprDocumentType type, ReportCollector report) {
    List<Ref> refs = new ArrayList<>();
    Map<DomainResource, String> paths = new LinkedHashMap<>();
    for (int i = 0; i < bundle.getEntry().size(); i++) {
      Resource r = bundle.getEntry().get(i).getResource();
      if (r instanceof DomainResource domain) {
        String path = "Bundle.entry[" + i + "].resource";
        collect(domain, path, refs, paths);
        for (int j = 0; j < domain.getContained().size(); j++) {
          if (domain.getContained().get(j) instanceof DomainResource contained) {
            collect(contained, path + ".contained[" + j + "]", refs, paths);
          }
        }
      }
    }
    sameDocumentPerEntry(refs, report);
    if (type == EprDocumentType.PADV) {
      changedBelongsToReference(bundle, refs, paths, report);
    }
    if (type == EprDocumentType.PML) {
      dispenseAndPrescriptionAgree(refs, paths, report);
    }
  }

  private static void collect(
      DomainResource resource, String path, List<Ref> refs, Map<DomainResource, String> paths) {
    paths.put(resource, path);
    List<Extension> extensions = resource.getExtension();
    for (int k = 0; k < extensions.size(); k++) {
      Extension ext = extensions.get(k);
      if (REFERENCES.contains(ext.getUrl())) {
        String id = value(ext, "id");
        if (id != null) {
          refs.add(
              new Ref(
                  ext.getUrl(),
                  id,
                  value(ext, "externalDocumentId"),
                  path + ".extension[" + k + "]",
                  resource));
        }
      }
    }
  }

  /** C1 (and C2): one entry id with only one document id. */
  private static void sameDocumentPerEntry(List<Ref> refs, ReportCollector report) {
    Map<String, Ref> first = new LinkedHashMap<>();
    for (Ref ref : refs) {
      if (ref.document() == null) {
        continue;
      }
      Ref seen = first.putIfAbsent(ref.id(), ref);
      if (seen != null && !seen.document().equals(ref.document())) {
        warn(
            report,
            ref.path(),
            "References name the same entry in two different documents: "
                + seen.path()
                + " and "
                + ref.path()
                + " (C1)");
      }
    }
  }

  /** C3: the changed resource of an advice belongs to what the observation references. */
  private static void changedBelongsToReference(
      Bundle bundle, List<Ref> refs, Map<DomainResource, String> paths, ReportCollector report) {
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (!(entry.getResource() instanceof Observation observation)) {
        continue;
      }
      List<Ref> targets =
          refs.stream()
              .filter(r -> r.owner() == observation)
              .filter(r -> r.url().equals(TREATMENT_PLAN) || r.url().equals(PRESCRIPTION))
              .toList();
      for (String url : List.of(STATEMENT_CHANGED, REQUEST_CHANGED)) {
        Extension changedExt = observation.getExtensionByUrl(url);
        if (targets.isEmpty() || changedExt == null) {
          continue;
        }
        DomainResource changed = resolve(bundle, observation, changedExt);
        if (changed == null) {
          continue;
        }
        List<String> own = new ArrayList<>(identifiers(changed));
        refs.stream().filter(r -> r.owner() == changed).map(Ref::id).forEach(own::add);
        boolean belongs = targets.stream().anyMatch(t -> own.contains(t.id()));
        if (!belongs) {
          warn(
              report,
              paths.get(observation) + ".extension",
              "The changed "
                  + changed.fhirType()
                  + " at "
                  + paths.get(changed)
                  + " belongs to none of the entries the advice references (C3)");
        }
      }
    }
  }

  /** C4: a dispense of a list and its prescription name the same treatment plan. */
  private static void dispenseAndPrescriptionAgree(
      List<Ref> refs, Map<DomainResource, String> paths, ReportCollector report) {
    for (Ref prescription : refs) {
      if (!(prescription.owner() instanceof MedicationDispense dispense)
          || !prescription.url().equals(PRESCRIPTION)) {
        continue;
      }
      String plan = treatmentPlanOf(refs, dispense);
      if (plan == null) {
        continue;
      }
      for (Map.Entry<DomainResource, String> e : paths.entrySet()) {
        if (e.getKey() instanceof MedicationRequest request
            && identifiers(request).contains(prescription.id())) {
          String requestPlan = treatmentPlanOf(refs, request);
          if (requestPlan != null && !requestPlan.equals(plan)) {
            warn(
                report,
                prescription.path(),
                "The dispense at "
                    + paths.get(dispense)
                    + " and its prescription at "
                    + e.getValue()
                    + " name different treatment plans (C4)");
          }
        }
      }
    }
  }

  private static String treatmentPlanOf(List<Ref> refs, DomainResource owner) {
    return refs.stream()
        .filter(r -> r.owner() == owner && r.url().equals(TREATMENT_PLAN))
        .map(Ref::id)
        .findFirst()
        .orElse(null);
  }

  /** The resource a changed-extension references: contained ({@code #id}) or a Bundle entry. */
  private static DomainResource resolve(Bundle bundle, DomainResource owner, Extension ext) {
    if (!(ext.getValue() instanceof Reference ref) || !ref.hasReference()) {
      return null;
    }
    String target = ref.getReference();
    if (target.startsWith("#")) {
      for (Resource c : owner.getContained()) {
        if (target.substring(1).equals(c.getIdPart()) && c instanceof DomainResource d) {
          return d;
        }
      }
      return null;
    }
    int index = RequiredData.index(bundle, ref);
    return index >= 0 && bundle.getEntry().get(index).getResource() instanceof DomainResource d
        ? d
        : null;
  }

  private static List<String> identifiers(DomainResource resource) {
    List<Identifier> ids =
        switch (resource) {
          case MedicationStatement s -> s.getIdentifier();
          case MedicationRequest r -> r.getIdentifier();
          case MedicationDispense d -> d.getIdentifier();
          case Observation o -> o.getIdentifier();
          default -> List.of();
        };
    return ids.stream().map(Identifier::getValue).filter(Objects::nonNull).toList();
  }

  private static String value(Extension ext, String part) {
    Extension sub = ext.getExtensionByUrl(part);
    return sub != null && sub.getValue() instanceof Identifier id ? id.getValue() : null;
  }

  private static void warn(ReportCollector report, String path, String message) {
    report.add(ReportEntry.warning(ReasonCode.REFERENCE_INCONSISTENT, Phase.MAP, path, message));
  }
}
