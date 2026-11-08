package ch.emed.converter.qr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Degraded variants of a clean QR image, standing in for scans and photos (T022, best effort). All
 * variants are deterministic: fixed angles, factors and seeds.
 */
public final class DegradedImages {

  private DegradedImages() {}

  /**
   * The four required variants of an image.
   *
   * @param clean clean QR image
   * @return image bytes by variant name (PNG, except the JPEG variant)
   */
  public static Map<String, byte[]> variants(BufferedImage clean) {
    Map<String, byte[]> variants = new LinkedHashMap<>();
    variants.put("rotated-8deg", Images.png(rotate(clean, 8)));
    variants.put("scaled-60pct", Images.png(scale(clean, 0.6)));
    variants.put("jpeg-q60", Images.jpeg(clean, 0.6f));
    variants.put("noise-sigma8", Images.png(noise(clean, 8, 20261108L)));
    return variants;
  }

  static BufferedImage rotate(BufferedImage image, double degrees) {
    int size = (int) Math.ceil(Math.hypot(image.getWidth(), image.getHeight()));
    BufferedImage out = white(size, size);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    AffineTransform t = new AffineTransform();
    t.translate(size / 2.0, size / 2.0);
    t.rotate(Math.toRadians(degrees));
    t.translate(-image.getWidth() / 2.0, -image.getHeight() / 2.0);
    g.drawImage(image, t, null);
    g.dispose();
    return out;
  }

  static BufferedImage scale(BufferedImage image, double factor) {
    int width = (int) Math.round(image.getWidth() * factor);
    int height = (int) Math.round(image.getHeight() * factor);
    BufferedImage out = white(width, height);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(image, 0, 0, width, height, null);
    g.dispose();
    return out;
  }

  static BufferedImage noise(BufferedImage image, double sigma, long seed) {
    Random random = new Random(seed);
    BufferedImage out =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int v = image.getRGB(x, y) & 0xFF;
        int n = (int) Math.round(v + random.nextGaussian() * sigma);
        out.getRaster().setSample(x, y, 0, Math.max(0, Math.min(255, n)));
      }
    }
    return out;
  }

  private static BufferedImage white(int width, int height) {
    BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g = out.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);
    g.dispose();
    return out;
  }
}
