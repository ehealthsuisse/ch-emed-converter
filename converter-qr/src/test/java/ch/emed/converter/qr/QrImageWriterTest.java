package ch.emed.converter.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** T036 (feature 003, US3): QR images (research R3, data-model.md §2, FR-007, FR-008, SC-002). */
class QrImageWriterTest {

  private static final String PAYLOAD = QrImageReaderTest.PAYLOAD;

  @Test
  void writesA1BitGrayscalePngWithoutTextOrTimeChunks() throws Exception {
    byte[] png = QrImageWriter.write(PAYLOAD);

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(image.getColorModel().getPixelSize()).isEqualTo(1);
    String chunks = chunkTypes(png).toString();
    assertThat(chunks).doesNotContain("tEXt").doesNotContain("iTXt").doesNotContain("tIME");
    assertThat(chunks).contains("pHYs");
  }

  @Test
  void usesLevelMSmallestVersionQuietZone4And8PixelsPerModule() throws Exception {
    byte[] png = QrImageWriter.write(PAYLOAD);
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    int expectedVersion =
        Encoder.encode(PAYLOAD, ErrorCorrectionLevel.M).getVersion().getVersionNumber();
    int modules = 17 + 4 * expectedVersion;

    assertThat(image.getWidth()).isEqualTo(image.getHeight()).isEqualTo((modules + 8) * 8);
    // quiet zone: the outer 4 modules are white
    for (int i = 0; i < image.getWidth(); i++) {
      assertThat(image.getRGB(i, 31) & 0xFFFFFF).isEqualTo(0xFFFFFF);
      assertThat(image.getRGB(31, i) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }
    // finder pattern starts right after the quiet zone
    assertThat(image.getRGB(32, 32) & 0xFFFFFF).isZero();
    assertThat(QrImageReader.read(png, QrImageReader.DEFAULT_MAX_PIXELS))
        .isEqualTo(PAYLOAD.getBytes(StandardCharsets.US_ASCII));
  }

  @Test
  void physicalResolutionGivesA40mmSymbol() throws Exception {
    byte[] png = QrImageWriter.write(PAYLOAD);
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    int symbolPixels = image.getWidth() - 2 * 4 * 8;

    double pixelsPerMeter = pixelsPerMeter(png);

    assertThat(symbolPixels / pixelsPerMeter * 1000)
        .isCloseTo(40.0, org.assertj.core.data.Offset.offset(0.1));
  }

  @Test
  void isDeterministic() throws Exception {
    assertThat(QrImageWriter.write(PAYLOAD)).isEqualTo(QrImageWriter.write(PAYLOAD));
  }

  @Test
  void refusesTextAboveTheCapacityOfOneCode() {
    String tooLong = "CHMED16A1" + "a".repeat(QrImageWriter.MAX_BYTES);

    assertThatThrownBy(() -> QrImageWriter.write(tooLong))
        .isInstanceOfSatisfying(
            QrException.class,
            e -> {
              assertThat(e.kind()).isEqualTo(QrException.Kind.CAPACITY);
              assertThat(e.getMessage()).contains("2331").doesNotContain("CHMED");
            });
  }

  @Test
  void largestTextThatFitsIsWritten() throws Exception {
    String largest = "CHMED16A1" + "a".repeat(QrImageWriter.MAX_BYTES - 9);

    byte[] png = QrImageWriter.write(largest);

    assertThat(QrImageReader.read(png, QrImageReader.DEFAULT_MAX_PIXELS))
        .isEqualTo(largest.getBytes(StandardCharsets.US_ASCII));
  }

  @Test
  void anIndependentReaderDecodesEveryCorpusPayloadExactly() throws Exception {
    // SC-002: BoofCV, not ZXing, reads the images of the CHMED16A corpus payloads
    List<Path> payloads;
    try (Stream<Path> files =
        Files.walk(Path.of("../format-chmed16a/src/test/resources/fixtures/chmed16a"))) {
      payloads =
          files.filter(f -> f.getFileName().toString().endsWith(".qr.txt")).sorted().toList();
    }
    assertThat(payloads).isNotEmpty();
    List<String> texts = new ArrayList<>();
    for (Path file : payloads) {
      texts.add(Files.readString(file, StandardCharsets.US_ASCII).strip());
    }
    texts.add(PAYLOAD);

    for (String text : texts) {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(QrImageWriter.write(text)));
      assertThat(IndependentQr.decode(image)).containsExactly(text);
    }
  }

  private static double pixelsPerMeter(byte[] png) throws Exception {
    try (MemoryCacheImageInputStream in =
        new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      ImageReader reader = readers.next();
      reader.setInput(in);
      IIOMetadata metadata = reader.getImageMetadata(0);
      Node root = metadata.getAsTree("javax_imageio_png_1.0");
      for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child.getNodeName().equals("pHYs")) {
          Element phys = (Element) child;
          assertThat(phys.getAttribute("unitSpecifier")).isEqualTo("meter");
          return Double.parseDouble(phys.getAttribute("pixelsPerUnitXAxis"));
        }
      }
      throw new AssertionError("no pHYs chunk");
    }
  }

  private static List<String> chunkTypes(byte[] png) {
    List<String> types = new ArrayList<>();
    int offset = 8;
    while (offset + 8 <= png.length) {
      int length =
          ((png[offset] & 0xFF) << 24)
              | ((png[offset + 1] & 0xFF) << 16)
              | ((png[offset + 2] & 0xFF) << 8)
              | (png[offset + 3] & 0xFF);
      types.add(new String(png, offset + 4, 4, StandardCharsets.US_ASCII));
      offset += 12 + length;
    }
    return types;
  }
}
