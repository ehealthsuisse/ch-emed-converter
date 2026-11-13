package ch.emed.converter.chmed23a.model;

/**
 * A patient identifier (README "PatientId").
 *
 * @param type 1 insurance card number, 2 local PID
 * @param sId system identifier (OID, URL, ...), required for type 2
 * @param val the value
 */
public record PatientId(Integer type, String sId, String val) {

  public static final int INSURANCE_CARD = 1;
  public static final int LOCAL_PID = 2;
}
