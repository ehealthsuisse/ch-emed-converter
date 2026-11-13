package ch.emed.converter.chmed23a.io;

import ch.emed.converter.chmed23a.mapping.MappingRule;
import ch.emed.converter.chmed23a.model.Application;
import ch.emed.converter.chmed23a.model.DosageObject;
import ch.emed.converter.chmed23a.model.Extension;
import ch.emed.converter.chmed23a.model.HealthcareOrganization;
import ch.emed.converter.chmed23a.model.HealthcarePerson;
import ch.emed.converter.chmed23a.model.MedicalData;
import ch.emed.converter.chmed23a.model.Medicament;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.Patient;
import ch.emed.converter.chmed23a.model.PatientId;
import ch.emed.converter.chmed23a.model.Posology;
import ch.emed.converter.chmed23a.model.PosologyDetail;
import ch.emed.converter.chmed23a.model.Repetition;
import ch.emed.converter.chmed23a.model.RiskCategory;
import ch.emed.converter.chmed23a.model.SequenceObject;
import ch.emed.converter.chmed23a.model.TimedDosage;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Reads CHMED23A JSON into the {@link Medication} model (mapping spec M23-IN-02, C23-OBJ-13/14,
 * C23-POS-12). Wrong JSON types and unknown object types are reported as {@code
 * CHMED_INVALID_FIELD}; unknown properties as INFORMATION; values never appear in messages.
 * Required properties are checked by the validator, not here.
 */
public final class Chmed23aJsonReader {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          // keep decimals as written (1.0 stays 1.0); the default factory strips trailing zeros
          .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

  private static final Set<String> MEDICATION =
      Set.of(
          "patient",
          "hcPerson",
          "hcOrg",
          "meds",
          "exts",
          "medType",
          "id",
          "auth",
          "rec",
          "dt",
          "rmk");
  private static final Set<String> PATIENT =
      Set.of(
          "fName", "lName", "bdt", "gender", "street", "zip", "city", "country", "lng", "ids",
          "exts", "mData", "phones", "emails");
  private static final Set<String> PATIENT_ID = Set.of("type", "sId", "val");
  private static final Set<String> MEDICAL_DATA =
      Set.of("dLstMen", "prem", "toG", "rcs", "w", "h", "exts");
  private static final Set<String> RISK_CATEGORY = Set.of("id", "rIds");
  private static final Set<String> EXTENSION = Set.of("nm", "val", "schema", "exts");
  private static final Set<String> HC_PERSON = Set.of("gln", "fName", "lName", "zsr");
  private static final Set<String> HC_ORG =
      Set.of("gln", "name", "street", "zip", "city", "country", "zsr");
  private static final Set<String> MEDICAMENT =
      Set.of(
          "id",
          "idType",
          "pos",
          "rsn",
          "autoMed",
          "prscbBy",
          "reps",
          "isNotSub",
          "sic",
          "nbPack",
          "exts");
  private static final Set<String> POSOLOGY =
      Set.of("dtFrom", "dtTo", "inRes", "po", "relMeal", "unit", "appInstr", "roa", "moa");

  private final ReportCollector report;

  private Chmed23aJsonReader(ReportCollector report) {
    this.report = report;
  }

  /**
   * Reads a CHMED23A JSON document.
   *
   * @param json UTF-8 JSON
   * @param report receives problems (phase {@code PARSE})
   * @return the model, or null if the input is not a JSON object (ERROR entry); with type errors
   *     the model is returned and the report has ERROR entries
   */
  @MappingRule({"M23-IN-02", "C23-OBJ-13", "C23-OBJ-14", "C23-POS-12"})
  public static Medication read(byte[] json, ReportCollector report) {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      report.add(
          ReportEntry.error(ReasonCode.PARSE_ERROR, Phase.PARSE, "/", "Input is not valid JSON"));
      return null;
    } catch (IOException e) {
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
    return new Chmed23aJsonReader(report).medication(root);
  }

  private Medication medication(JsonNode n) {
    known(n, "", MEDICATION);
    return new Medication(
        object(n, "", "patient", this::patient),
        object(n, "", "hcPerson", this::hcPerson),
        object(n, "", "hcOrg", this::hcOrg),
        list(n, "", "meds", this::medicament),
        list(n, "", "exts", this::extension),
        integer(n, "", "medType"),
        string(n, "", "id"),
        integer(n, "", "auth"),
        string(n, "", "rec"),
        string(n, "", "dt"),
        string(n, "", "rmk"));
  }

  private Patient patient(JsonNode n, String p) {
    known(n, p, PATIENT);
    return new Patient(
        string(n, p, "fName"),
        string(n, p, "lName"),
        string(n, p, "bdt"),
        integer(n, p, "gender"),
        string(n, p, "street"),
        string(n, p, "zip"),
        string(n, p, "city"),
        string(n, p, "country"),
        string(n, p, "lng"),
        list(n, p, "ids", this::patientId),
        list(n, p, "exts", this::extension),
        object(n, p, "mData", this::medicalData),
        list(n, p, "phones", this::stringElement),
        list(n, p, "emails", this::stringElement));
  }

  private PatientId patientId(JsonNode n, String p) {
    known(n, p, PATIENT_ID);
    return new PatientId(integer(n, p, "type"), string(n, p, "sId"), string(n, p, "val"));
  }

  private MedicalData medicalData(JsonNode n, String p) {
    known(n, p, MEDICAL_DATA);
    return new MedicalData(
        string(n, p, "dLstMen"),
        bool(n, p, "prem"),
        string(n, p, "toG"),
        list(n, p, "rcs", this::riskCategory),
        decimal(n, p, "w"),
        decimal(n, p, "h"),
        list(n, p, "exts", this::extension));
  }

  private RiskCategory riskCategory(JsonNode n, String p) {
    known(n, p, RISK_CATEGORY);
    return new RiskCategory(integer(n, p, "id"), list(n, p, "rIds", this::integerElement));
  }

  private Extension extension(JsonNode n, String p) {
    known(n, p, EXTENSION);
    return new Extension(
        string(n, p, "nm"),
        string(n, p, "val"),
        string(n, p, "schema"),
        list(n, p, "exts", this::extension));
  }

  private HealthcarePerson hcPerson(JsonNode n, String p) {
    known(n, p, HC_PERSON);
    return new HealthcarePerson(
        string(n, p, "gln"), string(n, p, "fName"), string(n, p, "lName"), string(n, p, "zsr"));
  }

  private HealthcareOrganization hcOrg(JsonNode n, String p) {
    known(n, p, HC_ORG);
    return new HealthcareOrganization(
        string(n, p, "gln"),
        string(n, p, "name"),
        string(n, p, "street"),
        string(n, p, "zip"),
        string(n, p, "city"),
        string(n, p, "country"),
        string(n, p, "zsr"));
  }

  private Medicament medicament(JsonNode n, String p) {
    known(n, p, MEDICAMENT);
    return new Medicament(
        string(n, p, "id"),
        integer(n, p, "idType"),
        list(n, p, "pos", this::posology),
        string(n, p, "rsn"),
        bool(n, p, "autoMed"),
        string(n, p, "prscbBy"),
        object(n, p, "reps", this::repetition),
        bool(n, p, "isNotSub"),
        bool(n, p, "sic"),
        decimal(n, p, "nbPack"),
        list(n, p, "exts", this::extension));
  }

  private Repetition repetition(JsonNode n, String p) {
    Integer t = type(n, p);
    if (t == null) {
      return null;
    }
    return switch (t) {
      case 1 -> {
        known(n, p, Set.of("t", "v"));
        yield new Repetition.Number(integer(n, p, "v"));
      }
      case 2 -> {
        known(n, p, Set.of("t", "d", "u"));
        yield new Repetition.Duration(integer(n, p, "d"), integer(n, p, "u"));
      }
      case 3 -> {
        known(n, p, Set.of("t", "v", "d", "u"));
        yield new Repetition.NumberAndDuration(
            integer(n, p, "v"), integer(n, p, "d"), integer(n, p, "u"));
      }
      default -> unknownType(p);
    };
  }

  private Posology posology(JsonNode n, String p) {
    known(n, p, POSOLOGY);
    return new Posology(
        string(n, p, "dtFrom"),
        string(n, p, "dtTo"),
        bool(n, p, "inRes"),
        object(n, p, "po", this::posologyDetail),
        integer(n, p, "relMeal"),
        string(n, p, "unit"),
        string(n, p, "appInstr"),
        string(n, p, "roa"),
        string(n, p, "moa"));
  }

  private PosologyDetail posologyDetail(JsonNode n, String p) {
    Integer t = type(n, p);
    if (t == null) {
      return null;
    }
    return switch (t) {
      case 1 -> {
        known(n, p, Set.of("t", "ds"));
        yield new PosologyDetail.Daily(list(n, p, "ds", this::decimalElement));
      }
      case 2 -> {
        known(n, p, Set.of("t", "text"));
        yield new PosologyDetail.FreeText(string(n, p, "text"));
      }
      case 3 -> {
        known(n, p, Set.of("t", "tdo"));
        yield new PosologyDetail.Single(object(n, p, "tdo", this::timedDosage));
      }
      case 4 -> {
        known(n, p, Set.of("t", "cyDuU", "cyDu", "tdo", "tdpc"));
        yield new PosologyDetail.Cyclic(
            integer(n, p, "cyDuU"),
            integer(n, p, "cyDu"),
            object(n, p, "tdo", this::timedDosage),
            integer(n, p, "tdpc"));
      }
      case 5 -> {
        known(n, p, Set.of("t", "sos"));
        yield new PosologyDetail.Sequence(list(n, p, "sos", this::sequenceObject));
      }
      default -> unknownType(p);
    };
  }

  private TimedDosage timedDosage(JsonNode n, String p) {
    Integer t = type(n, p);
    if (t == null) {
      return null;
    }
    return switch (t) {
      case 1 -> {
        known(n, p, Set.of("t", "do"));
        yield new TimedDosage.DosageOnly(object(n, p, "do", this::dosage));
      }
      case 2 -> {
        known(n, p, Set.of("t", "ts"));
        yield new TimedDosage.Times(list(n, p, "ts", this::atTime));
      }
      case 3 -> {
        known(n, p, Set.of("t", "ss"));
        yield new TimedDosage.DaySegments(list(n, p, "ss", this::inSegment));
      }
      case 4 -> {
        known(n, p, Set.of("t", "wds", "tdo"));
        yield new TimedDosage.WeekDays(
            list(n, p, "wds", this::integerElement), object(n, p, "tdo", this::timedDosage));
      }
      case 5 -> {
        known(n, p, Set.of("t", "doms", "tdo"));
        yield new TimedDosage.DaysOfMonth(
            list(n, p, "doms", this::integerElement), object(n, p, "tdo", this::timedDosage));
      }
      case 6 -> {
        known(n, p, Set.of("t", "do", "miDu", "miDuU"));
        yield new TimedDosage.Interval(
            object(n, p, "do", this::dosage), integer(n, p, "miDu"), integer(n, p, "miDuU"));
      }
      default -> unknownType(p);
    };
  }

  private DosageObject dosage(JsonNode n, String p) {
    Integer t = type(n, p);
    if (t == null) {
      return null;
    }
    return switch (t) {
      case 1 -> {
        known(n, p, Set.of("t", "a"));
        yield new DosageObject.Simple(decimal(n, p, "a"));
      }
      case 2 -> {
        known(n, p, Set.of("t", "aFrom", "aTo", "duU", "du"));
        yield new DosageObject.FromTo(
            decimal(n, p, "aFrom"),
            decimal(n, p, "aTo"),
            integer(n, p, "duU"),
            integer(n, p, "du"));
      }
      case 3 -> {
        known(n, p, Set.of("t", "aMin", "aMax"));
        yield new DosageObject.Range(decimal(n, p, "aMin"), decimal(n, p, "aMax"));
      }
      default -> unknownType(p);
    };
  }

  private SequenceObject sequenceObject(JsonNode n, String p) {
    Integer t = type(n, p);
    if (t == null) {
      return null;
    }
    return switch (t) {
      case 1 -> {
        known(n, p, Set.of("t", "du", "duU", "po"));
        yield new SequenceObject.PosologySequence(
            integer(n, p, "du"), integer(n, p, "duU"), object(n, p, "po", this::posologyDetail));
      }
      case 2 -> {
        known(n, p, Set.of("t", "du", "duU"));
        yield new SequenceObject.Pause(integer(n, p, "du"), integer(n, p, "duU"));
      }
      default -> unknownType(p);
    };
  }

  private Application.AtTime atTime(JsonNode n, String p) {
    known(n, p, Set.of("dt", "do"));
    return new Application.AtTime(string(n, p, "dt"), object(n, p, "do", this::dosage));
  }

  private Application.InSegment inSegment(JsonNode n, String p) {
    known(n, p, Set.of("s", "do"));
    return new Application.InSegment(integer(n, p, "s"), object(n, p, "do", this::dosage));
  }

  // -------------------------------------------------------------------------------------------

  private void known(JsonNode n, String p, Set<String> fields) {
    Iterator<String> names = n.fieldNames();
    while (names.hasNext()) {
      String name = names.next();
      if (!fields.contains(name)) {
        report.add(
            ReportEntry.information(
                ReasonCode.UNKNOWN_FIELD_IGNORED,
                Phase.PARSE,
                p + "/" + name,
                "Unknown property, ignored"));
      }
    }
  }

  /** The object type {@code t}; null after an ERROR entry. */
  private Integer type(JsonNode n, String p) {
    if (!n.has("t") || n.get("t").isNull()) {
      invalid(p + "/t", "Object type t is missing");
      return null;
    }
    return integer(n, p, "t");
  }

  private <T> T unknownType(String p) {
    invalid(p + "/t", "Unknown object type");
    return null;
  }

  private <T> T object(JsonNode n, String p, String field, BiFunction<JsonNode, String, T> reader) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    if (!v.isObject()) {
      return invalid(p + "/" + field, "Expected an object");
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
      invalid(p + "/" + field, "Expected an array");
      return List.of();
    }
    List<T> items = new ArrayList<>();
    for (int i = 0; i < v.size(); i++) {
      JsonNode item = v.get(i);
      String path = p + "/" + field + "/" + i;
      if (item.isObject() || item.isValueNode()) {
        T value = reader.apply(item, path);
        if (value != null) {
          items.add(value);
        }
      } else {
        invalid(path, "Unexpected array element");
      }
    }
    return items;
  }

  private String stringElement(JsonNode n, String p) {
    return n.isTextual() ? n.textValue() : invalid(p, "Expected a string");
  }

  private Integer integerElement(JsonNode n, String p) {
    return integerValue(n, p);
  }

  private BigDecimal decimalElement(JsonNode n, String p) {
    return n.isNumber() ? n.decimalValue() : invalid(p, "Expected a number");
  }

  private String string(JsonNode n, String p, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    if (!v.isTextual()) {
      return invalid(p + "/" + field, "Expected a string");
    }
    return v.textValue().isEmpty() ? null : v.textValue();
  }

  private Boolean bool(JsonNode n, String p, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    return v.isBoolean() ? v.booleanValue() : invalid(p + "/" + field, "Expected a boolean");
  }

  private BigDecimal decimal(JsonNode n, String p, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    return v.isNumber() ? v.decimalValue() : invalid(p + "/" + field, "Expected a number");
  }

  private Integer integer(JsonNode n, String p, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    return integerValue(v, p + "/" + field);
  }

  /** Integers given with a decimal part are rounded (conventions.md, C23-OBJ-14). */
  private Integer integerValue(JsonNode v, String path) {
    if (!v.isNumber()) {
      return invalid(path, "Expected an integer");
    }
    BigDecimal value = v.decimalValue();
    BigDecimal rounded = value.setScale(0, RoundingMode.HALF_UP);
    if (rounded.compareTo(value) != 0) {
      report.add(
          ReportEntry.information(
              ReasonCode.DERIVED_VALUE,
              Phase.PARSE,
              path,
              "Integer given with a decimal part; rounded to the closest whole number"));
    }
    try {
      return rounded.intValueExact();
    } catch (ArithmeticException e) {
      return invalid(path, "Integer out of range");
    }
  }

  private <T> T invalid(String path, String message) {
    report.add(
        ReportEntry.error(
            ReasonCode.CHMED_INVALID_FIELD, Phase.PARSE, path.isEmpty() ? "/" : path, message));
    return null;
  }
}
