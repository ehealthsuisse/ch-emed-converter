package ch.emed.converter.chmed23a.model;

import java.util.List;

/** The patient (README "Patient"). */
public record Patient(
    String fName,
    String lName,
    String bdt,
    Integer gender,
    String street,
    String zip,
    String city,
    String country,
    String lng,
    List<PatientId> ids,
    List<Extension> exts,
    MedicalData mData,
    List<String> phones,
    List<String> emails) {

  public static final int MALE = 1;
  public static final int FEMALE = 2;
  public static final int OTHER = 3;

  public Patient {
    ids = ids == null ? List.of() : List.copyOf(ids);
    exts = exts == null ? List.of() : List.copyOf(exts);
    phones = phones == null ? List.of() : List.copyOf(phones);
    emails = emails == null ? List.of() : List.copyOf(emails);
  }
}
