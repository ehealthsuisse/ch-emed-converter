package ch.emed.converter.core.report;

/** Conversion phase in which a report entry was produced; declaration order is report order. */
public enum Phase {
  PARSE,
  VALIDATE_INPUT,
  MAP,
  VALIDATE_OUTPUT
}
