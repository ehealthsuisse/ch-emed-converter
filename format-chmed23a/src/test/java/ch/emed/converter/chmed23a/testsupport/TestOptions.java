package ch.emed.converter.chmed23a.testsupport;

import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.OrganizationDetails;
import ch.emed.converter.core.api.PatientIdentifier;

/** Conversion options with fictitious author details for CHMED23A tests. */
public final class TestOptions {

  /**
   * Fictitious practitioner GLN, for documents whose healthcare person has none (Example 2, finding
   * F10); the document's own author data is used otherwise.
   */
  public static final AuthorDetails AUTHOR =
      new AuthorDetails(
          "7601000000002",
          "Thea",
          "Test",
          new OrganizationDetails("Praxis Test", "Testgasse 1", "3000", "Bern", "CH"));

  private TestOptions() {}

  /** Fictitious patient id, for documents without a usable id (Example 3, finding F7). */
  public static final PatientIdentifier PATIENT_ID =
      new PatientIdentifier("urn:oid:2.999.1", "TEST-0001");

  /** Defaults plus author details and patient identifier. */
  public static ConversionOptions withAuthorAndPatientId() {
    return withAuthorGln().withPatientIdentifier(PATIENT_ID);
  }

  /** Defaults plus the author details. */
  public static ConversionOptions withAuthorGln() {
    return ConversionOptions.defaults().withAuthor(AUTHOR);
  }
}
