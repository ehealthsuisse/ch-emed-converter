package ch.emed.converter.chmed16a.model;

import java.util.List;

/** CHMED16A Medicament. Prescription-only fields: Rep, Subs, NbPack, IndC. */
public record Medicament(
    String id,
    Integer idType,
    List<Posology> pos,
    String unit,
    String tkgRsn,
    String appInstr,
    Integer autoMed,
    String prscbBy,
    String roa,
    Integer rep,
    Integer subs,
    Long nbPack,
    String indC,
    List<PField> pFields) {

  public static final int ID_TYPE_NONE = 1;
  public static final int ID_TYPE_GTIN = 2;
  public static final int ID_TYPE_PHARMACODE = 3;
  public static final int ID_TYPE_PRODUCT_NUMBER = 4;

  public Medicament {
    pos = pos == null ? List.of() : List.copyOf(pos);
    pFields = pFields == null ? List.of() : List.copyOf(pFields);
  }
}
