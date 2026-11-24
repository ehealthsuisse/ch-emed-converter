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
  void apostropheInWordDoesNotOpenQuote() {
    // Terminology_PassThrough_TX_Message: "{0} (for ''{1}#{2}'')"; the apostrophe of "can't"
    // must not pair with the opening quote of the value
    String sanitized =
        ValidationMessageSanitizer.sanitize(
            "Terminology_PassThrough_TX_Message",
            "CodeSystem is unknown and can't be validated (for 'http://loinc.org#55108-5')");

    assertThat(sanitized)
        .isEqualTo(
            "Terminology_PassThrough_TX_Message: CodeSystem is unknown and can't be validated"
                + " (for '…')");
  }

  @Test
  void removesValueAfterSeveralApostropheWords() {
    String sanitized =
        ValidationMessageSanitizer.sanitize(
            null, "The name doesn't match and isn't allowed: 'Wegmüller', see the patient's data");

    assertThat(sanitized)
        .doesNotContain("Wegmüller")
        .isEqualTo("The name doesn't match and isn't allowed: '…', see the patient's data");
  }

  @Test
  void removesQuotedValueContainingApostrophe() {
    String sanitized =
        ValidationMessageSanitizer.sanitize(null, "The value 'O'Brien' exceeds the length");

    assertThat(sanitized).doesNotContain("Brien").isEqualTo("The value '…' exceeds the length");
  }

  @Test
  void removesUnclosedQuotedValue() {
    String sanitized = ValidationMessageSanitizer.sanitize(null, "Can't parse 'Wegmüller");

    assertThat(sanitized).doesNotContain("Wegmüller").isEqualTo("Can't parse '…");
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

  @Test
  void removesResourceIdsFromLocations() {
    assertThat(
            ValidationMessageSanitizer.location(
                "Bundle.entry[1].resource/*Patient/PatientDupont*/.identifier[0].value"))
        .isEqualTo("Bundle.entry[1].resource/*Patient*/.identifier[0].value");
    assertThat(ValidationMessageSanitizer.location(null)).isEqualTo("Bundle");
    assertThat(ValidationMessageSanitizer.location("Bundle.entry[0]")).isEqualTo("Bundle.entry[0]");
  }
}
