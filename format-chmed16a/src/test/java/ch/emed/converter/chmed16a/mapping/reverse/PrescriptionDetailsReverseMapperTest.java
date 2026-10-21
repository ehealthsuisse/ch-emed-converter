package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.document;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.entries;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.mapping.reverse.PrescriptionDetailsReverseMapper.Details;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

/** CH EMED → CHMED16A prescription details (M-REV-35..M-REV-39). */
class PrescriptionDetailsReverseMapperTest {

  private static final String SUBSTITUTION =
      "http://terminology.hl7.org/CodeSystem/v3-substanceAdminSubstitution";

  private final ChEmedDocument doc = document("chemed/medication-prescription-example.json");
  private final ReverseScope scope = scope(doc);

  private MedicationRequest request() {
    return doc.all(MedicationRequest.class).get(0).copy();
  }

  private Details map(MedicationRequest request) {
    return PrescriptionDetailsReverseMapper.map(request, "MedicationRequest[0]", scope);
  }

  private static Quantity packages(double value) {
    return new Quantity()
        .setValue(value)
        .setSystem("http://snomed.info/sct")
        .setCode("1681000175101");
  }

  @Test
  void theOfficialExampleHasThreeMonthsOfValidityAndAllowsEquivalentSubstitution() {
    Details details = map(request());

    assertThat(details.rep()).isEqualTo(3);
    assertThat(details.subs()).isEqualTo(0);
    assertThat(details.nbPack()).isNull();
    assertThat(details.indC()).isNull();
    assertThat(details.receiver()).isNull();
  }

  @Test
  void packageQuantityBecomesNbPack() {
    MedicationRequest request = request();
    request.getDispenseRequest().setQuantity(packages(2));

    assertThat(map(request).nbPack()).isEqualTo(2L);
    assertThat(entries(scope, e -> e.path().endsWith("quantity"))).isEmpty();
  }

  @Test
  void quantitiesInOtherUnitsOrWithFractionsAreReportedWithClinicalLoss() {
    MedicationRequest tablets = request();
    tablets
        .getDispenseRequest()
        .setQuantity(
            new Quantity().setValue(30).setSystem("http://snomed.info/sct").setCode("732936001"));
    MedicationRequest fraction = request();
    fraction.getDispenseRequest().setQuantity(packages(1.5));

    assertThat(map(tablets).nbPack()).isNull();
    assertThat(map(fraction).nbPack()).isNull();
    assertThat(
            entries(scope, e -> e.path().equals("MedicationRequest[0].dispenseRequest.quantity")))
        .hasSize(2)
        .allSatisfy(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isTrue();
            });
  }

  @Test
  void validityIsRepetitionOnlyIfItIsAWholeNumberOfMonths() {
    String[][] cases = {
      {"2012-02-04", "2012-08-03", "6"},
      {"2026-01-31", "2026-02-27", "1"},
      {"2026-10-01", "2027-09-30", "12"}
    };
    for (String[] c : cases) {
      MedicationRequest request = request();
      request
          .getDispenseRequest()
          .getValidityPeriod()
          .setStartElement(new org.hl7.fhir.r4.model.DateTimeType(c[0]))
          .setEndElement(new org.hl7.fhir.r4.model.DateTimeType(c[1]));

      assertThat(map(request).rep()).as(c[0] + ".." + c[1]).isEqualTo(Integer.valueOf(c[2]));
    }
    assertThat(entries(scope, e -> e.path().endsWith("validityPeriod"))).isEmpty();
  }

  @Test
  void otherValidityPeriodsAreReportedWithClinicalLoss() {
    MedicationRequest unaligned = request();
    unaligned
        .getDispenseRequest()
        .getValidityPeriod()
        .setStartElement(new org.hl7.fhir.r4.model.DateTimeType("2012-02-04"))
        .setEndElement(new org.hl7.fhir.r4.model.DateTimeType("2012-03-01"));
    MedicationRequest openEnded = request();
    openEnded.getDispenseRequest().getValidityPeriod().setEnd(null);

    assertThat(map(unaligned).rep()).isNull();
    assertThat(map(openEnded).rep()).isNull();
    assertThat(entries(scope, e -> e.path().endsWith("dispenseRequest.validityPeriod")))
        .hasSize(2)
        .allSatisfy(e -> assertThat(e.clinicalLoss()).isTrue());
  }

  @Test
  void numberOfRepeatsAllowedHasNoCounterpart() {
    MedicationRequest request = request();
    request.getDispenseRequest().setNumberOfRepeatsAllowed(2);

    map(request);

    assertThat(entries(scope, e -> e.path().endsWith("numberOfRepeatsAllowed")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isTrue();
            });
  }

  @Test
  void substitutionCodes() {
    String[][] cases = {{"N", "1"}, {"E", "0"}};
    for (String[] c : cases) {
      MedicationRequest request = request();
      request
          .getSubstitution()
          .setAllowed(new CodeableConcept(new Coding(SUBSTITUTION, c[0], null)));

      assertThat(map(request).subs()).as(c[0]).isEqualTo(Integer.valueOf(c[1]));
    }
    assertThat(entries(scope, e -> e.path().endsWith("substitution"))).isEmpty();
  }

  @Test
  void otherSubstitutionCodesAreReadAsNoSubstitutionAndReported() {
    MedicationRequest request = request();
    request.getSubstitution().setAllowed(new CodeableConcept(new Coding(SUBSTITUTION, "BC", null)));

    assertThat(map(request).subs()).isEqualTo(1);
    assertThat(entries(scope, e -> e.path().endsWith("substitution")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isTrue();
            });
  }

  @Test
  void booleanSubstitutionAndAbsentSubstitution() {
    MedicationRequest no = request();
    no.getSubstitution().setAllowed(new BooleanType(false));
    MedicationRequest yes = request();
    yes.getSubstitution().setAllowed(new BooleanType(true));
    MedicationRequest absent = request();
    absent.setSubstitution(null);

    assertThat(map(no).subs()).isEqualTo(1);
    assertThat(map(yes).subs()).isEqualTo(0);
    assertThat(map(absent).subs()).isNull();
  }

  @Test
  void indicationCodeTextIsInverted() {
    MedicationRequest request = request();
    request.getReasonCode().clear();
    request.addReasonCode().setText("SL indication code 12345.01");

    assertThat(map(request).indC()).isEqualTo("12345.01");
    assertThat(entries(scope, e -> e.clinicalLoss())).isEmpty();
  }

  @Test
  void everyOtherReasonIsReportedWithClinicalLoss() {
    MedicationRequest request = request();

    Details details = map(request);

    assertThat(details.indC()).isNull();
    assertThat(entries(scope, e -> e.path().equals("MedicationRequest[0].reasonCode[0]")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isTrue();
              assertThat(e.message()).doesNotContain("-\"");
            });
  }

  @Test
  void receiverFromAGlnIdentifierOrAResolvableOrganisation() {
    MedicationRequest logical = request();
    logical
        .getDispenseRequest()
        .getPerformer()
        .setIdentifier(new Identifier().setSystem("urn:oid:2.51.1.3").setValue("7601000000099"));
    MedicationRequest resolved = request();
    Organization pharmacy = new Organization();
    pharmacy.setId("pharmacy");
    pharmacy.addIdentifier().setSystem("urn:oid:2.51.1.3").setValue("7601000000088");
    doc.bundle()
        .addEntry()
        .setFullUrl("http://test.fhir.ch/r4/Organization/pharmacy")
        .setResource(pharmacy);
    resolved.getDispenseRequest().setPerformer(new Reference("Organization/pharmacy"));

    assertThat(map(logical).receiver()).isEqualTo("7601000000099");
    assertThat(map(resolved).receiver()).isEqualTo("7601000000088");
  }

  @Test
  void aPerformerWithoutGlnIsReported() {
    MedicationRequest request = request();
    request.getDispenseRequest().getPerformer().setDisplay("Apotheke");

    assertThat(map(request).receiver()).isNull();
    assertThat(entries(scope, e -> e.path().endsWith("dispenseRequest.performer")))
        .singleElement()
        .satisfies(e -> assertThat(e.severity()).isEqualTo(Severity.INFORMATION));
  }
}
