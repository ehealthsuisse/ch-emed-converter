package ch.emed.converter.chmed16a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.InputFormDetector.InputForm;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T009 (feature 003): detection order of data-model.md §1. */
class InputFormDetectorTest {

  private static byte[] bytes(int... values) {
    byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) values[i];
    }
    return result;
  }

  private static byte[] text(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  static List<Arguments> cases() {
    return List.of(
        Arguments.of(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0), InputForm.IMAGE),
        Arguments.of(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0), InputForm.IMAGE),
        Arguments.of(text("GIF89a...."), InputForm.UNSUPPORTED_IMAGE),
        Arguments.of(text("BM6\0\0\0\0\0"), InputForm.UNSUPPORTED_IMAGE),
        Arguments.of(bytes(0x49, 0x49, 0x2A, 0x00, 8, 0), InputForm.UNSUPPORTED_IMAGE),
        Arguments.of(bytes(0x4D, 0x4D, 0x00, 0x2A, 0, 8), InputForm.UNSUPPORTED_IMAGE),
        Arguments.of(text("RIFF\u0010\0\0\0WEBPVP8 "), InputForm.UNSUPPORTED_IMAGE),
        Arguments.of(
            text("https://eprescription.hin.ch/#CHMED16A1H4sI&i=a"), InputForm.EREZEPT_URL),
        Arguments.of(text("  HTTPS://EPRESCRIPTION.HIN.CH/#CHMED16A1\n"), InputForm.EREZEPT_URL),
        Arguments.of(text("http://eprescription.hin.ch/#CHMED16A1"), InputForm.JSON),
        Arguments.of(text("https://example.org/#CHMED16A1"), InputForm.JSON),
        Arguments.of(text("https://eprescription.hin.ch.example.org/#CHMED16A1"), InputForm.JSON),
        Arguments.of(text("CHMED16A1H4sIAAAA"), InputForm.PAYLOAD),
        Arguments.of(text("\n chmed16a0{\"Id\":\"x\"}"), InputForm.PAYLOAD),
        Arguments.of(
            text("{\"resourceType\":\"Bundle\",\"type\":\"document\"}"), InputForm.FHIR_JSON),
        Arguments.of(
            text("{\"id\":\"x\",\"meta\":{},\"resourceType\":\"Bundle\"}"), InputForm.FHIR_JSON),
        Arguments.of(text("{\"Id\":\"x\",\"Patient\":{\"resourceType\":\"y\"}}"), InputForm.JSON),
        Arguments.of(text("{\"Id\":\"x\",\"MedType\":1}"), InputForm.JSON),
        Arguments.of(text("not json at all"), InputForm.JSON),
        Arguments.of(new byte[0], InputForm.JSON));
  }

  @ParameterizedTest
  @MethodSource("cases")
  void detectsForm(byte[] input, InputForm expected) {
    assertThat(InputFormDetector.detect(input)).isEqualTo(expected);
  }

  @Test
  void textOfDecodedQrContentIsDetectedToo() {
    assertThat(InputFormDetector.detectText("CHMED16A1H4sI")).isEqualTo(InputForm.PAYLOAD);
    assertThat(InputFormDetector.detectText("https://eprescription.hin.ch/#x"))
        .isEqualTo(InputForm.EREZEPT_URL);
    assertThat(InputFormDetector.detectText("https://example.org/")).isEqualTo(InputForm.JSON);
  }
}
