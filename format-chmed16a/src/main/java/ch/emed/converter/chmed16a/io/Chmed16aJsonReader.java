package ch.emed.converter.chmed16a.io;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.model.HcOrg;
import ch.emed.converter.chmed16a.model.HcPerson;
import ch.emed.converter.chmed16a.model.Measurement;
import ch.emed.converter.chmed16a.model.MedicalData;
import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.model.PField;
import ch.emed.converter.chmed16a.model.Patient;
import ch.emed.converter.chmed16a.model.PatientId;
import ch.emed.converter.chmed16a.model.Posology;
import ch.emed.converter.chmed16a.model.RiskCategory;
import ch.emed.converter.chmed16a.model.TakingTime;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Reads CHMED16A input in plain JSON or textual QR payload form into the {@link Medication} model
 * (FR-001, mapping spec M-IN-01..04). Type errors are reported as {@code CHMED_INVALID_FIELD};
 * unknown fields and private fields are ignored and reported as INFORMATION. Field values never
 * appear in messages.
 */
public final class Chmed16aJsonReader {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  private static final Set<String> MEDICATION_FIELDS =
      Set.of(
          "Patient",
          "Medicaments",
          "Recoms",
          "MedType",
          "Id",
          "Auth",
          "Zsr",
          "Dt",
          "Rmk",
          "ValBy",
          "ValDt",
          "rev",
          "HcPerson",
          "HcOrg",
          "PSchema",
          "PFields");
  private static final Set<String> PATIENT_FIELDS =
      Set.of(
          "FName", "LName", "BDt", "Gender", "Street", "Zip", "City", "Lng", "Phone", "Email",
          "Rcv", "Ids", "Med", "PFields");
  private static final Set<String> PATIENT_ID_FIELDS = Set.of("Type", "Val");
  private static final Set<String> MEDICAL_DATA_FIELDS =
      Set.of("DLstMen", "Prem", "ToG", "Rc", "Meas", "PFields");
  private static final Set<String> RISK_FIELDS = Set.of("Id", "R");
  private static final Set<String> MEASUREMENT_FIELDS = Set.of("Type", "Val", "Unit");
  private static final Set<String> MEDICAMENT_FIELDS =
      Set.of(
          "Id",
          "IdType",
          "Pos",
          "Unit",
          "TkgRsn",
          "AppInstr",
          "AutoMed",
          "PrscbBy",
          "Roa",
          "Rep",
          "Subs",
          "NbPack",
          "IndC",
          "PFields");
  private static final Set<String> POSOLOGY_FIELDS =
      Set.of("DtFrom", "DtTo", "CyDu", "InRes", "D", "TT");
  private static final Set<String> TAKING_TIME_FIELDS =
      Set.of("Off", "Du", "DoFrom", "DoTo", "A", "MA");
  private static final Set<String> HC_PERSON_FIELDS = Set.of("Gln", "FName", "LName", "Zsr");
  private static final Set<String> HC_ORG_FIELDS =
      Set.of("Name", "NameAffix", "Street", "Zip", "City", "Country", "Zsr", "Phone", "Email");
  private static final Set<String> PFIELD_FIELDS = Set.of("Nm", "Val", "PFields");

  private Chmed16aJsonReader() {}

  /**
   * Reads CHMED16A input.
   *
   * @param input UTF-8 JSON or textual QR payload
   * @param options options (decoder size limit)
   * @param report receives problems
   * @return the model, or null if the input cannot be read (ERROR entries)
   */
  @MappingRule({"M-IN-01", "M-IN-02", "M-IN-03", "M-IN-04", "M-IN-06"})
  public static Medication read(byte[] input, ConversionOptions options, ReportCollector report) {
    String text = new String(input, StandardCharsets.UTF_8).strip();
    if (text.startsWith("﻿")) {
      text = text.substring(1).strip();
    }
    byte[] json;
    if (text.startsWith("{")) {
      json = text.getBytes(StandardCharsets.UTF_8);
    } else if (Chmed16aPayloadDecoder.isPayload(text)) {
      try {
        json = Chmed16aPayloadDecoder.decode(text, options.maxDecodedSize());
      } catch (Chmed16aPayloadDecoder.DecodingException e) {
        report.add(
            ReportEntry.error(
                e.unsupportedVersion() ? ReasonCode.UNSUPPORTED_VERSION : ReasonCode.PARSE_ERROR,
                Phase.PARSE,
                "/",
                e.getMessage()));
        return null;
      }
    } else if (text.regionMatches(true, 0, "http", 0, 4)) {
      report.add(
          ReportEntry.error(
              ReasonCode.PARSE_ERROR,
              Phase.PARSE,
              "/",
              "Input looks like a URL (e.g. a signed eRezept QR code); signed eRezept URLs and QR"
                  + " images are handled by the QR/eRezept feature"));
      return null;
    } else {
      report.add(
          ReportEntry.error(
              ReasonCode.PARSE_ERROR,
              Phase.PARSE,
              "/",
              "Input is neither CHMED16A JSON nor a CHMED16A QR payload"));
      return null;
    }

    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      report.add(
          ReportEntry.error(ReasonCode.PARSE_ERROR, Phase.PARSE, "/", "Input is not valid JSON"));
      return null;
    } catch (java.io.IOException e) {
      report.add(
          ReportEntry.error(ReasonCode.PARSE_ERROR, Phase.PARSE, "/", "Input cannot be read"));
      return null;
    }
    if (root == null || !root.isObject()) {
      report.add(
          ReportEntry.error(
              ReasonCode.PARSE_ERROR, Phase.PARSE, "/", "Input is not a JSON object"));
      return null;
    }
    if (!root.has("Patient") && (root.has("patient") || root.has("meds") || root.has("medType"))) {
      report.add(
          ReportEntry.error(
              ReasonCode.UNSUPPORTED_VERSION,
              Phase.PARSE,
              "/",
              "Input is a CHMED23A document (camelCase keys); only CHMED16A is supported by this"
                  + " conversion"));
      return null;
    }
    Reader reader = new Reader(report);
    Medication medication = reader.medication(root);
    return reader.failed ? null : medication;
  }

  /** Stateful single-use reader collecting type errors. */
  private static final class Reader {
    private final ReportCollector report;
    private boolean failed;

    Reader(ReportCollector report) {
      this.report = report;
    }

    Medication medication(JsonNode n) {
      unknown(n, "", MEDICATION_FIELDS);
      privateFields(n, "");
      return new Medication(
          object(n, "", "Patient", this::patient),
          list(n, "", "Medicaments", this::medicament),
          n.has("Recoms"),
          integer(n, "", "MedType"),
          string(n, "", "Id"),
          string(n, "", "Auth"),
          string(n, "", "Zsr"),
          string(n, "", "Dt"),
          string(n, "", "Rmk"),
          string(n, "", "ValBy"),
          string(n, "", "ValDt"),
          integer(n, "", "rev"),
          object(n, "", "HcPerson", this::hcPerson),
          object(n, "", "HcOrg", this::hcOrg),
          string(n, "", "PSchema"),
          list(n, "", "PFields", this::pField));
    }

    Patient patient(JsonNode n, String p) {
      unknown(n, p, PATIENT_FIELDS);
      privateFields(n, p);
      return new Patient(
          string(n, p, "FName"),
          string(n, p, "LName"),
          string(n, p, "BDt"),
          integer(n, p, "Gender"),
          string(n, p, "Street"),
          string(n, p, "Zip"),
          string(n, p, "City"),
          string(n, p, "Lng"),
          string(n, p, "Phone"),
          string(n, p, "Email"),
          string(n, p, "Rcv"),
          list(n, p, "Ids", this::patientId),
          object(n, p, "Med", this::medicalData),
          list(n, p, "PFields", this::pField));
    }

    PatientId patientId(JsonNode n, String p) {
      unknown(n, p, PATIENT_ID_FIELDS);
      return new PatientId(integer(n, p, "Type"), string(n, p, "Val"));
    }

    MedicalData medicalData(JsonNode n, String p) {
      unknown(n, p, MEDICAL_DATA_FIELDS);
      privateFields(n, p);
      return new MedicalData(
          string(n, p, "DLstMen"),
          integer(n, p, "Prem"),
          string(n, p, "ToG"),
          list(n, p, "Rc", this::riskCategory),
          list(n, p, "Meas", this::measurement),
          list(n, p, "PFields", this::pField));
    }

    RiskCategory riskCategory(JsonNode n, String p) {
      unknown(n, p, RISK_FIELDS);
      return new RiskCategory(integer(n, p, "Id"), integerList(n, p, "R"));
    }

    Measurement measurement(JsonNode n, String p) {
      unknown(n, p, MEASUREMENT_FIELDS);
      return new Measurement(integer(n, p, "Type"), string(n, p, "Val"), integer(n, p, "Unit"));
    }

    Medicament medicament(JsonNode n, String p) {
      unknown(n, p, MEDICAMENT_FIELDS);
      privateFields(n, p);
      return new Medicament(
          string(n, p, "Id"),
          integer(n, p, "IdType"),
          list(n, p, "Pos", this::posology),
          string(n, p, "Unit"),
          string(n, p, "TkgRsn"),
          string(n, p, "AppInstr"),
          integer(n, p, "AutoMed"),
          string(n, p, "PrscbBy"),
          string(n, p, "Roa"),
          integer(n, p, "Rep"),
          integer(n, p, "Subs"),
          longValue(n, p, "NbPack"),
          string(n, p, "IndC"),
          list(n, p, "PFields", this::pField));
    }

    Posology posology(JsonNode n, String p) {
      unknown(n, p, POSOLOGY_FIELDS);
      return new Posology(
          string(n, p, "DtFrom"),
          string(n, p, "DtTo"),
          longValue(n, p, "CyDu"),
          integer(n, p, "InRes"),
          decimalList(n, p, "D"),
          list(n, p, "TT", this::takingTime));
    }

    TakingTime takingTime(JsonNode n, String p) {
      unknown(n, p, TAKING_TIME_FIELDS);
      return new TakingTime(
          longValue(n, p, "Off"),
          longValue(n, p, "Du"),
          decimal(n, p, "DoFrom"),
          decimal(n, p, "DoTo"),
          decimal(n, p, "A"),
          decimal(n, p, "MA"));
    }

    HcPerson hcPerson(JsonNode n, String p) {
      unknown(n, p, HC_PERSON_FIELDS);
      return new HcPerson(
          string(n, p, "Gln"), string(n, p, "FName"), string(n, p, "LName"), string(n, p, "Zsr"));
    }

    HcOrg hcOrg(JsonNode n, String p) {
      unknown(n, p, HC_ORG_FIELDS);
      return new HcOrg(
          string(n, p, "Name"),
          string(n, p, "NameAffix"),
          string(n, p, "Street"),
          string(n, p, "Zip"),
          string(n, p, "City"),
          string(n, p, "Country"),
          string(n, p, "Zsr"),
          string(n, p, "Phone"),
          string(n, p, "Email"));
    }

    PField pField(JsonNode n, String p) {
      unknown(n, p, PFIELD_FIELDS);
      return new PField(
          string(n, p, "Nm"), string(n, p, "Val"), list(n, p, "PFields", this::pField));
    }

    private void unknown(JsonNode n, String path, Set<String> known) {
      for (Iterator<String> it = n.fieldNames(); it.hasNext(); ) {
        String name = it.next();
        if (!known.contains(name)) {
          report.add(
              ReportEntry.information(
                  ReasonCode.UNKNOWN_FIELD_IGNORED,
                  Phase.PARSE,
                  path + "/" + name,
                  "Unknown field ignored as required by the CHMED16A specification"));
        }
      }
    }

    private void privateFields(JsonNode n, String path) {
      if (n.has("PFields") && n.get("PFields").size() > 0) {
        report.add(
            ReportEntry.information(
                ReasonCode.UNKNOWN_FIELD_IGNORED,
                Phase.PARSE,
                path + "/PFields",
                "Private fields are not mapped"));
      }
    }

    private void invalid(String path, String expected) {
      failed = true;
      report.add(
          ReportEntry.error(
              ReasonCode.CHMED_INVALID_FIELD,
              Phase.VALIDATE_INPUT,
              path,
              "Field has the wrong JSON type, expected " + expected));
    }

    private String string(JsonNode n, String p, String field) {
      JsonNode v = n.get(field);
      if (v == null || v.isNull()) {
        return null;
      }
      if (!v.isTextual()) {
        invalid(p + "/" + field, "string");
        return null;
      }
      return v.textValue();
    }

    private Integer integer(JsonNode n, String p, String field) {
      JsonNode v = n.get(field);
      if (v == null || v.isNull()) {
        return null;
      }
      if (!v.isIntegralNumber() || !v.canConvertToInt()) {
        invalid(p + "/" + field, "integer");
        return null;
      }
      return v.intValue();
    }

    private Long longValue(JsonNode n, String p, String field) {
      JsonNode v = n.get(field);
      if (v == null || v.isNull()) {
        return null;
      }
      if (!v.isIntegralNumber() || !v.canConvertToLong()) {
        invalid(p + "/" + field, "integer");
        return null;
      }
      return v.longValue();
    }

    private BigDecimal decimal(JsonNode n, String p, String field) {
      JsonNode v = n.get(field);
      if (v == null || v.isNull()) {
        return null;
      }
      if (!v.isNumber()) {
        invalid(p + "/" + field, "number");
        return null;
      }
      return v.decimalValue();
    }

    private List<BigDecimal> decimalList(JsonNode n, String p, String field) {
      JsonNode v = n.get(field);
      if (v == null || v.isNull()) {
        return List.of();
      }
      if (!v.isArray()) {
        invalid(p + "/" + field, "array of numbers");
        return List.of();
      }
      List<BigDecimal> values = new ArrayList<>();
      for (int i = 0; i < v.size(); i++) {
        if (!v.get(i).isNumber()) {
          invalid(p + "/" + field + "/" + i, "number");
        } else {
          values.add(v.get(i).decimalValue());
        }
      }
      return values;
    }

    private List<Integer> integerList(JsonNode n, String p, String field) {
      JsonNode v = n.get(field);
      if (v == null || v.isNull()) {
        return List.of();
      }
      if (!v.isArray()) {
        invalid(p + "/" + field, "array of integers");
        return List.of();
      }
      List<Integer> values = new ArrayList<>();
      for (int i = 0; i < v.size(); i++) {
        if (!v.get(i).isIntegralNumber()) {
          invalid(p + "/" + field + "/" + i, "integer");
        } else {
          values.add(v.get(i).intValue());
        }
      }
      return values;
    }

    private <T> T object(
        JsonNode n, String p, String field, BiFunction<JsonNode, String, T> reader) {
      JsonNode v = n.get(field);
      if (v == null || v.isNull()) {
        return null;
      }
      if (!v.isObject()) {
        invalid(p + "/" + field, "object");
        return null;
      }
      return reader.apply(v, p + "/" + field);
    }

    private <T> List<T> list(
        JsonNode n, String p, String field, BiFunction<JsonNode, String, T> reader) {
      JsonNode v = n.get(field);
      if (v == null || v.isNull()) {
        return List.of();
      }
      if (!v.isArray()) {
        invalid(p + "/" + field, "array");
        return List.of();
      }
      List<T> values = new ArrayList<>();
      for (int i = 0; i < v.size(); i++) {
        String path = p + "/" + field + "/" + i;
        if (!v.get(i).isObject()) {
          invalid(path, "object");
        } else {
          values.add(reader.apply(v.get(i), path));
        }
      }
      return values;
    }
  }
}
