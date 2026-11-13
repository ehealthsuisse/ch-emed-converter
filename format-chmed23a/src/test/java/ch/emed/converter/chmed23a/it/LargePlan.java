package ch.emed.converter.chmed23a.it;

import java.nio.charset.StandardCharsets;

/** A synthetic CHMED23A medication plan with many medicaments (QR capacity, performance). */
final class LargePlan {

  private LargePlan() {}

  /** Text that GZip cannot compress much (hex of a hash), so the payload grows with the plan. */
  private static String noise(int seed) {
    try {
      byte[] hash =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(("seed" + seed).getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(hash);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static byte[] json(int medicaments) {
    StringBuilder meds = new StringBuilder();
    for (int i = 0; i < medicaments; i++) {
      if (i > 0) {
        meds.append(',');
      }
      meds.append("{\"id\":\"Synthetic medicament ")
          .append(i)
          .append(" ")
          .append(noise(i))
          .append("\",\"idType\":1,\"autoMed\":false,\"rsn\":\"Reason ")
          .append(noise(i + 1000))
          .append(" for a long treatment\",\"pos\":[{\"po\":{\"t\":1,\"ds\":[1,0,")
          .append(i % 3)
          .append(",0]},\"unit\":\"Stk\",\"appInstr\":\"Instruction number ")
          .append(i)
          .append(" for the patient\"}]}");
    }
    String json =
        "{\"patient\":{\"fName\":\"Test\",\"lName\":\"Synthetic\",\"bdt\":\"1950-01-01\","
            + "\"gender\":3,\"lng\":\"de\",\"ids\":[{\"type\":1,\"val\":\"80756000000000000000\"}]},"
            + "\"meds\":["
            + meds
            + "],\"medType\":1,\"auth\":2,\"dt\":\"2024-01-09T09:14:36+01:00\"}";
    return json.getBytes(StandardCharsets.UTF_8);
  }
}
