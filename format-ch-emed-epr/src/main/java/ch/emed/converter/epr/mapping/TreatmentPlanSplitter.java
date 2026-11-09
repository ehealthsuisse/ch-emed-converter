package ch.emed.converter.epr.mapping;

import ca.uhn.fhir.util.FhirTerser;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.MappingRule;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * A CH EMED Medication Card → one MTP document per treatment, delivered as a {@code collection}
 * Bundle (mapping rules M-EPR-31 to M-EPR-33, research R6, R7, data model §5). Each MTP is first
 * built as a CH EMED treatment plan from the card entry and then converted by {@link
 * FromChEmedMapper} with the MTP rules. All identifiers come from the id strategy (UUID v5), no
 * clock is read (research R10).
 */
public final class TreatmentPlanSplitter {

  static final String SNOMED = "http://snomed.info/sct";
  static final String LOINC = "http://loinc.org";
  static final String URI = "urn:ietf:rfc:3986";
  static final String CH_EMED_DOCUMENT =
      ProfileMap.CH_EMED + "ch-emed-document-medicationtreatmentplan";
  static final String CH_EMED_COMPOSITION =
      ProfileMap.CH_EMED + "ch-emed-composition-medicationtreatmentplan";
  static final String CH_EMED_STATEMENT = ProfileMap.CH_EMED + "ch-emed-medicationstatement";

  /** Composition and section titles of the CH EMED treatment plan, by language (M-DOC-08). */
  private static final Map<String, String[]> TITLES =
      Map.of(
          "de", new String[] {"Therapieentscheid Medikation", "Medikamentöser Behandlungsplan"},
          "fr",
              new String[] {
                "Décision thérapeutique relative à la médication",
                "Plan de traitement médicamenteux"
              },
          "it",
              new String[] {
                "Decisione terapeutica di trattamento farmacologico",
                "Piano terapeutico farmacologico"
              },
          "en", new String[] {"Medication Treatment Plan", "Medication Treatment Plan"});

  private final FromChEmedMapper mapper;

  /**
   * @param mapper the CH EMED → EPR mapper used for each MTP
   */
  public TreatmentPlanSplitter(FromChEmedMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Splits a validated CH EMED Medication Card.
   *
   * @param card the card; not changed
   * @param ctx mapping context; its report receives the entries of all MTP documents, with the
   *     index of the MTP in each path
   * @param validate validates one MTP document into the given context, true without errors; null to
   *     skip validation
   * @return the collection, or null on errors
   */
  @MappingRule({"M-EPR-31", "M-EPR-32", "M-EPR-33"})
  public Bundle split(
      Bundle card, MappingContext ctx, BiPredicate<Bundle, MappingContext> validate) {
    Composition composition = (Composition) card.getEntryFirstRep().getResource();
    List<Integer> statements = new ArrayList<>();
    for (Composition.SectionComponent section : composition.getSection()) {
      for (Reference ref : section.getEntry()) {
        int index = RequiredData.index(card, ref);
        if (index >= 0 && card.getEntry().get(index).getResource() instanceof MedicationStatement) {
          statements.add(index);
        }
      }
    }
    if (statements.isEmpty()) {
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.NO_TREATMENTS,
                  Phase.MAP,
                  "Bundle.entry[0].resource.section",
                  "The medication card has no treatments, so no treatment plan can be created"));
      return null;
    }

    ConversionOptions options = ctx.options();
    if (options.originalRepresentation().isPresent()) {
      ctx.report()
          .add(
              ReportEntry.warning(
                  ReasonCode.UNMAPPABLE_ELEMENT,
                  Phase.MAP,
                  "options.originalRepresentation",
                  "Original representation not used for treatment-plans"));
      options = withoutOriginalRepresentation(options);
    }

    String cardId = composition.getIdentifier().getValue();
    Bundle collection = new Bundle();
    collection.setType(Bundle.BundleType.COLLECTION);
    collection
        .getIdentifier()
        .setSystem(URI)
        .setValue("urn:uuid:" + options.idStrategy().id(cardId, "treatment-plans"));
    collection.setTimestampElement(
        new InstantType(composition.getDateElement().getValueAsString()));

    boolean ok = true;
    for (int i = 0; i < statements.size(); i++) {
      int index = statements.get(i);
      MedicationStatement statement =
          (MedicationStatement) card.getEntry().get(index).getResource();
      if (statement.getExtensionByUrl(RequiredData.EXT_TREATMENT_PLAN) != null) {
        ctx.report()
            .add(
                ReportEntry.warning(
                    ReasonCode.TREATMENT_PLAN_EXISTS,
                    Phase.MAP,
                    "Bundle.entry[" + index + "].resource.extension",
                    "This treatment already references a treatment plan in the EPR; its MTP"
                        + " records the treatment decision a second time"));
      }
      ReportCollector report = new ReportCollector();
      boolean[] authorFromSource = {false};
      Bundle chEmedMtp = treatmentPlan(card, composition, index, i, options, authorFromSource);
      if (authorFromSource[0]) {
        report.add(
            ReportEntry.information(
                ReasonCode.DERIVED_VALUE,
                Phase.MAP,
                "Bundle.entry[0].resource.author",
                "Author of the treatment plan taken from the information source of the card"
                    + " entry"));
      }
      MappingContext mtpCtx = new MappingContext(options, report, ctx.validator().orElse(null));
      Bundle mtp = mapper.map(chEmedMtp, EprDocumentType.MTP, mtpCtx);
      if (mtp != null && !report.hasErrors() && validate != null) {
        validate.test(mtp, mtpCtx);
      }
      String prefix = "Bundle.entry[" + i + "].resource";
      for (ReportEntry e : report.entries()) {
        ctx.report().add(prefixed(e, prefix));
      }
      if (mtp == null || report.hasErrors()) {
        ok = false;
        continue;
      }
      collection.addEntry().setFullUrl(mtp.getIdentifier().getValue()).setResource(mtp);
    }
    return ok ? collection : null;
  }

  /** The CH EMED treatment plan for card entry {@code index}, the {@code i}-th treatment. */
  private static Bundle treatmentPlan(
      Bundle card,
      Composition cardComposition,
      int index,
      int i,
      ConversionOptions options,
      boolean[] authorFromSource) {
    String cardId = cardComposition.getIdentifier().getValue();
    String base = "/treatment-plans/" + i;

    MedicationStatement statement =
        ((MedicationStatement) card.getEntry().get(index).getResource()).copy();
    statement.getMeta().setProfile(new ArrayList<>());
    statement.getMeta().addProfile(CH_EMED_STATEMENT);
    // the card's references to the EPR do not belong to the treatment plan itself
    statement
        .getExtension()
        .removeIf(
            e ->
                e.getUrl().equals(RequiredData.EXT_TREATMENT_PLAN)
                    || e.getUrl().equals(RequiredData.EXT_LAST_CONSIDERED));

    Composition composition = new Composition();
    composition.setId(options.idStrategy().id(cardId, base + "/Composition"));
    composition.getMeta().addProfile(CH_EMED_COMPOSITION);
    if (cardComposition.hasLanguage()) {
      composition.setLanguage(cardComposition.getLanguage());
    }
    composition
        .getIdentifier()
        .setSystem(URI)
        .setValue("urn:uuid:" + options.idStrategy().id(cardId, base + "/Composition"));
    composition.setStatus(Composition.CompositionStatus.FINAL);
    composition.setType(
        new CodeableConcept()
            .addCoding(
                new Coding(
                    SNOMED, "761931002", "Medication treatment plan report (record artifact)"))
            .addCoding(
                new Coding(LOINC, "77603-9", "Medication treatment plan.extended Document")));
    composition.setSubject(cardComposition.getSubject().copy());
    composition.setDateElement(cardComposition.getDateElement().copy());
    cardComposition.getAuthor().forEach(a -> composition.addAuthor(a.copy()));
    List<Bundle.BundleEntryComponent> moved = new ArrayList<>();
    if (!hasPersonAuthor(card, composition) && statement.hasInformationSource()) {
      // a card from the EPR is authored by the aggregation service (F8); the MTP needs the person
      // who recorded the treatment, its information source. A contained information source (as
      // in the EPR cards) moves into the document, since an author cannot reference it.
      moved = uncontainInformationSource(statement, cardId, base, options);
      composition.getAuthor().clear();
      composition.addAuthor(statement.getInformationSource().copy());
      authorFromSource[0] = true;
    }
    String[] titles = TITLES.getOrDefault(language(cardComposition), TITLES.get("de"));
    composition.setTitle(titles[0]);
    composition.setConfidentialityElement(cardComposition.getConfidentialityElement().copy());
    Composition.SectionComponent section = composition.addSection();
    section.setTitle(titles[1]);
    section.setCode(
        new CodeableConcept(
            new Coding(LOINC, "77604-7", "Medication treatment plan.brief Document")));
    String statementUrl = card.getEntry().get(index).getFullUrl();
    section.addEntry(new Reference(statementUrl));
    section.setText(Narratives.treatment(statement));

    Bundle mtp = new Bundle();
    mtp.getMeta().addProfile(CH_EMED_DOCUMENT);
    // CH EMED EPR: the Bundle and its Composition have the same identifier
    mtp.setIdentifier(composition.getIdentifier().copy());
    mtp.setType(Bundle.BundleType.DOCUMENT);
    mtp.setTimestampElement(new InstantType(cardComposition.getDateElement().getValueAsString()));
    mtp.addEntry().setFullUrl("urn:uuid:" + composition.getIdPart()).setResource(composition);
    mtp.addEntry().setFullUrl(statementUrl).setResource(statement);
    moved.forEach(mtp.getEntry()::add);

    // the resources the composition and the statement reach, in card order
    Set<Integer> reached = new LinkedHashSet<>();
    List<Resource> queue = new ArrayList<>(List.of(composition, statement));
    moved.forEach(e -> queue.add(e.getResource()));
    FhirTerser terser = ChEmedJson.context().newTerser();
    while (!queue.isEmpty()) {
      Resource r = queue.remove(0);
      for (Reference ref : terser.getAllPopulatedChildElementsOfType(r, Reference.class)) {
        int target = RequiredData.index(card, ref);
        if (target > 0 && target != index && reached.add(target)) {
          queue.add(card.getEntry().get(target).getResource());
        }
      }
    }
    reached.stream()
        .sorted()
        .forEach(
            t ->
                mtp.addEntry()
                    .setFullUrl(card.getEntry().get(t).getFullUrl())
                    .setResource(card.getEntry().get(t).getResource().copy()));

    // references point to the full URL of their target: the Composition is new and has a
    // urn:uuid full URL, against which relative references of the card would not resolve
    for (Bundle.BundleEntryComponent entry : mtp.getEntry()) {
      for (Reference ref :
          terser.getAllPopulatedChildElementsOfType(entry.getResource(), Reference.class)) {
        if (ref.hasReference() && !ref.getReference().startsWith("#")) {
          int target = RequiredData.index(card, ref);
          if (target >= 0 && card.getEntry().get(target).hasFullUrl()) {
            ref.setReference(card.getEntry().get(target).getFullUrl());
          }
        }
      }
    }
    return mtp;
  }

  /**
   * Moves the contained resources reached from {@code informationSource} (a PractitionerRole and
   * what it contains) out of the statement, as entries with deterministic {@code urn:uuid} full
   * URLs, and points the local references to them.
   */
  private static List<Bundle.BundleEntryComponent> uncontainInformationSource(
      MedicationStatement statement, String cardId, String base, ConversionOptions options) {
    List<Bundle.BundleEntryComponent> entries = new ArrayList<>();
    if (!statement.getInformationSource().getReference().startsWith("#")) {
      return entries;
    }
    FhirTerser terser = ChEmedJson.context().newTerser();
    Map<String, String> urls = new java.util.LinkedHashMap<>();
    List<String> queue = new ArrayList<>();
    queue.add(statement.getInformationSource().getReference().substring(1));
    while (!queue.isEmpty()) {
      String local = queue.remove(0);
      if (urls.containsKey(local)) {
        continue;
      }
      Resource contained = contained(statement, local);
      if (contained == null) {
        continue;
      }
      urls.put(local, "urn:uuid:" + options.idStrategy().id(cardId, base + "/contained/" + local));
      for (Reference ref : terser.getAllPopulatedChildElementsOfType(contained, Reference.class)) {
        if (ref.hasReference() && ref.getReference().startsWith("#")) {
          queue.add(ref.getReference().substring(1));
        }
      }
    }
    for (Map.Entry<String, String> e : urls.entrySet()) {
      Resource contained = contained(statement, e.getKey());
      statement.getContained().remove(contained);
      Resource copy = contained.copy();
      copy.setId(e.getValue().substring("urn:uuid:".length()));
      entries.add(new Bundle.BundleEntryComponent().setFullUrl(e.getValue()).setResource(copy));
    }
    List<Resource> owners = new ArrayList<>();
    owners.add(statement);
    entries.forEach(e -> owners.add(e.getResource()));
    for (Resource owner : owners) {
      for (Reference ref : terser.getAllPopulatedChildElementsOfType(owner, Reference.class)) {
        if (ref.hasReference() && ref.getReference().startsWith("#")) {
          String url = urls.get(ref.getReference().substring(1));
          if (url != null) {
            ref.setReference(url);
          }
        }
      }
    }
    return entries;
  }

  private static Resource contained(MedicationStatement statement, String local) {
    for (Resource r : statement.getContained()) {
      if (local.equals(r.getIdPart()) || ("#" + local).equals(r.getId())) {
        return r;
      }
    }
    return null;
  }

  private static boolean hasPersonAuthor(Bundle card, Composition composition) {
    for (Reference author : composition.getAuthor()) {
      int index = RequiredData.index(card, author);
      Resource r = index < 0 ? null : card.getEntry().get(index).getResource();
      if (r instanceof org.hl7.fhir.r4.model.PractitionerRole
          || r instanceof org.hl7.fhir.r4.model.Patient
          || r instanceof org.hl7.fhir.r4.model.RelatedPerson
          || r instanceof org.hl7.fhir.r4.model.Practitioner) {
        return true;
      }
    }
    return false;
  }

  private static String language(Composition composition) {
    return composition.hasLanguage()
        ? composition.getLanguage().substring(0, 2).toLowerCase(Locale.ROOT)
        : "de";
  }

  private static ConversionOptions withoutOriginalRepresentation(ConversionOptions o) {
    return new ConversionOptions(
        o.strict(),
        o.validateOutput(),
        o.idStrategy(),
        o.clock(),
        o.maxDecodedSize(),
        o.defaultLanguage(),
        o.confidentiality(),
        o.patientIdentifier(),
        o.author(),
        o.outputForm(),
        o.treatmentPlanReferences(),
        Optional.empty());
  }

  private static ReportEntry prefixed(ReportEntry e, String prefix) {
    String path = e.path();
    String mapped =
        path == null
            ? prefix
            : path.startsWith("Bundle") ? prefix + path.substring("Bundle".length()) : path;
    return new ReportEntry(
        e.severity(), e.code(), e.phase(), mapped, e.targetPath(), e.message(), e.clinicalLoss());
  }
}
