package ch.emed.converter.core.report;

import java.util.Objects;

/**
 * One entry of the conversion report (data-model.md §1). {@code message} MUST NOT contain values of
 * patient fields (FR-017).
 *
 * @param severity severity
 * @param code reason code
 * @param phase phase
 * @param path source path (JSON Pointer for CHMED, FHIRPath for CH EMED)
 * @param targetPath optional target path, may be null
 * @param message human-readable message without patient values
 * @param clinicalLoss true if clinically relevant information is not represented exactly
 */
public record ReportEntry(
    Severity severity,
    String code,
    Phase phase,
    String path,
    String targetPath,
    String message,
    boolean clinicalLoss) {

  public ReportEntry {
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(message, "message");
  }

  public static ReportEntry of(
      Severity severity, String code, Phase phase, String path, String message) {
    return new ReportEntry(severity, code, phase, path, null, message, false);
  }

  public static ReportEntry error(String code, Phase phase, String path, String message) {
    return of(Severity.ERROR, code, phase, path, message);
  }

  public static ReportEntry warning(String code, Phase phase, String path, String message) {
    return of(Severity.WARNING, code, phase, path, message);
  }

  public static ReportEntry information(String code, Phase phase, String path, String message) {
    return of(Severity.INFORMATION, code, phase, path, message);
  }

  public ReportEntry withTargetPath(String value) {
    return new ReportEntry(severity, code, phase, path, value, message, clinicalLoss);
  }

  public ReportEntry withClinicalLoss(boolean value) {
    return new ReportEntry(severity, code, phase, path, targetPath, message, value);
  }
}
