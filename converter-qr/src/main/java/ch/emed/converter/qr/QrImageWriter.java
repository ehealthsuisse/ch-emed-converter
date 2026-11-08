package ch.emed.converter.qr;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Writes a QR code as PNG (feature 003, research R3, data-model.md §2): byte mode without ECI,
 * error correction M, smallest version, quiet zone of 4 modules, 8 px per module, 1-bit grayscale,
 * and a {@code pHYs} resolution at which the symbol (without quiet zone) prints 40 mm wide.
 * Deterministic: equal text gives equal bytes. Stateless, thread-safe, in memory only.
 */
public final class QrImageWriter {

  /** Byte-mode capacity of version 40 at level M. */
  public static final int MAX_BYTES = 2331;

  static final int QUIET_ZONE_MODULES = 4;
  static final int PIXELS_PER_MODULE = 8;
  static final double SYMBOL_WIDTH_METERS = 0.040;

  private QrImageWriter() {}

  /**
   * Writes a QR code image.
   *
   * @param text ASCII text, e.g. a CHMED payload
   * @return PNG bytes
   * @throws QrException of kind {@code CAPACITY} if the text does not fit into one QR code
   */
  public static byte[] write(String text) throws QrException {
    if (text.length() > MAX_BYTES) {
      throw capacity();
    }
    QRCode code;
    try {
      // no charset hint: with one, ZXing adds an ECI segment; without, it uses ISO-8859-1 plainly
      code = Encoder.encode(text, ErrorCorrectionLevel.M);
    } catch (WriterException e) {
      throw capacity();
    }
    ByteMatrix matrix = code.getMatrix();
    int modules = matrix.getWidth();
    int size = (modules + 2 * QUIET_ZONE_MODULES) * PIXELS_PER_MODULE;
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY);
    WritableRaster raster = image.getRaster();
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        int mx = x / PIXELS_PER_MODULE - QUIET_ZONE_MODULES;
        int my = y / PIXELS_PER_MODULE - QUIET_ZONE_MODULES;
        boolean dark =
            mx >= 0 && my >= 0 && mx < modules && my < modules && matrix.get(mx, my) == 1;
        raster.setSample(x, y, 0, dark ? 0 : 1);
      }
    }
    long pixelsPerMeter = Math.round(modules * PIXELS_PER_MODULE / SYMBOL_WIDTH_METERS);
    return png(image, pixelsPerMeter);
  }

  private static QrException capacity() {
    return new QrException(
        QrException.Kind.CAPACITY,
        "Text exceeds the capacity of one QR code (" + MAX_BYTES + " bytes at level M)");
  }

  private static byte[] png(BufferedImage image, long pixelsPerMeter) {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      IIOMetadata metadata =
          writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
      IIOMetadataNode phys = new IIOMetadataNode("pHYs");
      phys.setAttribute("pixelsPerUnitXAxis", Long.toString(pixelsPerMeter));
      phys.setAttribute("pixelsPerUnitYAxis", Long.toString(pixelsPerMeter));
      phys.setAttribute("unitSpecifier", "meter");
      IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
      root.appendChild(phys);
      metadata.mergeTree("javax_imageio_png_1.0", root);
      writer.setOutput(out);
      writer.write(null, new IIOImage(image, null, metadata), param);
    } catch (IIOInvalidTreeException e) {
      throw new IllegalStateException("Cannot set the PNG resolution", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }
}
