package ch.emed.converter.qr;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * T022 (feature 003): degraded images, standing in for scans and phone photos. Reading photos is
 * best effort (spec assumption), but these four variants must be read.
 */
class QrImageReaderDegradedTest {

  @Test
  void readsEveryRequiredVariant() throws Exception {
    byte[] content = QrImageReaderTest.PAYLOAD.getBytes(StandardCharsets.US_ASCII);
    BufferedImage clean = IndependentQr.image(content, 8);

    for (Map.Entry<String, byte[]> variant : DegradedImages.variants(clean).entrySet()) {
      assertThat(QrImageReader.read(variant.getValue(), QrImageReader.DEFAULT_MAX_PIXELS))
          .as(variant.getKey())
          .isEqualTo(content);
    }
  }
}
