package ch.emed.converter.chmed23a.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * A medicament (README "Medicament").
 *
 * @param id the id of {@code idType}, or free text for type 1
 * @param idType 1 none, 2 GTIN, 3 Pharmacode, 4 product number (not for prescriptions)
 * @param pos posologies
 * @param rsn reason for the treatment
 * @param autoMed self-medication (plans)
 * @param prscbBy prescribed by, GLN or designation (plans)
 * @param reps repetition (prescriptions)
 * @param isNotSub must not be substituted
 * @param sic sic erat scriptum (prescriptions)
 * @param nbPack number of packages (prescriptions)
 * @param exts extensions
 */
public record Medicament(
    String id,
    Integer idType,
    List<Posology> pos,
    String rsn,
    Boolean autoMed,
    String prscbBy,
    Repetition reps,
    Boolean isNotSub,
    Boolean sic,
    BigDecimal nbPack,
    List<Extension> exts) {

  public static final int ID_NONE = 1;
  public static final int ID_GTIN = 2;
  public static final int ID_PHARMACODE = 3;
  public static final int ID_PRODUCT_NUMBER = 4;

  public Medicament {
    pos = pos == null ? List.of() : List.copyOf(pos);
    exts = exts == null ? List.of() : List.copyOf(exts);
  }
}
