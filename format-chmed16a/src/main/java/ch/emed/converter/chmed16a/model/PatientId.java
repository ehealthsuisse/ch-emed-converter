package ch.emed.converter.chmed16a.model;

/** CHMED16A PatientId; type 1 = insurance card number. */
public record PatientId(Integer type, String val) {

  public static final int TYPE_INSURANCE_CARD = 1;
}
