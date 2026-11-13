package ch.emed.converter.chmed23a.model;

import java.math.BigDecimal;

/** Dosage objects (posology.md "Dosage objects"); the JSON property is {@code do}. */
public sealed interface DosageObject {

  int type();

  /** t = 1: an amount. */
  record Simple(BigDecimal a) implements DosageObject {
    @Override
    public int type() {
      return 1;
    }
  }

  /** t = 2: an amount changing from {@code aFrom} to {@code aTo} over a duration. */
  record FromTo(BigDecimal aFrom, BigDecimal aTo, Integer duU, Integer du) implements DosageObject {
    @Override
    public int type() {
      return 2;
    }
  }

  /** t = 3: an amount between {@code aMin} and {@code aMax}. */
  record Range(BigDecimal aMin, BigDecimal aMax) implements DosageObject {
    @Override
    public int type() {
      return 3;
    }
  }
}
