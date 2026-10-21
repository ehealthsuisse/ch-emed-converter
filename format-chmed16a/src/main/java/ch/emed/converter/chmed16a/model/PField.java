package ch.emed.converter.chmed16a.model;

import java.util.List;

/** CHMED16A private field; ignored for the mapping and reported. */
public record PField(String nm, String val, List<PField> pFields) {

  public PField {
    pFields = pFields == null ? List.of() : List.copyOf(pFields);
  }
}
