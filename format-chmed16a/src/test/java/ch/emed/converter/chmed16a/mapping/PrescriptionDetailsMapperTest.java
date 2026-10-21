package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.parse;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.read;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.List;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.Test;

/** CHMED16A → CH EMED prescription details (M-RX-05..M-RX-09). */
class PrescriptionDetailsMapperTest {

  private static final String RX =
      """
      {"Id":"7b1f3c1e-9d54-4a0e-8e0c-2a6c1f9e8d02","MedType":3,"Auth":"7601000000001",
       "Dt":"%s","rev":3,
       "Patient":{"FName":"Hans","LName":"Muster","BDt":"1950-03-21","Gender":1%s},
       "HcPerson":{"Gln":"7601000000001","FName":"Thea","LName":"Test"},
       "HcOrg":{"Name":"Praxis Test","Street":"Testgasse 1","Zip":"3000","City":"Bern"},
       "Medicaments":[{"Id":"7680521101306","IdType":2%s}]}
      """;

  private MappingScope scope;

  private MedicationRequest map(String dt, String patientExtra, String medicamentExtra) {
    Medication med = parse(RX.formatted(dt, patientExtra, medicamentExtra));
    scope = scope(med, ConversionOptions.defaults());
    MedicationRequest request = new MedicationRequest();
    Medicament m = med.medicaments().get(0);
    PrescriptionDetailsMapper.apply(request, m, med, "/Medicaments/0", scope);
    return request;
  }

  private MedicationRequest map(String medicamentExtra) {
    return map("2026-10-01T09:30:00+02:00", "", medicamentExtra);
  }

  private List<ReportEntry> entries(String path) {
    return scope.report().entries().stream().filter(e -> e.path().equals(path)).toList();
  }

  @Test
  void nothingIsMappedWhenThePrescriptionHasNoDetails() {
    MedicationRequest request = map("");

    assertThat(request.hasDispenseRequest()).isFalse();
    assertThat(request.hasSubstitution()).isFalse();
    assertThat(request.hasReasonCode()).isFalse();
    assertThat(scope.report().entries()).isEmpty();
  }

  @Test
  void numberOfPackagesBecomesTheDispenseQuantity() {
    MedicationRequest request = map(",\"NbPack\":2");

    var quantity = request.getDispenseRequest().getQuantity();
    assertThat(quantity.getValue()).isEqualByComparingTo("2");
    assertThat(quantity.getSystem()).isEqualTo("http://snomed.info/sct");
    assertThat(quantity.getCode()).isEqualTo("1681000175101");
    assertThat(quantity.getUnit()).isEqualTo("Package - unit of product usage (qualifier value)");
  }

  @Test
  void repetitionMonthsBecomeAnInclusiveValidityPeriodFromThePrescriptionDate() {
    String[][] cases = {
      {"2026-10-01T09:30:00+02:00", "3", "2026-10-01", "2026-12-31"},
      {"2012-02-04T14:00:00+01:00", "3", "2012-02-04", "2012-05-03"},
      {"2012-02-04T14:00:00+01:00", "6", "2012-02-04", "2012-08-03"},
      {"2026-01-31T23:30:00-05:00", "1", "2026-01-31", "2026-02-27"}
    };
    for (String[] c : cases) {
      MedicationRequest request = map(c[0], "", ",\"Rep\":" + c[1]);

      var period = request.getDispenseRequest().getValidityPeriod();
      assertThat(period.getStartElement().getValueAsString())
          .as(c[0] + " +" + c[1])
          .isEqualTo(c[2]);
      assertThat(period.getEndElement().getValueAsString()).as(c[0] + " +" + c[1]).isEqualTo(c[3]);
    }
  }

  @Test
  void zeroOrAbsentRepetitionGivesNoValidityPeriod() {
    assertThat(map(",\"Rep\":0").getDispenseRequest().hasValidityPeriod()).isFalse();
    assertThat(map("").getDispenseRequest().hasValidityPeriod()).isFalse();
  }

  @Test
  void substitutionRuleMapsToTheV3SubstitutionCodes() {
    String system = "http://terminology.hl7.org/CodeSystem/v3-substanceAdminSubstitution";

    var none = map(",\"Subs\":1").getSubstitution().getAllowedCodeableConcept().getCodingFirstRep();
    var equivalent =
        map(",\"Subs\":0").getSubstitution().getAllowedCodeableConcept().getCodingFirstRep();

    assertThat(none.getSystem()).isEqualTo(system);
    assertThat(none.getCode()).isEqualTo("N");
    assertThat(none.getDisplay()).isEqualTo("none");
    assertThat(equivalent.getCode()).isEqualTo("E");
    assertThat(equivalent.getDisplay()).isEqualTo("equivalent");
    assertThat(map("").hasSubstitution()).as("the default 0 is not materialised").isFalse();
  }

  @Test
  void indicationCodeIsCarriedAsTextAndReported() {
    MedicationRequest request = map(",\"IndC\":\"12345.01\"");

    assertThat(request.getReasonCode())
        .singleElement()
        .satisfies(c -> assertThat(c.getText()).isEqualTo("SL indication code 12345.01"));
    assertThat(entries("/Medicaments/0/IndC"))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isFalse();
            });
  }

  @Test
  void receiverBecomesALogicalReferenceToTheIntendedDispenser() {
    MedicationRequest request = map("2026-10-01T09:30:00+02:00", ",\"Rcv\":\"7601000000099\"", "");

    var performer = request.getDispenseRequest().getPerformer();
    assertThat(performer.hasReference()).isFalse();
    assertThat(performer.getIdentifier().getSystem()).isEqualTo("urn:oid:2.51.1.3");
    assertThat(performer.getIdentifier().getValue()).isEqualTo("7601000000099");
  }

  @Test
  void theOfficialFixtureMapsEveryDetail() {
    Medication med = read("rx-basic.json");
    MappingScope s = scope(med, ConversionOptions.defaults());
    MedicationRequest request = new MedicationRequest();

    PrescriptionDetailsMapper.apply(request, med.medicaments().get(0), med, "/Medicaments/0", s);

    assertThat(request.getDispenseRequest().getQuantity().getValue()).isEqualByComparingTo("2");
    assertThat(request.getDispenseRequest().getValidityPeriod().getEndElement().getValueAsString())
        .isEqualTo("2026-12-31");
    assertThat(request.getDispenseRequest().getPerformer().getIdentifier().getValue())
        .isEqualTo("7601000000099");
    assertThat(request.getSubstitution().getAllowedCodeableConcept().getCodingFirstRep().getCode())
        .isEqualTo("N");
    assertThat(request.getReasonCodeFirstRep().getText()).isEqualTo("SL indication code 12345.01");
  }
}
