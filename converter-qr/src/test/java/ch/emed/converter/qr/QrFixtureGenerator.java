package ch.emed.converter.qr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Generates the committed image fixtures of {@code format-chmed16a/src/test/resources/fixtures/qr}
 * with BoofCV, independent of the converter's ZXing codec (T020). Runs only on request:
 *
 * <pre>
 * ./mvnw -pl converter-qr test -Dtest=QrFixtureGenerator -Dqr.fixtures=generate
 * </pre>
 */
@EnabledIfSystemProperty(named = "qr.fixtures", matches = "generate")
class QrFixtureGenerator {

  private static final Path FIXTURES = Path.of("../format-chmed16a/src/test/resources/fixtures");
  private static final int PIXELS_PER_MODULE = 4;

  @Test
  void generate() throws IOException {
    Path chmed = FIXTURES.resolve("chmed16a");
    Path out = Files.createDirectories(FIXTURES.resolve("qr"));

    String planBasic = Files.readString(chmed.resolve("plan-basic.qr.txt")).strip();
    String readme =
        Files.readString(chmed.resolve("official/readme-example-medication-plan.qr.txt")).strip();
    byte[] nonAscii = Files.readAllBytes(chmed.resolve("plan-non-ascii.json"));
    byte[] chmed23a = Files.readAllBytes(chmed.resolve("chmed23a-plan.json"));

    BufferedImage basic = image(planBasic.getBytes(StandardCharsets.US_ASCII));
    write(out.resolve("plan-basic.png"), Images.png(basic));
    write(out.resolve("plan-basic.jpg"), Images.jpeg(basic, 0.85f));
    write(out.resolve("readme-example.png"), Images.png(image(ascii(readme))));
    write(
        out.resolve("plan-non-ascii.png"),
        Images.png(image(ascii("CHMED16A1" + gzipBase64(nonAscii)))));
    write(
        out.resolve("plan-non-ascii-plain.png"),
        Images.png(image(concat("CHMED16A0".getBytes(StandardCharsets.US_ASCII), nonAscii))));
    write(
        out.resolve("chmed23a.png"), Images.png(image(ascii("CHMED23A1" + gzipBase64(chmed23a)))));
    write(out.resolve("two-codes.png"), Images.png(Images.sideBySide(basic, image(ascii(readme)))));
    write(out.resolve("no-code.png"), Images.png(Images.grey(320, 240)));
    write(out.resolve("url-code.png"), Images.png(image(ascii("https://example.org/"))));
    write(out.resolve("not-an-image.gif"), Images.gif(basic));

    // feature 003, US2: QR code of a synthetic signed eRezept URL
    String signedUrl = Files.readString(FIXTURES.resolve("erezept/rx-signed.url.txt")).strip();
    write(FIXTURES.resolve("erezept/rx-signed.png"), Images.png(image(ascii(signedUrl))));
  }

  private static BufferedImage image(byte[] content) {
    return IndependentQr.image(content, PIXELS_PER_MODULE);
  }

  private static byte[] ascii(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }

  private static String gzipBase64(byte[] data) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
      gzip.write(data);
    }
    return Base64.getEncoder().encodeToString(bytes.toByteArray());
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] all = new byte[a.length + b.length];
    System.arraycopy(a, 0, all, 0, a.length);
    System.arraycopy(b, 0, all, a.length, b.length);
    return all;
  }

  private static void write(Path file, byte[] bytes) throws IOException {
    Files.write(file, bytes);
  }
}
