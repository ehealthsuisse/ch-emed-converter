package ch.emed.converter.chmed16a.testsupport;

import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.OrganizationDetails;
import ch.emed.converter.core.api.PatientIdentifier;

/** Conversion options with fictitious author details and patient identifier for tests. */
public final class TestOptions {

  public static final AuthorDetails AUTHOR =
      new AuthorDetails(
          null,
          "Thea",
          "Test",
          new OrganizationDetails("Praxis Test", "Testgasse 1", "3000", "Bern", "CH"));

  public static final PatientIdentifier PATIENT_ID =
      new PatientIdentifier("urn:oid:2.999.1", "TEST-0001");

  private TestOptions() {}

  /** Defaults plus author details (for plans with patient Ids). */
  public static ConversionOptions withAuthor() {
    return ConversionOptions.defaults().withAuthor(AUTHOR);
  }

  /** Defaults plus author details and patient identifier (for plans without patient Ids). */
  public static ConversionOptions withAuthorAndPatientId() {
    return withAuthor().withPatientIdentifier(PATIENT_ID);
  }
}
