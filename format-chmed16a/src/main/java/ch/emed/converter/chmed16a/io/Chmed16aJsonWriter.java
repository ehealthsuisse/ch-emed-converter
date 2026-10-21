package ch.emed.converter.chmed16a.io;

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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Writes a {@link Medication} as CHMED16A JSON: UTF-8, no pretty printing, properties in the order
 * of the specification tables, absent values and empty lists omitted (defaults such as {@code CyDu}
 * or {@code NbPack} are never materialised). The list {@code Medicaments} is always written.
 */
public final class Chmed16aJsonWriter {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

  private Chmed16aJsonWriter() {}

  /**
   * Serializes the document.
   *
   * @param med the document
   * @return UTF-8 JSON
   */
  public static byte[] write(Medication med) {
    ObjectNode n = MAPPER.createObjectNode();
    put(n, "Patient", med.patient() == null ? null : patient(med.patient()));
    ArrayNode medicaments = n.putArray("Medicaments");
    med.medicaments().forEach(m -> medicaments.add(medicament(m)));
    put(n, "MedType", med.medType());
    put(n, "Id", med.id());
    put(n, "Auth", med.auth());
    put(n, "Zsr", med.zsr());
    put(n, "Dt", med.dt());
    put(n, "Rmk", med.rmk());
    put(n, "ValBy", med.valBy());
    put(n, "ValDt", med.valDt());
    put(n, "rev", med.rev());
    put(n, "HcPerson", med.hcPerson() == null ? null : hcPerson(med.hcPerson()));
    put(n, "HcOrg", med.hcOrg() == null ? null : hcOrg(med.hcOrg()));
    put(n, "PSchema", med.pSchema());
    list(n, "PFields", med.pFields(), Chmed16aJsonWriter::pField);
    try {
      return MAPPER.writeValueAsBytes(n);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("CHMED16A JSON cannot be written", e);
    }
  }

  private static ObjectNode patient(Patient p) {
    ObjectNode n = MAPPER.createObjectNode();
    put(n, "FName", p.fName());
    put(n, "LName", p.lName());
    put(n, "BDt", p.bDt());
    put(n, "Gender", p.gender());
    put(n, "Street", p.street());
    put(n, "Zip", p.zip());
    put(n, "City", p.city());
    put(n, "Lng", p.lng());
    put(n, "Phone", p.phone());
    put(n, "Email", p.email());
    put(n, "Rcv", p.rcv());
    list(n, "Ids", p.ids(), Chmed16aJsonWriter::patientId);
    put(n, "Med", p.med() == null ? null : medicalData(p.med()));
    list(n, "PFields", p.pFields(), Chmed16aJsonWriter::pField);
    return n;
  }

  private static void patientId(ArrayNode out, PatientId id) {
    ObjectNode n = out.addObject();
    put(n, "Type", id.type());
    put(n, "Val", id.val());
  }

  private static ObjectNode medicalData(MedicalData m) {
    ObjectNode n = MAPPER.createObjectNode();
    put(n, "DLstMen", m.dLstMen());
    put(n, "Prem", m.prem());
    put(n, "ToG", m.toG());
    list(n, "Rc", m.rc(), Chmed16aJsonWriter::riskCategory);
    list(n, "Meas", m.meas(), Chmed16aJsonWriter::measurement);
    list(n, "PFields", m.pFields(), Chmed16aJsonWriter::pField);
    return n;
  }

  private static void riskCategory(ArrayNode out, RiskCategory rc) {
    ObjectNode n = out.addObject();
    put(n, "Id", rc.id());
    ArrayNode risks = n.putArray("R");
    rc.r().forEach(risks::add);
  }

  private static void measurement(ArrayNode out, Measurement m) {
    ObjectNode n = out.addObject();
    put(n, "Type", m.type());
    put(n, "Val", m.val());
    put(n, "Unit", m.unit());
  }

  private static ObjectNode medicament(Medicament m) {
    ObjectNode n = MAPPER.createObjectNode();
    put(n, "Id", m.id());
    put(n, "IdType", m.idType());
    list(n, "Pos", m.pos(), Chmed16aJsonWriter::posology);
    put(n, "Unit", m.unit());
    put(n, "TkgRsn", m.tkgRsn());
    put(n, "AppInstr", m.appInstr());
    put(n, "AutoMed", m.autoMed());
    put(n, "PrscbBy", m.prscbBy());
    put(n, "Roa", m.roa());
    put(n, "Rep", m.rep());
    put(n, "Subs", m.subs());
    put(n, "NbPack", m.nbPack());
    put(n, "IndC", m.indC());
    list(n, "PFields", m.pFields(), Chmed16aJsonWriter::pField);
    return n;
  }

  private static void posology(ArrayNode out, Posology p) {
    ObjectNode n = out.addObject();
    put(n, "DtFrom", p.dtFrom());
    put(n, "DtTo", p.dtTo());
    put(n, "CyDu", p.cyDu());
    put(n, "InRes", p.inRes());
    if (!p.d().isEmpty()) {
      ArrayNode doses = n.putArray("D");
      p.d().forEach(d -> doses.add(number(d)));
    }
    list(n, "TT", p.tt(), Chmed16aJsonWriter::takingTime);
  }

  private static void takingTime(ArrayNode out, TakingTime t) {
    ObjectNode n = out.addObject();
    put(n, "Off", t.off());
    put(n, "Du", t.du());
    putNumber(n, "DoFrom", t.doFrom());
    putNumber(n, "DoTo", t.doTo());
    putNumber(n, "A", t.a());
    putNumber(n, "MA", t.ma());
  }

  private static ObjectNode hcPerson(HcPerson p) {
    ObjectNode n = MAPPER.createObjectNode();
    put(n, "Gln", p.gln());
    put(n, "FName", p.fName());
    put(n, "LName", p.lName());
    put(n, "Zsr", p.zsr());
    return n;
  }

  private static ObjectNode hcOrg(HcOrg o) {
    ObjectNode n = MAPPER.createObjectNode();
    put(n, "Name", o.name());
    put(n, "NameAffix", o.nameAffix());
    put(n, "Street", o.street());
    put(n, "Zip", o.zip());
    put(n, "City", o.city());
    put(n, "Country", o.country());
    put(n, "Zsr", o.zsr());
    put(n, "Phone", o.phone());
    put(n, "Email", o.email());
    return n;
  }

  private static void pField(ArrayNode out, PField f) {
    ObjectNode n = out.addObject();
    put(n, "Nm", f.nm());
    put(n, "Val", f.val());
    list(n, "PFields", f.pFields(), Chmed16aJsonWriter::pField);
  }

  // ---------------------------------------------------------------------------------------------

  private static void put(ObjectNode n, String name, String value) {
    if (value != null) {
      n.put(name, value);
    }
  }

  private static void put(ObjectNode n, String name, Integer value) {
    if (value != null) {
      n.put(name, value);
    }
  }

  private static void put(ObjectNode n, String name, Long value) {
    if (value != null) {
      n.put(name, value);
    }
  }

  private static void put(ObjectNode n, String name, ObjectNode value) {
    if (value != null) {
      n.set(name, value);
    }
  }

  private static void putNumber(ObjectNode n, String name, BigDecimal value) {
    if (value != null) {
      n.set(name, number(value));
    }
  }

  private static com.fasterxml.jackson.databind.JsonNode number(BigDecimal value) {
    BigDecimal stripped = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    if (stripped.scale() < 0) {
      stripped = stripped.setScale(0);
    }
    return MAPPER.getNodeFactory().numberNode(stripped);
  }

  private static <T> void list(
      ObjectNode n, String name, List<T> values, BiConsumer<ArrayNode, T> writer) {
    if (values.isEmpty()) {
      return;
    }
    ArrayNode array = n.putArray(name);
    values.forEach(v -> writer.accept(array, v));
  }
}
