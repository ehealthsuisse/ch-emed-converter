package ch.emed.converter.chmed16a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** T035 (feature 003, US3): the CHMED16A QR payload (research R2, FR-006, FR-011, FR-012). */
class Chmed16aPayloadEncoderTest {

  private static final byte[] JSON = Fixtures.bytes("chmed16a/plan-non-ascii.json");

  @Test
  void startsWithPrefixAndGzipMagic() {
    String payload = new String(Chmed16aPayloadEncoder.encode(JSON), StandardCharsets.US_ASCII);

    assertThat(payload).startsWith("CHMED16A1H4sI");
  }

  @Test
  void decodesToExactlyTheJsonBytes() throws Exception {
    String payload = new String(Chmed16aPayloadEncoder.encode(JSON), StandardCharsets.US_ASCII);

    assertThat(Chmed16aPayloadDecoder.decode(payload, Long.MAX_VALUE)).isEqualTo(JSON);
  }

  @Test
  void gzipHeaderHasNoTimestamp() {
    String payload = new String(Chmed16aPayloadEncoder.encode(JSON), StandardCharsets.US_ASCII);
    byte[] gzip = Base64.getDecoder().decode(payload.substring("CHMED16A1".length()));

    assertThat(gzip[0] & 0xFF).isEqualTo(0x1F);
    assertThat(gzip[1] & 0xFF).isEqualTo(0x8B);
    assertThat(new byte[] {gzip[4], gzip[5], gzip[6], gzip[7]}).containsOnly(0);
  }

  @Test
  void isDeterministic() {
    assertThat(Chmed16aPayloadEncoder.encode(JSON)).isEqualTo(Chmed16aPayloadEncoder.encode(JSON));
  }

  @Test
  void isStandardBase64AsciiWithoutLineBreaks() {
    byte[] payload = Chmed16aPayloadEncoder.encode(JSON);
    String text = new String(payload, StandardCharsets.US_ASCII);

    for (byte b : payload) {
      assertThat(b & 0xFF).isLessThan(0x80);
    }
    assertThat(text)
        .doesNotContain("\n")
        .doesNotContain("\r")
        .doesNotContain("-")
        .doesNotContain("_");
    assertThat(text.substring("CHMED16A1".length()).length() % 4).isZero();
  }
}
