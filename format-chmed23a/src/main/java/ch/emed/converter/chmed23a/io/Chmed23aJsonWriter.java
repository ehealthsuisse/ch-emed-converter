package ch.emed.converter.chmed23a.io;

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
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Writes the {@link Medication} model as compact CHMED23A JSON (UTF-8, no line breaks). The
 * property order follows the official examples (examples.md); absent values and empty lists are
 * omitted, as the specification recommends to keep QR payloads small; defaults are not
 * materialised; decimals keep their precision. Equal models give equal bytes.
 */
public final class Chmed23aJsonWriter {

  private static final JsonFactory FACTORY = new JsonFactory();

  private final JsonGenerator g;

  private Chmed23aJsonWriter(JsonGenerator g) {
    this.g = g;
  }

  /**
   * Serializes a document.
   *
   * @param medication the document
   * @return UTF-8 JSON
   */
  public static byte[] write(Medication medication) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (JsonGenerator g = FACTORY.createGenerator(out)) {
      g.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
      new Chmed23aJsonWriter(g).medication(medication);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  private void medication(Medication m) throws IOException {
    g.writeStartObject();
    if (m.patient() != null) {
      g.writeFieldName("patient");
      patient(m.patient());
    }
    if (!m.meds().isEmpty()) {
      g.writeArrayFieldStart("meds");
      for (Medicament med : m.meds()) {
        medicament(med);
      }
      g.writeEndArray();
    }
    number("medType", m.medType());
    string("id", m.id());
    number("auth", m.auth());
    string("rec", m.rec());
    string("dt", m.dt());
    string("rmk", m.rmk());
    if (m.hcPerson() != null) {
      g.writeFieldName("hcPerson");
      person(m.hcPerson());
    }
    if (m.hcOrg() != null) {
      g.writeFieldName("hcOrg");
      organization(m.hcOrg());
    }
    extensions(m.exts());
    g.writeEndObject();
  }

  private void patient(Patient p) throws IOException {
    g.writeStartObject();
    string("fName", p.fName());
    string("lName", p.lName());
    string("bdt", p.bdt());
    number("gender", p.gender());
    string("street", p.street());
    string("zip", p.zip());
    string("city", p.city());
    string("country", p.country());
    string("lng", p.lng());
    strings("phones", p.phones());
    strings("emails", p.emails());
    if (!p.ids().isEmpty()) {
      g.writeArrayFieldStart("ids");
      for (PatientId id : p.ids()) {
        g.writeStartObject();
        number("type", id.type());
        string("sId", id.sId());
        string("val", id.val());
        g.writeEndObject();
      }
      g.writeEndArray();
    }
    if (p.mData() != null) {
      g.writeFieldName("mData");
      medicalData(p.mData());
    }
    extensions(p.exts());
    g.writeEndObject();
  }

  private void medicalData(MedicalData d) throws IOException {
    g.writeStartObject();
    if (!d.rcs().isEmpty()) {
      g.writeArrayFieldStart("rcs");
      for (RiskCategory rc : d.rcs()) {
        g.writeStartObject();
        number("id", rc.id());
        integers("rIds", rc.rIds());
        g.writeEndObject();
      }
      g.writeEndArray();
    }
    decimal("w", d.w());
    decimal("h", d.h());
    string("dLstMen", d.dLstMen());
    bool("prem", d.prem());
    string("toG", d.toG());
    extensions(d.exts());
    g.writeEndObject();
  }

  private void person(HealthcarePerson p) throws IOException {
    g.writeStartObject();
    string("gln", p.gln());
    string("fName", p.fName());
    string("lName", p.lName());
    string("zsr", p.zsr());
    g.writeEndObject();
  }

  private void organization(HealthcareOrganization o) throws IOException {
    g.writeStartObject();
    string("gln", o.gln());
    string("name", o.name());
    string("street", o.street());
    string("zip", o.zip());
    string("city", o.city());
    string("country", o.country());
    string("zsr", o.zsr());
    g.writeEndObject();
  }

  private void medicament(Medicament m) throws IOException {
    g.writeStartObject();
    string("id", m.id());
    number("idType", m.idType());
    if (!m.pos().isEmpty()) {
      g.writeArrayFieldStart("pos");
      for (Posology p : m.pos()) {
        posology(p);
      }
      g.writeEndArray();
    }
    string("rsn", m.rsn());
    bool("autoMed", m.autoMed());
    string("prscbBy", m.prscbBy());
    if (m.reps() != null) {
      g.writeFieldName("reps");
      repetition(m.reps());
    }
    bool("isNotSub", m.isNotSub());
    bool("sic", m.sic());
    decimal("nbPack", m.nbPack());
    extensions(m.exts());
    g.writeEndObject();
  }

  private void repetition(Repetition r) throws IOException {
    g.writeStartObject();
    g.writeNumberField("t", r.type());
    switch (r) {
      case Repetition.Number n -> number("v", n.v());
      case Repetition.Duration d -> {
        number("u", d.u());
        number("d", d.d());
      }
      case Repetition.NumberAndDuration nd -> {
        number("v", nd.v());
        number("u", nd.u());
        number("d", nd.d());
      }
    }
    g.writeEndObject();
  }

  private void posology(Posology p) throws IOException {
    g.writeStartObject();
    string("dtFrom", p.dtFrom());
    string("dtTo", p.dtTo());
    if (p.po() != null) {
      g.writeFieldName("po");
      detail(p.po());
    }
    number("relMeal", p.relMeal());
    bool("inRes", p.inRes());
    string("roa", p.roa());
    string("moa", p.moa());
    string("unit", p.unit());
    string("appInstr", p.appInstr());
    g.writeEndObject();
  }

  private void detail(PosologyDetail d) throws IOException {
    g.writeStartObject();
    g.writeNumberField("t", d.type());
    switch (d) {
      case PosologyDetail.Daily daily -> decimals("ds", daily.ds());
      case PosologyDetail.FreeText f -> string("text", f.text());
      case PosologyDetail.Single s -> timedField("tdo", s.tdo());
      case PosologyDetail.Cyclic c -> {
        number("cyDuU", c.cyDuU());
        number("cyDu", c.cyDu());
        timedField("tdo", c.tdo());
        number("tdpc", c.tdpc());
      }
      case PosologyDetail.Sequence s -> {
        g.writeArrayFieldStart("sos");
        for (SequenceObject so : s.sos()) {
          g.writeStartObject();
          g.writeNumberField("t", so.type());
          if (so instanceof SequenceObject.PosologySequence ps && ps.po() != null) {
            g.writeFieldName("po");
            detail(ps.po());
          }
          number("duU", so.duU());
          number("du", so.du());
          g.writeEndObject();
        }
        g.writeEndArray();
      }
    }
    g.writeEndObject();
  }

  private void timedField(String name, TimedDosage tdo) throws IOException {
    if (tdo == null) {
      return;
    }
    g.writeFieldName(name);
    g.writeStartObject();
    g.writeNumberField("t", tdo.type());
    switch (tdo) {
      case TimedDosage.DosageOnly d -> dose(d.dose());
      case TimedDosage.Times t -> {
        g.writeArrayFieldStart("ts");
        for (Application.AtTime a : t.ts()) {
          g.writeStartObject();
          string("dt", a.dt());
          dose(a.dose());
          g.writeEndObject();
        }
        g.writeEndArray();
      }
      case TimedDosage.DaySegments s -> {
        g.writeArrayFieldStart("ss");
        for (Application.InSegment a : s.ss()) {
          g.writeStartObject();
          number("s", a.s());
          dose(a.dose());
          g.writeEndObject();
        }
        g.writeEndArray();
      }
      case TimedDosage.WeekDays w -> {
        integers("wds", w.wds());
        timedField("tdo", w.tdo());
      }
      case TimedDosage.DaysOfMonth d -> {
        integers("doms", d.doms());
        timedField("tdo", d.tdo());
      }
      case TimedDosage.Interval i -> {
        dose(i.dose());
        number("miDuU", i.miDuU());
        number("miDu", i.miDu());
      }
    }
    g.writeEndObject();
  }

  private void dose(DosageObject d) throws IOException {
    if (d == null) {
      return;
    }
    g.writeFieldName("do");
    g.writeStartObject();
    g.writeNumberField("t", d.type());
    switch (d) {
      case DosageObject.Simple s -> decimal("a", s.a());
      case DosageObject.FromTo f -> {
        decimal("aFrom", f.aFrom());
        decimal("aTo", f.aTo());
        number("duU", f.duU());
        number("du", f.du());
      }
      case DosageObject.Range r -> {
        decimal("aMin", r.aMin());
        decimal("aMax", r.aMax());
      }
    }
    g.writeEndObject();
  }

  private void extensions(List<Extension> exts) throws IOException {
    if (exts.isEmpty()) {
      return;
    }
    g.writeArrayFieldStart("exts");
    for (Extension e : exts) {
      g.writeStartObject();
      string("nm", e.nm());
      string("val", e.val());
      string("schema", e.schema());
      extensions(e.exts());
      g.writeEndObject();
    }
    g.writeEndArray();
  }

  private void string(String name, String value) throws IOException {
    if (value != null && !value.isEmpty()) {
      g.writeStringField(name, value);
    }
  }

  private void number(String name, Integer value) throws IOException {
    if (value != null) {
      g.writeNumberField(name, value);
    }
  }

  private void decimal(String name, BigDecimal value) throws IOException {
    if (value != null) {
      g.writeNumberField(name, value);
    }
  }

  private void bool(String name, Boolean value) throws IOException {
    if (value != null) {
      g.writeBooleanField(name, value);
    }
  }

  private void strings(String name, List<String> values) throws IOException {
    if (values.isEmpty()) {
      return;
    }
    g.writeArrayFieldStart(name);
    for (String v : values) {
      g.writeString(v);
    }
    g.writeEndArray();
  }

  private void integers(String name, List<Integer> values) throws IOException {
    g.writeArrayFieldStart(name);
    for (Integer v : values) {
      g.writeNumber(v);
    }
    g.writeEndArray();
  }

  private void decimals(String name, List<BigDecimal> values) throws IOException {
    g.writeArrayFieldStart(name);
    for (BigDecimal v : values) {
      g.writeNumber(v);
    }
    g.writeEndArray();
  }
}
