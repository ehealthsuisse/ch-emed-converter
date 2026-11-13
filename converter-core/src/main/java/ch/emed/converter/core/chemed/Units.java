package ch.emed.converter.core.chemed;

import java.util.Locale;
import java.util.Map;

/**
 * eMediplan unit codes (INDEX CDTYP 9, used by CHMED16A and CHMED23A) → code of the CH Term
 * UnitCode value set {system, code, display}. Only unambiguous mappings are listed; extend after
 * domain review. Unmapped units lead to a free-text dosage (CH EMED requires coded dose units).
 * Moved from format-chmed16a (feature 006).
 */
public final class Units {

  private static final String UCUM = "http://unitsofmeasure.org";

  /** Keys are upper case; look up with {@link #code(String)}. */
  public static final Map<String, String[]> CDTYP9 =
      Map.ofEntries(
          Map.entry("STK", new String[] {Profiles.SNOMED, "767525000", "Unit"}),
          Map.entry("MG", new String[] {UCUM, "mg", "milligram"}),
          Map.entry("G", new String[] {UCUM, "g", "gram"}),
          Map.entry("UG", new String[] {UCUM, "ug", "microgram"}),
          Map.entry("MCG", new String[] {UCUM, "ug", "microgram"}),
          Map.entry("ML", new String[] {UCUM, "mL", "milliliter"}),
          Map.entry("L", new String[] {UCUM, "L", "liter"}),
          Map.entry("IE", new String[] {UCUM, "[iU]", "international unit"}),
          Map.entry("MMOL", new String[] {UCUM, "mmol", "millimole"}),
          Map.entry(
              "TR", new String[] {Profiles.SNOMED, "732994000", "Drop (unit of presentation)"}),
          Map.entry(
              "HUB",
              new String[] {Profiles.SNOMED, "732981002", "Actuation (unit of presentation)"}));

  private Units() {}

  /**
   * The CH Term code of an eMediplan unit, compared case-insensitively.
   *
   * @param unit eMediplan unit code, e.g. {@code Stk}
   * @return {system, code, display}, or null if the unit has no confirmed mapping
   */
  public static String[] code(String unit) {
    return unit == null || unit.isBlank() ? null : CDTYP9.get(unit.trim().toUpperCase(Locale.ROOT));
  }
}
