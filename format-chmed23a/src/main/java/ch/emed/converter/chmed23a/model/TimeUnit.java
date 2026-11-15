package ch.emed.converter.chmed23a.model;

/** Time units of posology.md ("Time units") and of repetitions. */
public enum TimeUnit {
  SECOND(1),
  MINUTE(2),
  HOUR(3),
  DAY(4),
  WEEK(5),
  MONTH(6),
  YEAR(7);

  private final int code;

  TimeUnit(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  /**
   * The unit of a code.
   *
   * @param code 1-7
   * @return the unit, or null for another value
   */
  public static TimeUnit of(Integer code) {
    if (code == null) {
      return null;
    }
    for (TimeUnit unit : values()) {
      if (unit.code == code) {
        return unit;
      }
    }
    return null;
  }
}
