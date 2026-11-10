package ch.emed.converter.epr.mapping;

import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.EprReasonCode;
import ch.emed.converter.epr.MappingRule;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;

/**
 * CH EMED EPR → CH EMED (mapping rules M-EPR-1x). A valid CH EMED EPR document is a CH EMED
 * document with stricter constraints, so only the declared profiles change (M-EPR-01).
 */
public final class ToChEmedMapper {

  /**
   * Maps a validated CH EMED EPR document.
   *
   * @param epr the EPR document; not changed
   * @param type its document type
   * @param ctx mapping context
   * @return the CH EMED document
   */
  @MappingRule({"M-EPR-01", "M-EPR-12"})
  public Bundle map(Bundle epr, EprDocumentType type, MappingContext ctx) {
    Bundle out = epr.copy();
    relabel(out, "Bundle", ctx);
    for (int i = 0; i < out.getEntry().size(); i++) {
      Resource resource = out.getEntry().get(i).getResource();
      if (resource != null) {
        String path = "Bundle.entry[" + i + "].resource";
        relabel(resource, path, ctx);
        if (resource instanceof DomainResource domain) {
          for (int j = 0; j < domain.getContained().size(); j++) {
            relabel(domain.getContained().get(j), path + ".contained[" + j + "]", ctx);
          }
        }
      }
    }
    return out;
  }

  private static void relabel(Resource resource, String path, MappingContext ctx) {
    List<CanonicalType> profiles = resource.getMeta().getProfile();
    if (profiles.isEmpty()) {
      return;
    }
    List<String> mapped = new ArrayList<>();
    boolean changed = false;
    for (CanonicalType profile : profiles) {
      String url = profile.getValue();
      String target = url == null ? null : ProfileMap.toChEmed(url).orElse(null);
      if (target == null) {
        mapped.add(url);
      } else {
        changed = true;
        if (!target.isEmpty() && !mapped.contains(target)) {
          mapped.add(target);
        }
      }
    }
    if (!changed) {
      return;
    }
    resource.getMeta().setProfile(new ArrayList<>());
    mapped.forEach(resource.getMeta()::addProfile);
    if (resource.getMeta().isEmpty()) {
      resource.setMeta(null);
    }
    ctx.report()
        .add(
            ReportEntry.information(
                EprReasonCode.PROFILE_CHANGED,
                Phase.MAP,
                path + ".meta.profile",
                "CH EMED EPR profile replaced by its CH EMED base profile (M-EPR-01)"));
  }
}
