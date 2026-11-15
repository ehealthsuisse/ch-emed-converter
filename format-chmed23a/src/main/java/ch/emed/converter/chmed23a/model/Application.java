package ch.emed.converter.chmed23a.model;

/** Application objects (posology.md "Application objects"); they carry no type. */
public final class Application {

  private Application() {}

  /** A dosage at a time of day ({@code hh:mm:ss} or {@code hh:mm}). */
  public record AtTime(String dt, DosageObject dose) {}

  /** A dosage in a day segment (1 morning, 2 noon, 3 evening, 4 night). */
  public record InSegment(Integer s, DosageObject dose) {}
}
