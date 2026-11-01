package ch.emed.converter.chmed16a.testsupport;

import java.nio.charset.StandardCharsets;

/** Generates large synthetic CHMED16A medication plans (fictitious patient, valid GTINs). */
public final class LargePlanGenerator {

  private LargePlanGenerator() {}

  /**
   * A medication plan with {@code medicaments} medicaments: GTIN-coded with morning/evening doses,
   * every third one with a posology end date and every fifth one self-medicated.
   *
   * @param medicaments number of medicaments
   * @return UTF-8 JSON
   */
  public static byte[] plan(int medicaments) {
    StringBuilder json =
        new StringBuilder(
            "{\"Id\":\"11111111-2222-4333-8444-555555555555\",\"MedType\":1,"
                + "\"Auth\":\"7601000000001\",\"Dt\":\"2026-10-01T09:30:00+02:00\","
                + "\"Patient\":{\"FName\":\"Hans\",\"LName\":\"Muster\",\"BDt\":\"1950-03-21\","
                + "\"Gender\":1,\"Lng\":\"de\",\"Ids\":[{\"Type\":1,\"Val\":\"80756000000000000001\"}]},"
                + "\"Medicaments\":[");
    for (int i = 0; i < medicaments; i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append("{\"Id\":\"")
          .append(gtin(768_000_000_000L + i * 17L))
          .append("\",\"IdType\":2,\"Pos\":[{\"DtFrom\":\"2026-10-01\"")
          .append(i % 3 == 0 ? ",\"DtTo\":\"2026-12-31\"" : "")
          .append(",\"D\":[1,0,")
          .append(i % 2 == 0 ? "0.5" : "1")
          .append(",0]}],\"Unit\":\"STK\",\"TkgRsn\":\"Indikation ")
          .append(i)
          .append("\",\"AppInstr\":\"Nach dem Essen\",\"AutoMed\":")
          .append(i % 5 == 0 ? 1 : 0)
          .append(",\"Roa\":\"PO\"}");
    }
    json.append("]}");
    return json.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** The 12-digit prefix plus the GTIN check digit. */
  private static String gtin(long prefix12) {
    String digits = String.format("%012d", prefix12);
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      int digit = digits.charAt(i) - '0';
      sum += (i % 2 == 0) ? digit : digit * 3;
    }
    return digits + ((10 - (sum % 10)) % 10);
  }
}
