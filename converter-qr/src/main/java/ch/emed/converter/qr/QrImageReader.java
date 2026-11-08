package ch.emed.converter.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Reads the content of exactly one QR code from a PNG or JPEG image (feature 003, research R5).
 * Stateless and thread-safe; works in memory only (no ImageIO disk cache) and offline.
 */
public final class QrImageReader {

  /** Default pixel limit: 40 megapixels. */
  public static final long DEFAULT_MAX_PIXELS = 40_000_000L;

  private static final Map<DecodeHintType, Object> HINTS = hints();

  private QrImageReader() {}

  /**
   * Reads the QR code of an image.
   *
   * @param image PNG or JPEG bytes
   * @param maxPixels maximum width × height, checked before the pixels are decoded
   * @return the raw content bytes of the code (its byte segments); the caller decides the charset
   * @throws QrException if the image cannot be read or does not hold exactly one QR code
   */
  public static byte[] read(byte[] image, long maxPixels) throws QrException {
    BufferedImage picture = decodeImage(image, maxPixels);
    int width = picture.getWidth();
    int height = picture.getHeight();
    int[] argb = picture.getRGB(0, 0, width, height, null, 0, width);
    LuminanceSource source = new RGBLuminanceSource(width, height, argb);

    List<Function<LuminanceSource, BinaryBitmap>> binarizers =
        List.of(
            s -> new BinaryBitmap(new HybridBinarizer(s)),
            s -> new BinaryBitmap(new GlobalHistogramBinarizer(s)));
    for (Function<LuminanceSource, BinaryBitmap> binarizer : binarizers) {
      Result[] results = detectAll(binarizer.apply(source));
      if (results.length > 1) {
        throw new QrException(
            QrException.Kind.MULTIPLE, "Image holds more than one QR code; exactly one expected");
      }
      Result result = results.length == 1 ? results[0] : decodeSingle(binarizer.apply(source));
      if (result != null) {
        return content(result);
      }
    }
    throw new QrException(QrException.Kind.NOT_FOUND, "No readable QR code found in the image");
  }

  private static BufferedImage decodeImage(byte[] image, long maxPixels) throws QrException {
    try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(image))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext() || !isPngOrJpeg(image)) {
        throw new QrException(QrException.Kind.UNREADABLE, "Unsupported image format");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels > maxPixels) {
          throw new QrException(
              QrException.Kind.TOO_LARGE,
              "Image exceeds the pixel limit of " + maxPixels + " pixels");
        }
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      throw new QrException(QrException.Kind.UNREADABLE, "Image cannot be decoded");
    }
  }

  private static boolean isPngOrJpeg(byte[] image) {
    return image.length > 3
        && ((image[0] & 0xFF) == 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G'
            || (image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8 && (image[2] & 0xFF) == 0xFF);
  }

  private static Result[] detectAll(BinaryBitmap bitmap) {
    try {
      return new QRCodeMultiReader().decodeMultiple(bitmap, HINTS);
    } catch (NotFoundException e) {
      return new Result[0];
    }
  }

  private static Result decodeSingle(BinaryBitmap bitmap) {
    try {
      return new QRCodeReader().decode(bitmap, HINTS);
    } catch (NotFoundException | ChecksumException | FormatException e) {
      return null;
    }
  }

  /**
   * The content bytes as encoded (research R5). Without an ECI designator, ZXing decodes byte
   * segments with the ISO-8859-1 hint, which maps every byte to one char, so the chars give back
   * the raw bytes of all segments (numeric and alphanumeric segments are ASCII). With an ECI, the
   * text is already decoded with the declared charset and is returned as UTF-8.
   */
  private static byte[] content(Result result) {
    String text = result.getText();
    boolean eci = hasEci(result) || text.chars().anyMatch(c -> c > 0xFF);
    return text.getBytes(eci ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
  }

  /** Symbology identifier {@code ]Q2}, {@code ]Q4} or {@code ]Q6}: an ECI is present. */
  private static boolean hasEci(Result result) {
    Map<ResultMetadataType, Object> metadata = result.getResultMetadata();
    Object id = metadata == null ? null : metadata.get(ResultMetadataType.SYMBOLOGY_IDENTIFIER);
    return id instanceof String s && s.length() == 3 && (s.charAt(2) - '0') % 2 == 0;
  }

  private static Map<DecodeHintType, Object> hints() {
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
    hints.put(DecodeHintType.CHARACTER_SET, StandardCharsets.ISO_8859_1.name());
    return Map.copyOf(hints);
  }
}
