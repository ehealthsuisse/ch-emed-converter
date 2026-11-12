package ch.emed.converter.epr.mapping;

import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.EprReasonCode;
import ch.emed.converter.epr.MappingRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;

/**
 * CH EMED → CH EMED EPR (mapping rules M-EPR-2x). In this order: copy, profile change (M-EPR-01,
 * M-EPR-21), unsupported elements (M-EPR-22), substitution (M-EPR-27), required data and
 * treatment-plan references (M-EPR-23, M-EPR-24), original representation (M-EPR-25). The original
 * representation comes last so a rendered PDF shows the completed document. Thread-safe.
 */
public final class FromChEmedMapper {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final UnsupportedElements unsupported;
  private final FullUrls fullUrls = new FullUrls();
  private final Substitution substitution = new Substitution();
  private final RequiredData requiredData = new RequiredData();
  private final OriginalRepresentation originalRepresentation = new OriginalRepresentation();

  /**
   * @param unsupported the pinned list of unsupported elements
   */
  public FromChEmedMapper(UnsupportedElements unsupported) {
    this.unsupported = unsupported;
  }

  /**
   * Maps a validated CH EMED document.
   *
   * @param chEmed the CH EMED document; not changed
   * @param type its document type
   * @param ctx mapping context
   * @return the CH EMED EPR document, or null if required data is missing (ERROR entries)
   */
  @MappingRule({"M-EPR-01", "M-EPR-21"})
  public Bundle map(Bundle chEmed, EprDocumentType type, MappingContext ctx) {
    // feature 005, M-EPR-05: urn:uuid full URLs before anything refers to the entries
    Bundle epr = fullUrls.apply(chEmed.copy(), type, ctx);
    relabel(epr, "Bundle", type, ctx);
    for (int i = 0; i < epr.getEntry().size(); i++) {
      Resource resource = epr.getEntry().get(i).getResource();
      if (resource == null) {
        continue;
      }
      String path = "Bundle.entry[" + i + "].resource";
      relabel(resource, path, type, ctx);
      if (resource instanceof DomainResource domain) {
        for (int j = 0; j < domain.getContained().size(); j++) {
          relabel(domain.getContained().get(j), path + ".contained[" + j + "]", type, ctx);
        }
      }
    }

    epr = withoutUnsupported(epr, type, ctx);
    substitution.apply(epr, ctx);
    if (!requiredData.complete(epr, type, ctx)) {
      return null;
    }
    originalRepresentation.apply(epr, type, ctx);
    return epr;
  }

  private Bundle withoutUnsupported(Bundle epr, EprDocumentType type, MappingContext ctx) {
    try {
      ObjectNode json = (ObjectNode) JSON.readTree(ChEmedJson.serialize(epr));
      unsupported.remove(json, type, ctx.report());
      return ChEmedJson.parse(JSON.writeValueAsBytes(json));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void relabel(
      Resource resource, String path, EprDocumentType type, MappingContext ctx) {
    List<CanonicalType> profiles = resource.getMeta().getProfile();
    if (profiles.isEmpty()) {
      return;
    }
    List<String> mapped = new ArrayList<>();
    boolean changed = false;
    for (CanonicalType profile : profiles) {
      String url = profile.getValue();
      String target = url == null ? null : ProfileMap.toEpr(url, type).orElse(null);
      if (target == null) {
        mapped.add(url);
      } else {
        changed = true;
        if (!mapped.contains(target)) {
          mapped.add(target);
        }
      }
    }
    if (!changed) {
      return;
    }
    resource.getMeta().setProfile(new ArrayList<>());
    mapped.forEach(resource.getMeta()::addProfile);
    ctx.report()
        .add(
            ReportEntry.information(
                EprReasonCode.PROFILE_CHANGED,
                Phase.MAP,
                path + ".meta.profile",
                "CH EMED profile replaced by its CH EMED EPR profile (M-EPR-01)"));
  }
}
