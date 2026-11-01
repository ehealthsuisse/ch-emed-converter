package ch.emed.converter.chmed16a.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects sensitive values from fixtures (patient field values, raw QR payloads and eRezept
 * signature parameters) and scans text such as reports and log output for them (FR-017, SC-006).
 */
public final class PhiScanner {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String[] PATIENT_FIELDS = {
    "FName", "LName", "BDt", "Street", "Zip", "City", "Phone", "Email"
  };
  private static final Pattern SIGNATURE_PARAM = Pattern.compile("[?&#]([its])=([^&\\s]+)");
  private static final int MIN_LENGTH = 4;
  private static final int PAYLOAD_SUBSTRING = 20;

  private final Set<String> values = new LinkedHashSet<>();

  /** Adds all patient field values of a CHMED16A JSON fixture. */
  public PhiScanner addChmed16aJson(String json) {
    try {
      JsonNode patient = MAPPER.readTree(json).path("Patient");
      for (String field : PATIENT_FIELDS) {
        add(patient.path(field).asText(""));
      }
      for (JsonNode id : patient.path("Ids")) {
        add(id.path("Val").asText(""));
      }
    } catch (IOException e) {
      // not JSON (e.g. a QR payload fixture); handled by addRawPayload
    }
    return this;
  }

  /** Adds the patient field values of a CH EMED document (name, birth date, address, contacts). */
  public PhiScanner addChEmedJson(String json) {
    try {
      for (JsonNode entry : MAPPER.readTree(json).path("entry")) {
        JsonNode resource = entry.path("resource");
        if (!"Patient".equals(resource.path("resourceType").asText())) {
          continue;
        }
        for (JsonNode name : resource.path("name")) {
          add(name.path("family").asText(""));
          name.path("given").forEach(g -> add(g.asText("")));
        }
        add(resource.path("birthDate").asText(""));
        for (JsonNode address : resource.path("address")) {
          address.path("line").forEach(l -> add(l.asText("")));
          add(address.path("city").asText(""));
          add(address.path("postalCode").asText(""));
        }
        resource.path("telecom").forEach(t -> add(t.path("value").asText("")));
        resource.path("identifier").forEach(i -> add(i.path("value").asText("")));
      }
    } catch (IOException e) {
      // not JSON
    }
    return this;
  }

  /** Adds a raw QR payload or eRezept URL and its signature parameters. */
  public PhiScanner addRawPayload(String payload) {
    String trimmed = payload.trim();
    if (trimmed.length() >= PAYLOAD_SUBSTRING) {
      values.add(trimmed.substring(0, PAYLOAD_SUBSTRING));
      values.add(trimmed.substring(trimmed.length() - PAYLOAD_SUBSTRING));
    }
    Matcher m = SIGNATURE_PARAM.matcher(trimmed);
    while (m.find()) {
      add(m.group(2));
    }
    return this;
  }

  private void add(String value) {
    if (value != null && value.length() >= MIN_LENGTH) {
      values.add(value);
    }
  }

  public Set<String> values() {
    return Set.copyOf(values);
  }

  /** Returns every collected value that occurs in the text. */
  public List<String> findIn(String text) {
    List<String> found = new ArrayList<>();
    for (String value : values) {
      if (text.contains(value)) {
        found.add(value);
      }
    }
    return found;
  }
}
