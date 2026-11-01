package ch.emed.converter.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** FR-017: validator messages must not carry patient values into the report. */
class ValidationMessageSanitizerTest {

  @Test
  void removesSingleQuotedValues() {
    String sanitized =
        ValidationMessageSanitizer.sanitize(
            "Type_Specific_Checks_DT_String_Length",
            "The value 'Wegmüller' exceeds the allowed length");

    assertThat(sanitized)
        .doesNotContain("Wegmüller")
        .contains("Type_Specific_Checks_DT_String_Length");
  }

  @Test
  void removesDoubleQuotedValues() {
    String sanitized =
        ValidationMessageSanitizer.sanitize(null, "Value \"Monika\" does not match pattern");

    assertThat(sanitized).doesNotContain("Monika");
  }

  @Test
  void removesDatesAndLongDigitSequences() {
    String sanitized =
        ValidationMessageSanitizer.sanitize(
            null, "Birth date 1943-05-15 and identifier 7560000000000 are inconsistent");

    assertThat(sanitized).doesNotContain("1943-05-15").doesNotContain("7560000000000");
  }

  @Test
  void keepsStructuralInformation() {
    String sanitized =
        ValidationMessageSanitizer.sanitize(
            "Reference_REF_CantMatchChoice", "Unable to find a match for profile among choices");

    assertThat(sanitized)
        .isEqualTo(
            "Reference_REF_CantMatchChoice: Unable to find a match for profile among choices");
  }
}
