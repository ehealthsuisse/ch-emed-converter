package ch.emed.converter.core.api;

import java.util.List;
import java.util.Objects;

/**
 * Input and output forms of a format (feature 003, capabilities), and the document types it
 * converts (feature 004, FR-014).
 *
 * @param format the format
 * @param inputForms names of the accepted input forms, e.g. {@code json}, {@code qr-image}
 * @param outputForms offered output forms
 * @param documentTypes names of the document types, e.g. {@code PMLC}; empty if the format does not
 *     name them
 */
public record FormatInfo(
    FormatId format,
    List<String> inputForms,
    List<OutputForm> outputForms,
    List<String> documentTypes) {

  public FormatInfo {
    Objects.requireNonNull(format, "format");
    inputForms = List.copyOf(inputForms);
    outputForms = List.copyOf(outputForms);
    documentTypes = List.copyOf(documentTypes);
  }

  /** Without document types (feature 003). */
  public FormatInfo(FormatId format, List<String> inputForms, List<OutputForm> outputForms) {
    this(format, inputForms, outputForms, List.of());
  }
}
