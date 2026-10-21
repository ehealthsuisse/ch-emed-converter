package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.document;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.entries;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.List;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

/** M-REV-30..M-REV-40: the CHMED16A prescription built from a CH EMED Medication Prescription. */
class PrescriptionBuilderTest {

  private ChEmedDocument doc = document("chemed/medication-prescription-example.json");
  private ReverseScope scope = scope(doc);

  private Medication build() {
    return PrescriptionBuilder.build(scope);
  }

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

  private List<ReportEntry> errors() {
    return entries(scope, e -> e.severity() == Severity.ERROR);
  }

  @Test
  void buildsThePrescriptionOfTheOfficialExample() {
    Medication med = build();

    assertThat(errors()).isEmpty();
    assertThat(med.medType()).isEqualTo(Medication.MED_TYPE_PRESCRIPTION);
    assertThat(med.rev()).isEqualTo(Medication.LATEST_PRESCRIPTION_REVISION);
    assertThat(med.id()).isEqualTo("1c5b5e9b-24f7-45ed-ae9c-6e2ec53e7b05");
    assertThat(med.dt()).isEqualTo("2012-02-04T14:00:00+01:00");
    assertThat(med.auth()).isEqualTo("7601000234438");
    assertThat(med.hcPerson().gln()).isEqualTo("7601000234438");
    assertThat(med.hcPerson().fName()).isEqualTo("Familien");
    assertThat(med.hcPerson().lName()).isEqualTo("Hausarzt");
    assertThat(med.hcOrg().name()).isEqualTo("Hausarzt");
    assertThat(med.hcOrg().street()).isEqualTo("Krankenstrasse 2");
    assertThat(med.hcOrg().zip()).isEqualTo("8005");
    assertThat(med.hcOrg().city()).isEqualTo("Zürich");
    assertThat(med.hcOrg().country()).isEqualTo("CH");
    assertThat(med.rmk()).isEqualTo("Kommentar zu Medication Prescription");
    assertThat(med.valBy()).isNull();
    assertThat(med.patient().med()).isNull();
  }

  @Test
  void thePatientHasNoLanguageInAPrescription() {
    Medication med = build();

    assertThat(med.patient().lng()).isNull();
    assertThat(med.patient().rcv()).isNull();
    assertThat(entries(scope, e -> e.path().equals("Patient.communication"))).isEmpty();
  }

  @Test
  void theMedicamentCarriesOnlyFieldsOfThePrescriptionSpecification() {
    Medicament m = build().medicaments().get(0);

    assertThat(m.id()).isEqualTo("7680500440334");
    assertThat(m.idType()).isEqualTo(Medicament.ID_TYPE_GTIN);
    assertThat(m.unit()).isEqualTo("STK");
    assertThat(m.appInstr()).isEqualTo("-");
    assertThat(m.rep()).isEqualTo(3);
    assertThat(m.subs()).isEqualTo(0);
    assertThat(m.nbPack()).isNull();
    assertThat(m.indC()).isNull();
    assertThat(m.pos())
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.dtFrom()).isNull();
              assertThat(p.dtTo()).isNull();
              assertThat(p.d()).hasSize(4);
              assertThat(p.tt()).isEmpty();
              assertThat(p.inRes()).isNull();
            });
    assertThat(m.tkgRsn()).isNull();
    assertThat(m.roa()).isNull();
    assertThat(m.autoMed()).isNull();
    assertThat(m.prscbBy()).isNull();
  }

  @Test
  void whatThePrescriptionCannotExpressIsReported() {
    build();

    assertThat(
            entries(scope, e -> e.path().equals("MedicationRequest[0].dosageInstruction[0].route")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isTrue();
            });
    assertThat(entries(scope, e -> e.path().equals("MedicationRequest[0].reasonCode[0]")))
        .singleElement()
        .satisfies(e -> assertThat(e.clinicalLoss()).isTrue());
    assertThat(
            entries(
                scope,
                e ->
                    e.path()
                        .equals(
                            "MedicationRequest[0].dosageInstruction[0].timing.repeat.boundsPeriod.start")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
              assertThat(e.clinicalLoss()).isFalse();
            });
  }

  @Test
  void aStartDateDifferentFromThePrescriptionDateIsALoss() {
    MedicationRequest request = doc.all(MedicationRequest.class).get(0);
    request
        .getDosageInstructionFirstRep()
        .getTiming()
        .getRepeat()
        .getBoundsPeriod()
        .setStartElement(new org.hl7.fhir.r4.model.DateTimeType("2012-03-01"));

    build();

    assertThat(entries(scope, e -> e.path().endsWith("boundsPeriod.start")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isTrue();
            });
  }

  @Test
  void theEndDateBecomesDtTo() {
    MedicationRequest request = doc.all(MedicationRequest.class).get(0);
    request
        .getDosageInstructionFirstRep()
        .getTiming()
        .getRepeat()
        .getBoundsPeriod()
        .setEndElement(new org.hl7.fhir.r4.model.DateTimeType("2012-04-30"));

    assertThat(build().medicaments().get(0).pos().get(0).dtTo()).isEqualTo("2012-04-30");
  }

  @Test
  void dosageThePrescriptionCannotExpressIsCarriedAsText() {
    MedicationRequest request = doc.all(MedicationRequest.class).get(0);
    request.getDosageInstructionFirstRep().setAsNeeded(new org.hl7.fhir.r4.model.BooleanType(true));

    Medicament m = build().medicaments().get(0);

    assertThat(m.pos()).isEmpty();
    assertThat(m.appInstr()).startsWith("-; dose=1 STK; when=MORN,EVE");
    assertThat(m.appInstr()).contains("asNeeded").contains("from=2012-02-04");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK)))
        .isNotEmpty()
        .allSatisfy(e -> assertThat(e.clinicalLoss()).isTrue());
  }

  @Test
  void aMedicationWithoutDosageTextHasNoPosology() {
    MedicationRequest request = doc.all(MedicationRequest.class).get(0);
    request.getDosageInstruction().clear();
    request.addDosageInstruction().setText("Dosierung nicht angegeben");

    Medicament m = build().medicaments().get(0);

    assertThat(m.pos()).isEmpty();
    assertThat(m.unit()).isNull();
  }

  @Test
  void cancelledAndStoppedRequestsAreExcludedAndReported() {
    MedicationRequest request = doc.all(MedicationRequest.class).get(0);
    request.setStatus(MedicationRequestStatus.CANCELLED);

    Medication med = build();

    assertThat(med == null || med.medicaments().isEmpty()).isTrue();
    assertThat(entries(scope, e -> e.path().equals("MedicationRequest[0].status")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.clinicalLoss()).isTrue();
              assertThat(e.message()).contains("excluded");
            });
  }

  @Test
  void aPrescriberWithoutGlnViolatesTheErezeptRequirements() {
    practitioner().getIdentifier().clear();

    build();

    assertThat(errors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.EREZEPT_REQUIREMENT);
              assertThat(e.path()).isEqualTo("Composition.author");
            });
  }

  @Test
  void thePatientAsAuthorViolatesTheErezeptRequirements() {
    doc.composition().getAuthor().clear();
    doc.composition().addAuthor(new Reference(doc.composition().getSubject().getReference()));

    build();

    assertThat(errors())
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.EREZEPT_REQUIREMENT));
  }

  @Test
  void missingOrganisationAddressIsMissingRequiredData() {
    organization().getAddress().clear();

    build();

    assertThat(errors())
        .extracting(ReportEntry::path)
        .contains(
            "Organization.address.line",
            "Organization.address.postalCode",
            "Organization.address.city");
    assertThat(errors())
        .allSatisfy(e -> assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA));
  }

  @Test
  void zsrOfPersonOrOrganisationBecomesTheRootZsrAndTheHealthcareObjectZsr() {
    organization()
        .addIdentifier()
        .setSystem("urn:oid:2.16.756.5.30.1.123.100.2.1.1")
        .setValue("H123456");

    Medication med = build();

    assertThat(med.zsr()).isEqualTo("H123456");
    assertThat(med.hcOrg().zsr()).isEqualTo("H123456");
    assertThat(med.hcPerson().zsr()).isNull();
  }

  @Test
  void zsrOnBothPersonAndOrganisationKeepsThePersonsAndReportsTheOther() {
    practitioner()
        .addIdentifier()
        .setSystem("urn:oid:2.16.756.5.30.1.123.100.2.1.1")
        .setValue("P111111");
    organization()
        .addIdentifier()
        .setSystem("urn:oid:2.16.756.5.30.1.123.100.2.1.1")
        .setValue("H123456");

    Medication med = build();

    assertThat(med.hcPerson().zsr()).isEqualTo("P111111");
    assertThat(med.hcOrg().zsr()).isNull();
    assertThat(med.zsr()).isEqualTo("P111111");
    assertThat(
            entries(
                scope,
                e -> e.path().startsWith("Organization.identifier") && e.message().contains("ZSR")))
        .singleElement()
        .satisfies(e -> assertThat(e.severity()).isEqualTo(Severity.INFORMATION));
  }

  @Test
  void theDocumentIdIsWrittenInLowerCase() {
    doc.bundle().getIdentifier().setValue("urn:uuid:1C5B5E9B-24F7-45ED-AE9C-6E2EC53E7B05");

    Medication med = build();

    assertThat(med.id()).isEqualTo("1c5b5e9b-24f7-45ed-ae9c-6e2ec53e7b05");
    assertThat(entries(scope, e -> e.path().equals("Bundle.identifier")))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE));
  }

  @Test
  void theOrganisationNameAffixComesFromTheFirstAliasAndTelecomsAreMapped() {
    organization().addAlias("Allgemeine Innere Medizin");
    organization()
        .addTelecom()
        .setSystem(org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.PHONE)
        .setValue("+41 31 111 11 11");
    organization()
        .addTelecom()
        .setSystem(org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.EMAIL)
        .setValue("praxis@example.org");

    Medication med = build();

    assertThat(med.hcOrg().nameAffix()).isEqualTo("Allgemeine Innere Medizin");
    assertThat(med.hcOrg().phone()).isEqualTo("+41 31 111 11 11");
    assertThat(med.hcOrg().email()).isEqualTo("praxis@example.org");
  }

  @Test
  void theReceiverBecomesThePatientsRcv() {
    doc.all(MedicationRequest.class)
        .get(0)
        .getDispenseRequest()
        .getPerformer()
        .setIdentifier(new Identifier().setSystem("urn:oid:2.51.1.3").setValue("7601000000099"));

    assertThat(build().patient().rcv()).isEqualTo("7601000000099");
  }

  @Test
  void aPrescriptionWithoutAnyMedicamentViolatesTheErezeptRequirements() {
    doc.bundle().getEntry().removeIf(e -> e.getResource() instanceof MedicationRequest);

    build();

    assertThat(errors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.EREZEPT_REQUIREMENT);
              assertThat(e.path()).isEqualTo("MedicationRequest");
            });
  }
}
