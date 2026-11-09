package ch.emed.converter.epr;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.epr.testsupport.EprFixtures;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** T011 (feature 004): document type recognition (data-model.md §2). */
class EprDocumentTypeTest {

  private static Bundle official(String name) {
    return ChEmedJson.parse(EprFixtures.bytes("official/" + name));
  }

  @ParameterizedTest
  @CsvSource({
    "Bundle-BundlePmlc1.json, PMLC",
    "Bundle-DocumentUCSF1CARAPMP004PMLCEmpty.json, PMLC",
    "Bundle-DocumentPre1.json, PRE",
    "Bundle-DocumentMtp1.json, MTP"
  })
  void recognisesTheThreeTypes(String name, EprDocumentType expected) {
    assertThat(EprDocumentType.of(official(name))).contains(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "Bundle-DocumentDis1.json, medication dispense (DIS)",
    "Bundle-DocumentPadv1.json, pharmaceutical advice (PADV)",
    "Bundle-BundlePml1.json, medication list (PML)"
  })
  void laterTypesAreNotRecognisedAndNamed(String name, String label) {
    Bundle bundle = official(name);

    assertThat(EprDocumentType.of(bundle)).isEmpty();
    assertThat(EprDocumentType.unsupportedMessage(bundle))
        .contains(label + " is not yet supported")
        .contains("PMLC", "PRE", "MTP");
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
