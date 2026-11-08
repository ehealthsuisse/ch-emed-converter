package ch.emed.converter.qr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Image helpers for the QR tests; in memory only. */
public final class Images {

  private Images() {}

  public static byte[] png(BufferedImage image) {
    return write(image, "png");
  }

  public static byte[] gif(BufferedImage image) {
    return write(image, "gif");
  }

  public static byte[] jpeg(BufferedImage image, float quality) {
    BufferedImage rgb = copy(image, BufferedImage.TYPE_INT_RGB);
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    ImageWriter writer = writers.next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
      writer.setOutput(out);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality);
      writer.write(null, new IIOImage(rgb, null, null), param);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }

  /** Two images side by side on a white background. */
  public static BufferedImage sideBySide(BufferedImage left, BufferedImage right) {
    int gap = 40;
    BufferedImage both =
        new BufferedImage(
            left.getWidth() + gap + right.getWidth(),
            Math.max(left.getHeight(), right.getHeight()),
            BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g = both.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, both.getWidth(), both.getHeight());
    g.drawImage(left, 0, 0, null);
    g.drawImage(right, left.getWidth() + gap, 0, null);
    g.dispose();
    return both;
  }

  /** A plain grey image without any code. */
  public static BufferedImage grey(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(0xB0, 0xB0, 0xB0));
    g.fillRect(0, 0, width, height);
    g.dispose();
    return image;
  }

  public static BufferedImage copy(BufferedImage image, int type) {
    BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), type);
    Graphics2D g = copy.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return copy;
  }

  private static byte[] write(BufferedImage image, String format) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      if (!ImageIO.write(image, format, new MemoryCacheImageOutputStream(bytes))) {
        throw new IllegalStateException("No writer for " + format);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }
}
