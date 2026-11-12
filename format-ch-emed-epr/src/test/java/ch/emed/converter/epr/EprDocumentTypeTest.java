package ch.emed.converter.epr;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.epr.testsupport.EprFixtures;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** T011 (feature 004), T004 (feature 005): document type recognition (data-model.md §1/§2). */
class EprDocumentTypeTest {

  private static Bundle official(String name) {
    return ChEmedJson.parse(EprFixtures.bytes("official/" + name));
  }

  @ParameterizedTest
  @CsvSource({
    "Bundle-BundlePmlc1.json, PMLC",
    "Bundle-DocumentUCSF1CARAPMP004PMLCEmpty.json, PMLC",
    "Bundle-DocumentPre1.json, PRE",
    "Bundle-DocumentMtp1.json, MTP",
    "Bundle-DocumentDis1.json, DIS",
    "Bundle-DocumentUCSF4CARAPMP004DISDafalganWithoutPrescription.json, DIS",
    "Bundle-DocumentPadv1.json, PADV",
    "Bundle-DocumentPadv2.json, PADV",
    "Bundle-DocumentPadvCancelParacetamolCARAPMP004.json, PADV",
    "Bundle-DocumentPadvChangeParacetamolCARAPMP004.json, PADV",
    "Bundle-BundlePml1.json, PML",
    "Bundle-BundleUtc1Pml.json, PML",
    "Bundle-BundleUtc6bPml.json, PML"
  })
  void recognisesTheOfficialEprExamples(String name, EprDocumentType expected) {
    assertThat(EprDocumentType.of(official(name))).contains(expected);
  }

  /** The CH EMED examples; 2-1 also carries the LOINC code of the medication card. */
  @ParameterizedTest
  @CsvSource({
    "Bundle-1-2-MedicationDispense.json, DIS",
    "Bundle-2-4-MedicationDispense.json, DIS",
    "Bundle-3-1-MedicationDispense.json, DIS",
    "Bundle-3-2-MedicationDispense.json, DIS",
    "Bundle-2-2-PharmaceuticalAdvice.json, PADV",
    "Bundle-PharmaceuticalAdvice-ChangeDosage.json, PADV",
    "Bundle-2-1-MedicationList.json, PML"
  })
  void recognisesTheChEmedExamples(String name, EprDocumentType expected) {
    Bundle bundle = ChEmedJson.parse(EprFixtures.bytes("chemed/" + name));

    assertThat(EprDocumentType.of(bundle)).contains(expected);
  }

  @Test
  void anUnknownTypeIsNamedWithAllSupportedTypes() {
    Bundle bundle = official("Bundle-DocumentDis1.json");
    ((Composition) bundle.getEntryFirstRep().getResource())
        .getType()
        .getCoding()
        .forEach(c -> c.setCode("1234567"));

    assertThat(EprDocumentType.of(bundle)).isEmpty();
    assertThat(EprDocumentType.unsupportedMessage(bundle))
        .contains("not recognised")
        .contains("PMLC", "PRE", "MTP", "DIS", "PADV", "PML");
  }

  @ParameterizedTest
  @EnumSource(EprDocumentType.class)
  void profilesFollowTheNamingOfBothIgs(EprDocumentType type) {
    assertThat(type.eprDocumentProfile())
        .startsWith("http://fhir.ch/ig/ch-emed-epr/StructureDefinition/ch-emed-epr-document-");
    assertThat(type.chEmedCompositionProfile())
        .startsWith("http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-composition-");
  }
}
