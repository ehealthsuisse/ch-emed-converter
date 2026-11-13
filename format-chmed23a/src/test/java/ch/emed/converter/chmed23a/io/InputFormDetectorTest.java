package ch.emed.converter.chmed23a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed23a.io.InputFormDetector.InputForm;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** T021: input form detection, rule M23-IN-01 (research R3). */
class InputFormDetectorTest {

  private static InputForm detect(String text) {
    return InputFormDetector.detect(text.getBytes(StandardCharsets.UTF_8));
  }

  private static InputForm detect(int... bytes) {
    byte[] input = new byte[bytes.length + 8];
    for (int i = 0; i < bytes.length; i++) {
      input[i] = (byte) bytes[i];
    }
    return InputFormDetector.detect(input);
  }

  @Test
  void imagesByTheirSignature() {
    assertThat(detect(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)).isEqualTo(InputForm.IMAGE);
    assertThat(detect(0xFF, 0xD8, 0xFF)).isEqualTo(InputForm.IMAGE);
    assertThat(detect('G', 'I', 'F', '8')).isEqualTo(InputForm.UNSUPPORTED_IMAGE);
    assertThat(detect('B', 'M')).isEqualTo(InputForm.UNSUPPORTED_IMAGE);
  }

  @Test
  void payloadsAndChunks() {
    assertThat(detect("CHMED23A.H4sI")).isEqualTo(InputForm.PAYLOAD);
    assertThat(detect("  chmed23a.H4sI\n")).isEqualTo(InputForm.PAYLOAD);
    assertThat(detect("CHMED23A.1/2.H4sI\nCHMED23A.2/2.AAAA")).isEqualTo(InputForm.PAYLOAD);
    assertThat(detect("﻿CHMED23A.H4sI")).isEqualTo(InputForm.PAYLOAD);
  }

  @Test
  void chmed16aIsRecognisedAsTheOtherVersion() {
    assertThat(detect("CHMED16A1H4sI")).isEqualTo(InputForm.CHMED16A);
    assertThat(detect("{\"Patient\":{\"FName\":\"A\"},\"Medicaments\":[]}"))
        .isEqualTo(InputForm.CHMED16A);
  }

  @Test
  void jsonForms() {
    assertThat(detect("{\"resourceType\":\"Bundle\"}")).isEqualTo(InputForm.FHIR_JSON);
    assertThat(detect("{\"patient\":{},\"medType\":1}")).isEqualTo(InputForm.JSON);
    assertThat(detect("{ not json")).isEqualTo(InputForm.JSON);
    assertThat(detect("hello")).isEqualTo(InputForm.UNKNOWN);
  }
}
