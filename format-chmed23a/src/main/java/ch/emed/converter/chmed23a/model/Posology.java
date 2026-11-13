package ch.emed.converter.chmed23a.model;

/**
 * A posology (README "Posology", posology.md "Posology").
 *
 * @param dtFrom start date or date-time
 * @param dtTo end date or date-time, inclusive
 * @param inRes reserve medication
 * @param po the posology detail
 * @param relMeal relation to a meal, 1-7
 * @param unit quantity unit (CDTYP 9)
 * @param appInstr application instructions
 * @param roa route of administration (CDTYP 61)
 * @param moa method of administration (CDTYP 62)
 */
public record Posology(
    String dtFrom,
    String dtTo,
    Boolean inRes,
    PosologyDetail po,
    Integer relMeal,
    String unit,
    String appInstr,
    String roa,
    String moa) {}
