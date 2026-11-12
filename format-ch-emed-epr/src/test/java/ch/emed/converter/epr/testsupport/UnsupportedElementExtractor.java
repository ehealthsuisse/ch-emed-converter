package ch.emed.converter.epr.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * T030 (feature 004, research R5, F7): extracts the elements that CH EMED EPR does not support from
 * the vendored package {@code ch.fhir.ig.ch-emed-epr#3.0.0} into the format of data model §4. The
 * result is pinned as {@code epr/unsupported-elements.json}; {@code UnsupportedElementsTest}
 * compares both.
 *
 * <ul>
 *   <li>An element is not supported when its snapshot carries the standards-status {@code draft}
 *       extension and its {@code short} starts with "✕" (F7: {@code Dosage.text} of the base dosage
 *       is {@code draft} but required in practice, and has no "✕").
 *   <li>Feature 005, research F4: an element whose value a {@code pattern[x]} or {@code fixed[x]}
 *       of an ancestor in the same profile requires is supported despite the marker; the DIS and
 *       PADV compositions require {@code Composition.type.coding.version} by their type pattern but
 *       mark it "✕".
 *   <li>Scope {@code pmlc}: "✕ This element will be removed in PML(C) documents"; else {@code all}.
 *   <li>The EPR datatype profiles (dosage, quantities, ratios) are expanded into the resource
 *       profiles at the elements that use them, so each resource profile lists every path below it.
 *   <li>Every slice on a listed path gets a matcher taken from the slicing discriminator: a {@code
 *       property} for a choice type, an extension {@code url}, a {@code system} and {@code code}, a
 *       {@code resourceType}, or a {@code position} ({@code first} or {@code rest}) for the dosage
 *       base and additional entries.
 *   <li>{@code clinical} comes from {@link #CLINICAL}, set by hand; the reasons are in {@code
 *       docs/mapping/ch-emed-epr.md}.
 * </ul>
 */
public final class UnsupportedElementExtractor {

  /** Package resource on the test classpath (from {@code converter-validation}). */
  public static final String PACKAGE = "/packages/ch.fhir.ig.ch-emed-epr-3.0.0.tgz";

  public static final String PACKAGE_ID = "ch.fhir.ig.ch-emed-epr#3.0.0";

  private static final String EPR = "http://fhir.ch/ig/ch-emed-epr/StructureDefinition/";
  private static final String STATUS =
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status";

  /**
   * Clinically relevant unsupported elements, by element id of the defining profile without slice
   * names. Removing one of them loses information about the medication, its dosage, the reason or
   * the status of the treatment, so strict mode fails (FR-008). Reviewed in T036.
   */
  public static final Map<String, String> CLINICAL =
      Map.ofEntries(
          Map.entry("Dosage.text", "split dosage text: part of the dosage instructions"),
          Map.entry("Dosage.timing.event", "dosage timing"),
          Map.entry("Dosage.timing.code", "dosage timing"),
          Map.entry("Dosage.timing.repeat.count", "dosage timing"),
          Map.entry("Dosage.timing.repeat.countMax", "dosage timing"),
          Map.entry("Dosage.timing.repeat.duration", "dosage timing"),
          Map.entry("Dosage.timing.repeat.durationMax", "dosage timing"),
          Map.entry("Dosage.timing.repeat.durationUnit", "dosage timing"),
          Map.entry("Dosage.timing.repeat.frequency", "dosage timing"),
          Map.entry("Dosage.timing.repeat.frequencyMax", "dosage timing"),
          Map.entry("Dosage.timing.repeat.period", "dosage timing"),
          Map.entry("Dosage.timing.repeat.periodMax", "dosage timing"),
          Map.entry("Dosage.timing.repeat.periodUnit", "dosage timing"),
          Map.entry("Dosage.timing.repeat.dayOfWeek", "dosage timing"),
          Map.entry("Dosage.timing.repeat.timeOfDay", "dosage timing"),
          Map.entry("Dosage.timing.repeat.offset", "dosage timing"),
          Map.entry("Dosage.site", "site of administration"),
          Map.entry("Dosage.method", "method of administration"),
          Map.entry("Dosage.maxDosePerLifetime", "maximum dose"),
          Map.entry("MedicationStatement.statusReason", "reason for the treatment status"),
          Map.entry("MedicationStatement.reasonReference", "reason for the treatment"),
          Map.entry("MedicationStatement.effective[x]", "period of the treatment"),
          Map.entry("MedicationRequest.statusReason", "reason for the prescription status"),
          Map.entry("MedicationRequest.reasonReference", "reason for the prescription"),
          Map.entry("MedicationRequest.courseOfTherapyType", "acute or long-term treatment"),
          Map.entry("MedicationRequest.detectedIssue", "clinical issue with the prescription"),
          Map.entry("Observation.value[x]", "observed value"),
          Map.entry("Observation.component", "observed values"),
          Map.entry("Observation.interpretation", "interpretation of the value"),
          Map.entry("Observation.referenceRange", "reference range of the value"),
          Map.entry("Observation.effective[x]", "time of the observation"));

  private final Map<String, JsonNode> definitions = new TreeMap<>();

  private UnsupportedElementExtractor(Map<String, JsonNode> definitions) {
    this.definitions.putAll(definitions);
  }

  /**
   * @return the extractor for the vendored package
   */
  public static UnsupportedElementExtractor fromVendoredPackage() {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, JsonNode> definitions = new TreeMap<>();
    try (InputStream in = UnsupportedElementExtractor.class.getResourceAsStream(PACKAGE)) {
      if (in == null) {
        throw new IllegalStateException("Package not on the classpath: " + PACKAGE);
      }
      untar(
          new GZIPInputStream(in),
          (name, bytes) -> {
            if (name.startsWith("package/StructureDefinition-") && name.endsWith(".json")) {
              try {
                JsonNode sd = mapper.readTree(bytes);
                definitions.put(sd.path("url").asText(), sd);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new UnsupportedElementExtractor(definitions);
  }

  /**
   * @return the list in the format of data model §4
   */
  public ObjectNode extract() {
    JsonNodeFactory f = JsonNodeFactory.instance;
    ObjectNode root = f.objectNode();
    root.put("package", PACKAGE_ID);
    ArrayNode profiles = root.putArray("profiles");
    for (JsonNode sd : definitions.values()) {
      if (!"resource".equals(sd.path("kind").asText())
          || !sd.path("url").asText().startsWith(EPR)) {
        continue;
      }
      Map<String, ObjectNode> elements = new LinkedHashMap<>();
      Map<String, ObjectNode> slices = new TreeMap<>();
      collect(sd, sd.path("type").asText(), elements, slices, new ArrayList<>());
      ObjectNode profile = profiles.addObject();
      profile.put("profile", sd.path("url").asText());
      ArrayNode list = profile.putArray("elements");
      elements.values().forEach(list::add);
      ArrayNode sliceList = profile.putArray("slices");
      slices.forEach(
          (path, match) -> {
            if (elements.keySet().stream()
                .anyMatch(p -> p.startsWith(path + ".") || p.equals(path))) {
              ObjectNode slice = sliceList.addObject();
              slice.put("path", path);
              slice.set("match", match);
            }
          });
    }
    return root;
  }

  private void collect(
      JsonNode sd,
      String root,
      Map<String, ObjectNode> elements,
      Map<String, ObjectNode> slices,
      List<String> stack) {
    String type = sd.path("type").asText();
    Map<String, JsonNode> byId = new LinkedHashMap<>();
    sd.path("snapshot").path("element").forEach(e -> byId.put(e.path("id").asText(), e));
    for (JsonNode e : byId.values()) {
      String id = e.path("id").asText();
      if (id.equals(type)) {
        continue;
      }
      String path = root + id.substring(type.length());
      if (elements.keySet().stream().anyMatch(p -> path.startsWith(p + "."))) {
        continue; // below a removed element
      }
      if (e.has("sliceName")) {
        slices.putIfAbsent(path, matcher(e, byId));
      }
      if (unsupported(e) && !requiredByPattern(id, byId)) {
        if (!elements.containsKey(path)) {
          ObjectNode element = JsonNodeFactory.instance.objectNode();
          element.put("path", path);
          element.put("scope", e.path("short").asText().contains("PML(C)") ? "pmlc" : "all");
          element.put("clinical", CLINICAL.containsKey(withoutSlices(id)));
          elements.put(path, element);
        }
        continue;
      }
      for (JsonNode t : e.path("type")) {
        for (JsonNode p : t.path("profile")) {
          JsonNode target = definitions.get(p.asText());
          if (target != null
              && "complex-type".equals(target.path("kind").asText())
              && !stack.contains(p.asText())) {
            stack.add(p.asText());
            collect(target, path, elements, slices, stack);
            stack.remove(stack.size() - 1);
          }
        }
      }
    }
  }

  /**
   * @return true if a {@code pattern[x]} or {@code fixed[x]} of an ancestor element sets a value at
   *     the element (feature 005, research F4)
   */
  static boolean requiredByPattern(String id, Map<String, JsonNode> byId) {
    String[] parts = id.split("\\.");
    for (int i = parts.length - 1; i > 0; i--) {
      JsonNode ancestor = byId.get(String.join(".", java.util.Arrays.copyOf(parts, i)));
      if (ancestor == null) {
        continue;
      }
      List<String> rest = new ArrayList<>();
      for (int k = i; k < parts.length; k++) {
        rest.add(parts[k].replaceAll(":.*$", ""));
      }
      for (java.util.Iterator<Map.Entry<String, JsonNode>> it = ancestor.fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> field = it.next();
        if ((field.getKey().startsWith("pattern") || field.getKey().startsWith("fixed"))
            && present(field.getValue(), rest)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean present(JsonNode node, List<String> path) {
    if (path.isEmpty()) {
      return !node.isMissingNode() && !node.isNull();
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        if (present(item, path)) {
          return true;
        }
      }
      return false;
    }
    return node.has(path.get(0)) && present(node.get(path.get(0)), path.subList(1, path.size()));
  }

  /**
   * @return true for an element CH EMED EPR does not support (R5 refined by F7)
   */
  static boolean unsupported(JsonNode element) {
    boolean draft = false;
    for (JsonNode x : element.path("extension")) {
      if (STATUS.equals(x.path("url").asText()) && "draft".equals(x.path("valueCode").asText())) {
        draft = true;
      }
    }
    return draft && element.path("short").asText().startsWith("✕");
  }

  /**
   * @return the element id without slice names, for example {@code Composition.section.focus}
   */
  public static String withoutSlices(String id) {
    return id.replaceAll(":[^.]+", "");
  }

  private static ObjectNode matcher(JsonNode slice, Map<String, JsonNode> byId) {
    JsonNodeFactory f = JsonNodeFactory.instance;
    String id = slice.path("id").asText();
    String name = slice.path("sliceName").asText();
    String base = id.substring(0, id.length() - name.length() - 1);
    ObjectNode match = f.objectNode();
    if (base.endsWith("[x]")) {
      match.put("property", name);
      return match;
    }
    JsonNode discriminator =
        byId.getOrDefault(base, f.objectNode()).path("slicing").path("discriminator").path(0);
    String kind = discriminator.path("type").asText();
    String on = discriminator.path("path").asText();
    if (on.equals("url")) {
      match.put("url", slice.path("type").path(0).path("profile").path(0).asText());
    } else if (on.equals("code")) {
      JsonNode code = byId.getOrDefault(id + ".code", f.objectNode());
      JsonNode coding =
          code.has("patternCodeableConcept")
              ? code.path("patternCodeableConcept").path("coding").path(0)
              : code.path("fixedCodeableConcept").path("coding").path(0);
      match.put("system", coding.path("system").asText());
      match.put("code", coding.path("code").asText());
    } else if (on.equals("system")) {
      JsonNode system = byId.getOrDefault(id + ".system", f.objectNode());
      match.put(
          "system",
          system.has("fixedUri")
              ? system.path("fixedUri").asText()
              : system.path("patternUri").asText());
    } else if (kind.equals("profile") && on.equals("resource")) {
      String resourceType =
          byId.getOrDefault(id + ".resource", f.objectNode())
              .path("type")
              .path(0)
              .path("code")
              .asText();
      // entry:Encounter and entry:Location are typed "Resource" with a CH Core profile; their
      // slice names are the resource types
      match.put("resourceType", "Resource".equals(resourceType) ? name : resourceType);
    } else if (kind.equals("profile") && on.equals("$this")) {
      match.put(
          "position",
          "1".equals(slice.path("max").asText()) && slice.path("min").asInt() == 1
              ? "first"
              : "rest");
    } else {
      match.put("unsupportedDiscriminator", kind + " " + on);
    }
    return match;
  }

  private interface EntryConsumer {
    void accept(String name, byte[] bytes);
  }

  private static void untar(InputStream in, EntryConsumer consumer) throws IOException {
    byte[] header = new byte[512];
    while (in.readNBytes(header, 0, 512) == 512) {
      String name = text(header, 0, 100);
      if (name.isEmpty()) {
        return;
      }
      String prefix = text(header, 345, 155);
      if (!prefix.isEmpty()) {
        name = prefix + "/" + name;
      }
      long size =
          Long.parseLong(
              text(header, 124, 12).trim().isEmpty() ? "0" : text(header, 124, 12).trim(), 8);
      ByteArrayOutputStream body = new ByteArrayOutputStream();
      body.write(in.readNBytes((int) size));
      long padding = (512 - size % 512) % 512;
      in.readNBytes((int) padding);
      if (header[156] == '0' || header[156] == 0) {
        consumer.accept(name, body.toByteArray());
      }
    }
  }

  private static String text(byte[] header, int offset, int length) {
    int end = offset;
    while (end < offset + length && header[end] != 0) {
      end++;
    }
    return new String(header, offset, end - offset, StandardCharsets.UTF_8);
  }
}
