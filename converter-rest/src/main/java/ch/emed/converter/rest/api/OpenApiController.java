package ch.emed.converter.rest.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/openapi.yaml}: the contract of the spec, packaged unchanged at build time
 * (research R8).
 */
@RestController
public class OpenApiController {

  private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

  private final byte[] contract;

  public OpenApiController() {
    try (InputStream in = getClass().getResourceAsStream("/static-api/openapi.yaml")) {
      if (in == null) {
        throw new IllegalStateException("static-api/openapi.yaml is missing from the classpath");
      }
      this.contract = in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @GetMapping("/api/v1/openapi.yaml")
  public ResponseEntity<byte[]> openApi() {
    return ResponseEntity.ok().contentType(YAML).body(contract);
  }
}
