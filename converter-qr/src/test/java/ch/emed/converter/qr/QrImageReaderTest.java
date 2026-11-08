package ch.emed.converter.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import boofcv.alg.fiducial.qrcode.QrCode;
import boofcv.alg.fiducial.qrcode.QrCodeEncoder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** T021 (feature 003): reading QR images (research R5, FR-001, FR-017). */
class QrImageReaderTest {

  /** Synthetic payload-like text: prefix plus Base64 of fixed pseudo-random bytes. */
  static final String PAYLOAD = payload(900);

  static final String PLAIN_NON_ASCII =
      "CHMED16A0{\"Id\":\"x\",\"Rmk\":\"Grüezi Zürich, déjà vu, Eß-Löffel\"}";

  static String payload(int bytes) {
    byte[] data = new byte[bytes];
    new Random(42).nextBytes(data);
    return "CHMED16A1H4sI" + java.util.Base64.getEncoder().encodeToString(data);
  }

  private static byte[] read(byte[] image) throws QrException {
    return QrImageReader.read(image, QrImageReader.DEFAULT_MAX_PIXELS);
  }

  @Test
  void readsPngAndJpeg() throws Exception {
    BufferedImage image = IndependentQr.image(PAYLOAD.getBytes(StandardCharsets.US_ASCII), 6);

    assertThat(read(Images.png(image))).isEqualTo(PAYLOAD.getBytes(StandardCharsets.US_ASCII));
    assertThat(read(Images.jpeg(image, 0.85f)))
        .isEqualTo(PAYLOAD.getBytes(StandardCharsets.US_ASCII));
  }

  @Test
  void returnsRawUtf8BytesWithoutEci() throws Exception {
    byte[] content = PLAIN_NON_ASCII.getBytes(StandardCharsets.UTF_8);

    assertThat(read(Images.png(IndependentQr.image(content, 6)))).isEqualTo(content);
  }

  @Test
  void returnsUtf8BytesWithEci() throws Exception {
    BitMatrix matrix =
        new QRCodeWriter()
            .encode(
                PLAIN_NON_ASCII,
                BarcodeFormat.QR_CODE,
                0,
                0,
                Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 4));

    assertThat(read(Images.png(toImage(matrix, 6))))
        .isEqualTo(PLAIN_NON_ASCII.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void returnsAllSegmentsOfMixedModeCodes() throws Exception {
    String rest = PAYLOAD.substring("CHMED16A1".length());
    QrCode qr =
        new QrCodeEncoder()
            .setError(QrCode.ErrorLevel.M)
            .addAlphanumeric("CHMED16A1")
            .addBytes(rest.getBytes(StandardCharsets.US_ASCII))
            .fixate();
    BufferedImage image = IndependentQr.image(qr, 6);

    assertThat(new String(read(Images.png(image)), StandardCharsets.US_ASCII)).isEqualTo(PAYLOAD);
  }

  @Test
  void noCodeIsNotFound() {
    assertThatThrownBy(() -> read(Images.png(Images.grey(400, 300))))
        .isInstanceOfSatisfying(
            QrException.class, e -> assertThat(e.kind()).isEqualTo(QrException.Kind.NOT_FOUND));
  }

  @Test
  void twoCodesAreRefusedWithoutChoosing() {
    BufferedImage a = IndependentQr.image("CHMED16A1AAAA".getBytes(StandardCharsets.US_ASCII), 6);
    BufferedImage b = IndependentQr.image("CHMED16A1BBBB".getBytes(StandardCharsets.US_ASCII), 6);

    assertThatThrownBy(() -> read(Images.png(Images.sideBySide(a, b))))
        .isInstanceOfSatisfying(
            QrException.class, e -> assertThat(e.kind()).isEqualTo(QrException.Kind.MULTIPLE));
  }

  @Test
  void gifAndTruncatedPngAreUnreadable() {
    BufferedImage image = IndependentQr.image(PAYLOAD.getBytes(StandardCharsets.US_ASCII), 4);
    byte[] png = Images.png(image);

    for (byte[] bad : new byte[][] {Images.gif(image), Arrays.copyOf(png, 60), new byte[] {1, 2}}) {
      assertThatThrownBy(() -> read(bad))
          .isInstanceOfSatisfying(
              QrException.class, e -> assertThat(e.kind()).isEqualTo(QrException.Kind.UNREADABLE));
    }
  }

  @Test
  void pixelLimitIsCheckedBeforeDecoding() {
    byte[] huge = Images.png(new BufferedImage(8000, 6000, BufferedImage.TYPE_BYTE_BINARY));

    assertThatThrownBy(() -> read(huge))
        .isInstanceOfSatisfying(
            QrException.class,
            e -> {
              assertThat(e.kind()).isEqualTo(QrException.Kind.TOO_LARGE);
              assertThat(e.getMessage()).contains("40000000");
            });
  }

  @Test
  void writesNoTemporaryFiles() throws Exception {
    Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
    Set<Path> before = list(tmp);
    byte[] png = Images.png(IndependentQr.image(PAYLOAD.getBytes(StandardCharsets.US_ASCII), 6));

    read(png);
    read(Images.jpeg(IndependentQr.image(PAYLOAD.getBytes(StandardCharsets.US_ASCII), 6), 0.9f));

    assertThat(list(tmp)).as("new files in java.io.tmpdir").isSubsetOf(before);
  }

  @Test
  void messagesNeverContainContent() {
    BufferedImage a = IndependentQr.image(PAYLOAD.getBytes(StandardCharsets.US_ASCII), 6);

    assertThatThrownBy(() -> read(Images.png(Images.sideBySide(a, a))))
        .satisfies(e -> assertThat(e.getMessage()).doesNotContain("CHMED").doesNotContain("H4sI"));
  }

  private static Set<Path> list(Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(p -> p.getFileName().toString().startsWith("imageio"))
          .collect(Collectors.toSet());
    }
  }

  private static BufferedImage toImage(BitMatrix matrix, int scale) {
    BufferedImage image =
        new BufferedImage(
            matrix.getWidth() * scale, matrix.getHeight() * scale, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        image.getRaster().setSample(x, y, 0, matrix.get(x / scale, y / scale) ? 0 : 255);
      }
    }
    return image;
  }
}
