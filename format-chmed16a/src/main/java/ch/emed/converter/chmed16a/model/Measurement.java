package ch.emed.converter.chmed16a.model;

/**
 * CHMED16A Measurement.
 *
 * @param type 1 weight, 2 height
 * @param val value as given (string)
 * @param unit 1 centimetre (height), 2 kilogram (weight)
 */
public record Measurement(Integer type, String val, Integer unit) {

  public static final int TYPE_WEIGHT = 1;
  public static final int TYPE_HEIGHT = 2;
  public static final int UNIT_CM = 1;
  public static final int UNIT_KG = 2;
}
