package ch.emed.converter.chmed16a.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** The JSON schema published by E-Rezept Schweiz (see {@code resources/schemas/README.md}). */
public final class ErezeptSchema {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Schema SCHEMA = load();

  private ErezeptSchema() {}

  private static Schema load() {
    try (InputStream in =
        ErezeptSchema.class.getResourceAsStream("/schemas/erezept-chmed16a-schemav7.json")) {
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Validation messages of a CHMED16A prescription (empty if it is schema-valid). */
  public static List<String> validate(byte[] json) {
    try {
      JsonNode node = MAPPER.readTree(json);
      return SCHEMA.validate(node).stream().map(m -> m.getMessage()).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
