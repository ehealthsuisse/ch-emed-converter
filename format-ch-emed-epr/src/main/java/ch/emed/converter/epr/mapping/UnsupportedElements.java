package ch.emed.converter.epr.mapping;

import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.MappingRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Removes the elements CH EMED EPR does not support (mapping rule M-EPR-22, research R5 and F7),
 * using the pinned list {@code epr/unsupported-elements.json} (data model §4). Works on the JSON
 * tree of a document Bundle whose profiles are already the EPR ones (M-EPR-01). Every removed
 * element is reported as {@code UNMAPPABLE_ELEMENT} with its path, never its value: a clinically
 * relevant one as WARNING with clinical loss, any other as INFORMATION. Immutable and thread-safe
 * after loading.
 */
public final class UnsupportedElements {

  /** Resource of the pinned list. */
  public static final String RESOURCE = "/epr/unsupported-elements.json";

  private record Element(String path, boolean pmlcOnly, boolean clinical) {}

  private record Profile(List<Element> elements, Map<String, JsonNode> slices) {}

  private final Map<String, Profile> profiles;

  private UnsupportedElements(Map<String, Profile> profiles) {
    this.profiles = profiles;
  }

  /**
   * Loads the pinned list.
   *
   * @return the list
   */
  public static UnsupportedElements load() {
    try (InputStream in = UnsupportedElements.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + RESOURCE);
      }
      return of(new ObjectMapper().readTree(in));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + RESOURCE, e);
    }
  }

  /**
   * @param list a list in the format of data model §4
   * @return the list
   */
  static UnsupportedElements of(JsonNode list) {
    Map<String, Profile> profiles = new HashMap<>();
    for (JsonNode p : list.path("profiles")) {
      List<Element> elements = new ArrayList<>();
      for (JsonNode e : p.path("elements")) {
        elements.add(
            new Element(
                e.path("path").asText(),
                "pmlc".equals(e.path("scope").asText()),
                e.path("clinical").asBoolean()));
      }
      Map<String, JsonNode> slices = new HashMap<>();
      for (JsonNode s : p.path("slices")) {
        slices.put(s.path("path").asText(), s.path("match"));
      }
      profiles.put(p.path("profile").asText(), new Profile(List.copyOf(elements), slices));
    }
    return new UnsupportedElements(Map.copyOf(profiles));
  }

  /**
   * Removes the unsupported elements from the Bundle, its entries and their contained resources,
   * each by the CH EMED EPR profiles it declares.
   *
   * @param bundle JSON of the document Bundle; changed in place
   * @param type document type; elements of scope {@code pmlc} are removed for PMLC only
   * @param report receives one entry per removed element
   */
  @MappingRule("M-EPR-22")
  public void remove(ObjectNode bundle, EprDocumentType type, ReportCollector report) {
    boolean pmlc = type == EprDocumentType.PMLC;
    apply(bundle, "Bundle", pmlc, report);
    JsonNode entries = bundle.path("entry");
    for (int i = 0; i < entries.size(); i++) {
      JsonNode resource = entries.get(i).path("resource");
      if (resource instanceof ObjectNode object) {
        String path = "Bundle.entry[" + i + "].resource";
        apply(object, path, pmlc, report);
        JsonNode contained = object.path("contained");
        for (int j = 0; j < contained.size(); j++) {
          if (contained.get(j) instanceof ObjectNode inner) {
            apply(inner, path + ".contained[" + j + "]", pmlc, report);
          }
        }
      }
    }
  }

  private void apply(ObjectNode resource, String path, boolean pmlc, ReportCollector report) {
    for (JsonNode url : resource.path("meta").path("profile")) {
      Profile profile = profiles.get(url.asText());
      if (profile == null) {
        continue;
      }
      for (Element element : profile.elements()) {
        if (element.pmlcOnly() && !pmlc) {
          continue;
        }
        String[] segments = element.path().split("\\.");
        walk(resource, segments, 1, segments[0], path, element, profile, report);
      }
      prune(resource);
    }
  }

  private static void walk(
      ObjectNode node,
      String[] segments,
      int index,
      String idSoFar,
      String path,
      Element element,
      Profile profile,
      ReportCollector report) {
    String segment = segments[index];
    int colon = segment.indexOf(':');
    String name = colon < 0 ? segment : segment.substring(0, colon);
    String id = idSoFar + "." + segment;
    JsonNode match = colon < 0 ? null : profile.slices().get(id);
    boolean last = index == segments.length - 1;

    List<String> properties = new ArrayList<>();
    if (name.endsWith("[x]")) {
      if (match != null && match.has("property")) {
        properties.add(match.path("property").asText());
      } else {
        String stem = name.substring(0, name.length() - 3);
        node.fieldNames()
            .forEachRemaining(
                f -> {
                  if (f.startsWith(stem)
                      && f.length() > stem.length()
                      && Character.isUpperCase(f.charAt(stem.length()))) {
                    properties.add(f);
                  }
                });
      }
      match = null;
    } else {
      properties.add(name);
    }

    for (String property : properties) {
      JsonNode child = node.get(property);
      JsonNode extensions = node.get("_" + property);
      String childPath = path + "." + property;
      if (match == null && last) {
        if (child != null || extensions != null) {
          node.remove(property);
          node.remove("_" + property);
          report(childPath, element, report);
        }
        continue;
      }
      if (match != null) {
        if (!(child instanceof ArrayNode array)) {
          continue;
        }
        for (int i = array.size() - 1; i >= 0; i--) {
          if (!matches(match, array.get(i), i)) {
            continue;
          }
          if (last) {
            array.remove(i);
            report(childPath + "[" + i + "]", element, report);
          } else if (array.get(i) instanceof ObjectNode item) {
            walk(
                item, segments, index + 1, id, childPath + "[" + i + "]", element, profile, report);
          }
        }
        continue;
      }
      descend(child, segments, index, id, childPath, element, profile, report);
      descend(extensions, segments, index, id, childPath, element, profile, report);
    }
  }

  private static void descend(
      JsonNode child,
      String[] segments,
      int index,
      String id,
      String path,
      Element element,
      Profile profile,
      ReportCollector report) {
    if (child instanceof ObjectNode object) {
      walk(object, segments, index + 1, id, path, element, profile, report);
    } else if (child instanceof ArrayNode array) {
      for (int i = 0; i < array.size(); i++) {
        if (array.get(i) instanceof ObjectNode item) {
          walk(item, segments, index + 1, id, path + "[" + i + "]", element, profile, report);
        }
      }
    }
  }

  private static boolean matches(JsonNode match, JsonNode item, int position) {
    if (match.has("url")) {
      return match.path("url").asText().equals(item.path("url").asText());
    }
    if (match.has("resourceType")) {
      return match
          .path("resourceType")
          .asText()
          .equals(item.path("resource").path("resourceType").asText());
    }
    if (match.has("position")) {
      return "first".equals(match.path("position").asText()) == (position == 0);
    }
    if (match.has("code")) {
      for (JsonNode coding : item.path("code").path("coding")) {
        if (match.path("system").asText().equals(coding.path("system").asText())
            && match.path("code").asText().equals(coding.path("code").asText())) {
          return true;
        }
      }
      return false;
    }
    if (match.has("system")) {
      return match.path("system").asText().equals(item.path("system").asText());
    }
    return false;
  }

  private static void report(String path, Element element, ReportCollector report) {
    String message = "Element not supported by CH EMED EPR removed (M-EPR-22)";
    report.add(
        element.clinical()
            ? ReportEntry.warning(ReasonCode.UNMAPPABLE_ELEMENT, Phase.MAP, path, message)
                .withClinicalLoss(true)
            : ReportEntry.information(ReasonCode.UNMAPPABLE_ELEMENT, Phase.MAP, path, message));
  }

  /** Removes objects and arrays left empty by a removal; FHIR JSON does not allow them. */
  private static boolean prune(JsonNode node) {
    if (node instanceof ObjectNode object) {
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      while (fields.hasNext()) {
        if (prune(fields.next().getValue())) {
          fields.remove();
        }
      }
      return object.isEmpty();
    }
    if (node instanceof ArrayNode array) {
      // arrays of primitive extensions ("_given") keep their positions with null
      boolean positional = false;
      for (JsonNode item : array) {
        positional |= item.isNull();
      }
      for (int i = array.size() - 1; i >= 0; i--) {
        if (prune(array.get(i))) {
          if (positional) {
            array.set(i, NullNode.getInstance());
          } else {
            array.remove(i);
          }
        }
      }
      boolean empty = true;
      for (JsonNode item : array) {
        empty &= item.isNull();
      }
      return empty;
    }
    return false;
  }
}
