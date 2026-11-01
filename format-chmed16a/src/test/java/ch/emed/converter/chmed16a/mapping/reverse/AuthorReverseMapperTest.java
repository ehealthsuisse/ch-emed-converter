package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.CARD;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.document;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.entries;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

class AuthorReverseMapperTest {

  private final ChEmedDocument doc = document(CARD);
  private final ReverseScope scope = scope(doc);

  private Practitioner practitioner() {
    PractitionerRole role =
        doc.resolve(doc.composition().getAuthorFirstRep(), PractitionerRole.class);
    return doc.resolve(role.getPractitioner(), Practitioner.class);
  }

  private Organization organization() {
    PractitionerRole role =
        doc.resolve(doc.composition().getAuthorFirstRep(), PractitionerRole.class);
    return doc.resolve(role.getOrganization(), Organization.class);
  }

  @Test
  void practitionerGlnBecomesAuth() {
    var author = AuthorReverseMapper.map(scope);

    assertThat(author.auth()).isEqualTo("7601000234438");
    assertThat(author.zsr()).isNull();
    assertThat(scope.report().hasErrors()).isFalse();
  }

  @Test
  void patientAsAuthorBecomesPatientKeyword() {
    doc.composition().getAuthor().clear();
    doc.composition().addAuthor(new Reference(doc.composition().getSubject().getReference()));

    var author = AuthorReverseMapper.map(scope);

    assertThat(author.auth()).isEqualTo("patient");
  }

  @Test
  void practitionerWithoutGlnIsMissingRequiredData() {
    practitioner().getIdentifier().clear();

    var author = AuthorReverseMapper.map(scope);

    assertThat(author).isNull();
    assertThat(entries(scope, e -> e.severity() == Severity.ERROR))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA);
              assertThat(e.path()).isEqualTo("Composition.author");
            });
  }

  @Test
  void organizationZsrOfSevenCharactersBecomesZsr() {
    organization()
        .addIdentifier()
        .setSystem("urn:oid:2.16.756.5.30.1.123.100.2.1.1")
        .setValue("H123456");

    assertThat(AuthorReverseMapper.map(scope).zsr()).isEqualTo("H123456");
  }

  @Test
  void organizationZsrOfOtherLengthIsReportedNotCarried() {
    organization()
        .addIdentifier()
        .setSystem("urn:oid:2.16.756.5.30.1.123.100.2.1.1")
        .setValue("H12");

    assertThat(AuthorReverseMapper.map(scope).zsr()).isNull();
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT)))
        .singleElement()
        .satisfies(e -> assertThat(e.severity()).isEqualTo(Severity.INFORMATION));
  }

  @Test
  void furtherAuthorsAreReported() {
    doc.composition().addAuthor(new Reference(doc.composition().getSubject().getReference()));

    var author = AuthorReverseMapper.map(scope);

    assertThat(author.auth()).isEqualTo("7601000234438");
    assertThat(entries(scope, e -> e.path().equals("Composition.author[1]"))).hasSize(1);
  }
}
