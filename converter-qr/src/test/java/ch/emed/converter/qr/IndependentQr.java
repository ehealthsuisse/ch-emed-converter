package ch.emed.converter.qr;

import boofcv.abst.fiducial.QrCodePreciseDetector;
import boofcv.alg.fiducial.qrcode.QrCode;
import boofcv.alg.fiducial.qrcode.QrCodeEncoder;
import boofcv.alg.fiducial.qrcode.QrCodeGeneratorImage;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * QR encoder and reader independent of ZXing (BoofCV), so that tests do not check the codec with
 * itself (research R10, SC-002).
 */
public final class IndependentQr {

  private IndependentQr() {}

  /**
   * Renders a QR code holding exactly the given bytes in byte mode, without ECI.
   *
   * @param content content bytes
   * @param pixelsPerModule module size in pixels
   * @return a grayscale image with a 4-module quiet zone
   */
  public static BufferedImage image(byte[] content, int pixelsPerModule) {
    return image(
        new QrCodeEncoder().setError(QrCode.ErrorLevel.M).addBytes(content).fixate(),
        pixelsPerModule);
  }

  /**
   * Renders a prepared BoofCV QR code, e.g. one with mixed segment modes.
   *
   * @param qr the code
   * @param pixelsPerModule module size in pixels
   * @return a grayscale image with a 4-module quiet zone
   */
  public static BufferedImage image(QrCode qr, int pixelsPerModule) {
    QrCodeGeneratorImage generator = new QrCodeGeneratorImage(pixelsPerModule);
    generator.setBorderModule(4);
    generator.render(qr);
    return toImage(generator.getGray());
  }

  /**
   * Decodes every QR code BoofCV finds.
   *
   * @param image image
   * @return the messages of all codes found
   */
  public static List<String> decode(BufferedImage image) {
    QrCodePreciseDetector<GrayU8> detector = FactoryFiducial.qrcode(null, GrayU8.class);
    detector.process(toGray(image));
    return detector.getDetections().stream().map(qr -> qr.message).toList();
  }

  private static BufferedImage toImage(GrayU8 gray) {
    BufferedImage image = new BufferedImage(gray.width, gray.height, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < gray.height; y++) {
      for (int x = 0; x < gray.width; x++) {
        int v = gray.get(x, y);
        image.getRaster().setSample(x, y, 0, v);
      }
    }
    return image;
  }

  private static GrayU8 toGray(BufferedImage image) {
    GrayU8 gray = new GrayU8(image.getWidth(), image.getHeight());
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int rgb = image.getRGB(x, y);
        int luma =
            (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        gray.set(x, y, luma);
      }
    }
    return gray;
  }
}
