package ch.emed.converter.epr.mapping;

import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.MappingRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Full URLs as CH EMED EPR requires them (mapping rule M-EPR-05, feature 005, decision 2026-11-10):
 * an entry whose {@code fullUrl} is not a {@code urn:uuid} gets one, and every reference to it is
 * changed accordingly. The new UUID is the version-5 UUID of the old full URL from the id strategy;
 * the changed MedicationStatement or MedicationRequest of a PADV gets the {@code urn:uuid} of its
 * identifier, as the EPR invariants {@code padv-mtp-same-id} and {@code
 * treatment-plan-id-reference-matches-statement-changed-reference} require. Each change is reported
 * as {@code DERIVED_VALUE}, without the URLs.
 */
@MappingRule("M-EPR-05")
public final class FullUrls {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern URN_UUID =
      Pattern.compile(
          "^urn:uuid:[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$");
  private static final Set<String> CHANGED =
      Set.of(ReferenceConsistency.STATEMENT_CHANGED, ReferenceConsistency.REQUEST_CHANGED);

  /**
   * @param document the document; not changed
   * @param type its document type
   * @param ctx mapping context with the id strategy and the report
   * @return the document with {@code urn:uuid} full URLs, or the same instance if nothing changes
   */
  public org.hl7.fhir.r4.model.Bundle apply(
      org.hl7.fhir.r4.model.Bundle document, EprDocumentType type, MappingContext ctx) {
    try {
      ObjectNode bundle = (ObjectNode) JSON.readTree(ChEmedJson.serialize(document));
      if (!rewrite(bundle, type, ctx)) {
        return document;
      }
      return ChEmedJson.parse(JSON.writeValueAsBytes(bundle));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean rewrite(ObjectNode bundle, EprDocumentType type, MappingContext ctx) {
    ArrayNode entries = (ArrayNode) bundle.path("entry");
    String documentId = bundle.path("identifier").path("value").asText("");
    Set<String> changed = type == EprDocumentType.PADV ? changedTargets(entries) : Set.of();
    Set<String> used = new HashSet<>();
    entries.forEach(e -> used.add(e.path("fullUrl").asText()));

    Map<String, String> replacements = new LinkedHashMap<>();
    for (int i = 0; i < entries.size(); i++) {
      ObjectNode entry = (ObjectNode) entries.get(i);
      String old = entry.path("fullUrl").asText(null);
      if (old == null || URN_UUID.matcher(old).matches()) {
        continue;
      }
      JsonNode resource = entry.path("resource");
      String relative = resource.path("resourceType").asText() + "/" + resource.path("id").asText();
      String url = null;
      if (changed.contains(old) || changed.contains(relative)) {
        String identifier = singleIdentifier(resource);
        if (identifier != null
            && URN_UUID.matcher(identifier).matches()
            && !used.contains(identifier)) {
          url = identifier;
        }
      }
      if (url == null) {
        url = "urn:uuid:" + ctx.options().idStrategy().id(documentId, "/fullUrl/" + old);
      }
      used.add(url);
      replacements.put(old, url);
      if (!resource.path("id").isMissingNode()) {
        replacements.putIfAbsent(relative, url);
      }
      entry.put("fullUrl", url);
      ctx.report()
          .add(
              ReportEntry.information(
                  ReasonCode.DERIVED_VALUE,
                  Phase.MAP,
                  "Bundle.entry[" + i + "].fullUrl",
                  "Full URL replaced by a urn:uuid, as CH EMED EPR requires (M-EPR-05)"));
    }
    if (replacements.isEmpty()) {
      return false;
    }
    references(bundle, replacements);
    return true;
  }

  /** Full URLs or relative references of the changed resources of the advice's observations. */
  private static Set<String> changedTargets(ArrayNode entries) {
    Set<String> targets = new HashSet<>();
    for (JsonNode entry : entries) {
      for (JsonNode ext : entry.path("resource").path("extension")) {
        if (CHANGED.contains(ext.path("url").asText())) {
          String ref = ext.path("valueReference").path("reference").asText(null);
          if (ref != null) {
            targets.add(ref);
          }
        }
      }
    }
    return targets;
  }

  private static String singleIdentifier(JsonNode resource) {
    JsonNode ids = resource.path("identifier");
    return ids.isArray() && ids.size() == 1 ? ids.get(0).path("value").asText(null) : null;
  }

  /** Replaces every {@code reference} that names a changed entry, anywhere in the document. */
  private static void references(JsonNode node, Map<String, String> replacements) {
    if (node instanceof ObjectNode object) {
      JsonNode reference = object.get("reference");
      if (reference != null && reference.isTextual()) {
        String replacement = replacements.get(reference.asText());
        if (replacement != null) {
          object.put("reference", replacement);
        }
      }
      object.elements().forEachRemaining(child -> references(child, replacements));
    } else if (node instanceof ArrayNode array) {
      array.forEach(child -> references(child, replacements));
    }
  }
}
