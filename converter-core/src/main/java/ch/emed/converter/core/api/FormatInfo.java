package ch.emed.converter.core.api;

import java.util.List;
import java.util.Objects;

/**
 * Input and output forms of a format (feature 003, capabilities).
 *
 * @param format the format
 * @param inputForms names of the accepted input forms, e.g. {@code json}, {@code qr-image}
 * @param outputForms offered output forms
 */
public record FormatInfo(FormatId format, List<String> inputForms, List<OutputForm> outputForms) {

  public FormatInfo {
    Objects.requireNonNull(format, "format");
    inputForms = List.copyOf(inputForms);
    outputForms = List.copyOf(outputForms);
  }
}
