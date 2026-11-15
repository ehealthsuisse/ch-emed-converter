package ch.emed.converter.chmed23a.model;

import java.util.List;

/** An extension (README "Extension"). */
public record Extension(String nm, String val, String schema, List<Extension> exts) {

  public Extension {
    exts = exts == null ? List.of() : List.copyOf(exts);
  }
}
