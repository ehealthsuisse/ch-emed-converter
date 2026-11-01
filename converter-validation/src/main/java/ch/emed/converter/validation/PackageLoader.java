package ch.emed.converter.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Reads the vendored package manifest ({@code <dir>/manifest.json}, written by {@code
 * tools/fetch-packages.sh}) from the classpath and verifies every package checksum.
 */
public final class PackageLoader {

  /**
   * A vendored FHIR package.
   *
   * @param name package name
   * @param version package version
   * @param file file name inside the package directory
   * @param sha256 expected SHA-256 checksum
   * @param load whether the package is loaded into the validator
   */
  public record PackageRef(String name, String version, String file, String sha256, boolean load) {

    public String coordinates() {
      return name + "#" + version;
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PackageLoader() {}

  /**
   * Verifies the checksums of all packages in the manifest and returns the ones to load.
   *
   * @param directory classpath directory, e.g. {@code packages}
   * @return packages marked {@code "load": true}, in manifest order
   * @throws IllegalStateException if the manifest or a package is missing or a checksum mismatches
   */
  public static List<PackageRef> loadManifest(String directory) {
    JsonNode manifest = readManifest(directory);
    List<PackageRef> toLoad = new ArrayList<>();
    for (JsonNode node : manifest.get("packages")) {
      PackageRef ref =
          new PackageRef(
              node.get("name").asText(),
              node.get("version").asText(),
              node.get("file").asText(),
              node.get("sha256").asText(),
              node.get("load").asBoolean());
      verifyChecksum(directory, ref);
      if (ref.load()) {
        toLoad.add(ref);
      }
    }
    return List.copyOf(toLoad);
  }

  /**
   * Root package of the manifest, e.g. {@code ch.fhir.ig.ch-emed#6.0.0}.
   *
   * @param directory classpath directory
   * @return root package coordinates
   */
  public static String rootPackage(String directory) {
    return readManifest(directory).get("root").asText();
  }

  private static JsonNode readManifest(String directory) {
    String resource = "/" + directory + "/manifest.json";
    try (InputStream in = PackageLoader.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Package manifest not found: " + resource);
      }
      return MAPPER.readTree(in);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read package manifest " + resource, e);
    }
  }

  private static void verifyChecksum(String directory, PackageRef ref) {
    String resource = "/" + directory + "/" + ref.file();
    try (InputStream in = PackageLoader.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Vendored package not found: " + resource);
      }
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[64 * 1024];
      for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
        sha.update(buffer, 0, n);
      }
      String actual = HexFormat.of().formatHex(sha.digest());
      if (!actual.equalsIgnoreCase(ref.sha256())) {
        throw new IllegalStateException("Checksum mismatch for vendored package " + ref.file());
      }
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("Cannot verify vendored package " + ref.file(), e);
    }
  }
}
