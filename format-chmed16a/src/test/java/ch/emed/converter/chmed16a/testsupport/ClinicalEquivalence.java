package ch.emed.converter.chmed16a.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares two CHMED16A documents or two CH EMED documents for clinical equivalence: generated ids,
 * narratives, PDFs and values equal to a documented default are ignored; the differences are
 * returned as paths (never values), so they can be matched against {@code capability-gaps.json}.
 */
public final class ClinicalEquivalence {

  /** Kind of a difference between the first and the second document. */
  public enum Kind {
    REMOVED,
    ADDED,
    CHANGED
  }

  /**
   * A difference.
   *
   * @param path JSON Pointer (CHMED16A) or resource path (CH EMED)
   * @param kind kind
   */
  public record Difference(String path, Kind kind) {
    @Override
    public String toString() {
      return kind + " " + path;
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> CHEMED_NOISE =
      Set.of("id", "text", "meta", "fullUrl", "extension", "_confidentiality", "language");

  private ClinicalEquivalence() {}

  // ---------------------------------------------------------------------------------------------
  // CHMED16A

  /** Differences between two CHMED16A documents (JSON bytes). */
  public static List<Difference> chmed16a(byte[] first, byte[] second) {
    List<Difference> differences = new ArrayList<>();
    diff("", normalizeChmed16a(read(first)), normalizeChmed16a(read(second)), differences);
    return differences;
  }

  /** Removes values equal to a documented default and private fields. */
  private static JsonNode normalizeChmed16a(JsonNode root) {
    ObjectNode copy = (ObjectNode) withoutEmptyStrings(root);
    copy.remove("PFields");
    copy.remove("PSchema");
    copy.remove("Recoms");
    JsonNode patient = copy.get("Patient");
    if (patient instanceof ObjectNode p) {
      p.remove("PFields");
      if (p.path("Lng").isTextual()) {
        p.put("Lng", p.path("Lng").asText().toLowerCase(java.util.Locale.ROOT));
      }
    }
    JsonNode medicaments = copy.get("Medicaments");
    if (medicaments instanceof ArrayNode list) {
      for (JsonNode m : list) {
        if (m instanceof ObjectNode medicament) {
          medicament.remove("PFields");
          dropDefault(medicament, "AutoMed", 0);
          dropDefault(medicament, "Rep", 0);
          for (JsonNode pos : medicament.path("Pos")) {
            if (pos instanceof ObjectNode posology) {
              posology.remove("PFields");
              dropDefault(posology, "CyDu", 86_400);
              dropDefault(posology, "InRes", 0);
            }
          }
        }
      }
    }
    return copy;
  }

  /** Empty strings are treated as absent (mapping specification, conventions). */
  private static JsonNode withoutEmptyStrings(JsonNode node) {
    if (node.isObject()) {
      ObjectNode result = MAPPER.createObjectNode();
      node.fields()
          .forEachRemaining(
              e -> {
                if (!(e.getValue().isTextual() && e.getValue().asText().isEmpty())) {
                  result.set(e.getKey(), withoutEmptyStrings(e.getValue()));
                }
              });
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = MAPPER.createArrayNode();
      node.forEach(n -> result.add(withoutEmptyStrings(n)));
      return result;
    }
    return node;
  }

  private static void dropDefault(ObjectNode node, String field, int value) {
    JsonNode v = node.get(field);
    if (v != null && v.isNumber() && v.decimalValue().compareTo(BigDecimal.valueOf(value)) == 0) {
      node.remove(field);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // CH EMED

  /** Differences between two CH EMED document Bundles (FHIR JSON bytes). */
  public static List<Difference> chEmed(byte[] first, byte[] second) {
    List<Difference> differences = new ArrayList<>();
    diff("", projectChEmed(read(first)), projectChEmed(read(second)), differences);
    return differences;
  }

  /**
   * Clinical projection of a document: the Bundle identifier and the content of the Composition,
   * Patient, author resources and statements/requests without generated ids, references,
   * narratives, extensions and the original-representation PDF.
   */
  private static JsonNode projectChEmed(JsonNode bundle) {
    ObjectNode projection = MAPPER.createObjectNode();
    projection.set("Bundle.identifier", bundle.path("identifier").deepCopy());
    projection.set("Bundle.timestamp", bundle.path("timestamp").deepCopy());
    for (JsonNode entry : bundle.path("entry")) {
      JsonNode resource = entry.path("resource");
      String type = resource.path("resourceType").asText();
      if (type.equals("Binary")) {
        continue;
      }
      ArrayNode list =
          projection.has(type) ? (ArrayNode) projection.get(type) : projection.putArray(type);
      ObjectNode cleaned = (ObjectNode) clean(resource);
      canonicalDosage(cleaned, "dosage");
      canonicalDosage(cleaned, "dosageInstruction");
      list.add(cleaned);
    }
    if (projection.has("Composition")) {
      for (JsonNode composition : projection.get("Composition")) {
        if (composition instanceof ObjectNode c) {
          c.remove("section");
          c.remove("author");
          c.remove("subject");
          c.remove("identifier");
          c.remove("title");
        }
      }
    }
    return projection;
  }

  /** {@code text} of a CodeableConcept is content, {@code text} of a resource is a narrative. */
  private static boolean isCodeableText(String name, JsonNode value) {
    return name.equals("text") && value.isTextual();
  }

  /**
   * Replaces the dosage list by its meaning, so that a merged entry (two day periods in one dosage)
   * equals split entries: instruction, route, period, as-needed and the dose per day period;
   * dosages that are not of this simple form are kept as they are.
   */
  private static void canonicalDosage(ObjectNode resource, String element) {
    JsonNode dosages = resource.get(element);
    if (dosages == null || !dosages.isArray()) {
      return;
    }
    ObjectNode meaning = MAPPER.createObjectNode();
    ObjectNode slots = meaning.putObject("dosePerDayPeriod");
    ArrayNode other = meaning.putArray("other");
    for (JsonNode d : dosages) {
      JsonNode repeat = d.path("timing").path("repeat");
      JsonNode doseAndRate = d.path("doseAndRate");
      boolean simple =
          repeat.path("when").isArray()
              && repeat.path("when").size() > 0
              && doseAndRate.size() == 1
              && doseAndRate.get(0).has("doseQuantity")
              && !repeat.has("dayOfWeek")
              && !repeat.has("timeOfDay");
      for (String field : List.of("patientInstruction", "route", "asNeededBoolean")) {
        if (d.has(field)) {
          meaning.set(field, d.get(field));
        }
      }
      if (repeat.has("boundsPeriod")) {
        meaning.set("boundsPeriod", repeat.get("boundsPeriod"));
      }
      if (simple) {
        JsonNode dose = alias(doseAndRate.get(0).get("doseQuantity"));
        repeat.path("when").forEach(w -> slots.set(w.asText(), dose));
      } else if (!repeat.isMissingNode() || d.has("text") || d.has("doseAndRate")) {
        ObjectNode rest = ((ObjectNode) d).deepCopy();
        rest.remove("patientInstruction");
        rest.remove("route");
        rest.remove("sequence");
        other.add(rest);
      }
    }
    resource.set(element, meaning);
  }

  /** Dose units that CHMED16A cannot tell apart (STK): SNOMED "Tablet" and "Unit". */
  private static JsonNode alias(JsonNode quantity) {
    ObjectNode copy = quantity.deepCopy();
    String code = copy.path("code").asText();
    if (code.equals("732936001") || code.equals("767525000")) {
      copy.put("code", "STK");
      copy.put("unit", "STK");
      copy.remove("system");
    }
    return copy;
  }

  private static JsonNode clean(JsonNode node) {
    if (node.isObject()) {
      ObjectNode result = MAPPER.createObjectNode();
      Iterator<String> names = node.fieldNames();
      while (names.hasNext()) {
        String name = names.next();
        if ((CHEMED_NOISE.contains(name) && !isCodeableText(name, node.get(name)))
            || name.equals("reference")
            || name.equals("resourceType")) {
          continue;
        }
        if (name.equals("identifier")
            && node.path("resourceType").asText().startsWith("Medication")) {
          continue;
        }
        result.set(name, clean(node.get(name)));
      }
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = MAPPER.createArrayNode();
      node.forEach(n -> result.add(clean(n)));
      return result;
    }
    return node;
  }

  // ---------------------------------------------------------------------------------------------

  private static void diff(String path, JsonNode a, JsonNode b, List<Difference> out) {
    if (a == null && b == null) {
      return;
    }
    if (a == null || a.isMissingNode()) {
      out.add(new Difference(path, Kind.ADDED));
      return;
    }
    if (b == null || b.isMissingNode()) {
      out.add(new Difference(path, Kind.REMOVED));
      return;
    }
    if (a.isObject() && b.isObject()) {
      Set<String> names = new TreeSet<>();
      a.fieldNames().forEachRemaining(names::add);
      b.fieldNames().forEachRemaining(names::add);
      for (String name : names) {
        diff(path + "/" + name, a.get(name), b.get(name), out);
      }
    } else if (a.isArray() && b.isArray()) {
      int max = Math.max(a.size(), b.size());
      for (int i = 0; i < max; i++) {
        diff(path + "/" + i, i < a.size() ? a.get(i) : null, i < b.size() ? b.get(i) : null, out);
      }
    } else if (a.isNumber() && b.isNumber()) {
      if (a.decimalValue().compareTo(b.decimalValue()) != 0) {
        out.add(new Difference(path, Kind.CHANGED));
      }
    } else if (!a.equals(b)) {
      out.add(new Difference(path, Kind.CHANGED));
    }
  }

  private static JsonNode read(byte[] json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
