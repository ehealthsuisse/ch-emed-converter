package ch.emed.converter.chmed16a.model;

import java.util.List;

/** CHMED16A Patient. */
public record Patient(
    String fName,
    String lName,
    String bDt,
    Integer gender,
    String street,
    String zip,
    String city,
    String lng,
    String phone,
    String email,
    String rcv,
    List<PatientId> ids,
    MedicalData med,
    List<PField> pFields) {

  public static final int GENDER_MALE = 1;
  public static final int GENDER_FEMALE = 2;

  public Patient {
    ids = ids == null ? List.of() : List.copyOf(ids);
    pFields = pFields == null ? List.of() : List.copyOf(pFields);
  }

  /** Copy with another receiver (GLN of the intended receiver of a prescription). */
  public Patient withRcv(String receiver) {
    return new Patient(
        fName, lName, bDt, gender, street, zip, city, lng, phone, email, receiver, ids, med,
        pFields);
  }
}
