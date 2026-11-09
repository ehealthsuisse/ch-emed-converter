package ch.emed.converter.epr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.determinism.NameBasedIdStrategy;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

/**
 * T037 (feature 004, research R6, R7, data model §5): one MTP document per treatment of a card.
 * Without a validator; {@code TreatmentPlansIT} validates. Regenerate the expected file with {@code
 * -Depr.regenerate=true}.
 */
class TreatmentPlanSplitterTest {

  private static final String EXPECTED = "expected/treatment-plans-3.json";

  private static final TreatmentPlanSplitter SPLITTER =
      new TreatmentPlanSplitter(new FromChEmedMapper(UnsupportedElements.load()));

  /** The CH EMED card example with a third treatment, a copy of the first. */
  private static Bundle card() {
    Bundle card = ChEmedJson.parse(Fixtures.bytes("chemed/medication-card-example.json"));
    Bundle.BundleEntryComponent first =
        card.getEntry().stream()
            .filter(e -> e.getResource() instanceof MedicationStatement)
            .findFirst()
            .orElseThrow();
    MedicationStatement third = ((MedicationStatement) first.getResource()).copy();
    third.setId("third");
    third.getIdentifierFirstRep().setValue("urn:uuid:0a1b2c3d-0000-4000-8000-000000000003");
    String url = "http://test.fhir.ch/r4/MedicationStatement/third";
    card.addEntry().setFullUrl(url).setResource(third);
    Composition composition = composition(card);
    composition.getSection().stream()
        .filter(s -> s.getEntry().stream().anyMatch(r -> r.getReference().contains("Medication")))
        .findFirst()
        .orElseThrow()
        .addEntry(new Reference("MedicationStatement/third"));
    return card;
  }

  private static Composition composition(Bundle bundle) {
    return (Composition) bundle.getEntryFirstRep().getResource();
  }

  private static ConversionOptions options() {
    return ConversionOptions.defaults().withOutputForm(OutputForm.TREATMENT_PLANS);
  }

  private static Bundle split(Bundle card, ConversionOptions options, ReportCollector report) {
    return SPLITTER.split(card, new MappingContext(options, report), null);
  }

  private static String uuid(String cardId, String path) {
    return "urn:uuid:" + new NameBasedIdStrategy().id(cardId, path);
  }

  @Test
  void oneMtpPerTreatmentInSectionOrder() {
    Bundle card = card();
    Composition cardComposition = composition(card);
    String cardId = cardComposition.getIdentifier().getValue();
    ReportCollector report = new ReportCollector();

    Bundle collection = split(card, options(), report);

    assertThat(report.hasErrors()).as(report.entries().toString()).isFalse();
    assertThat(collection.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    assertThat(collection.getIdentifier().getValue()).isEqualTo(uuid(cardId, "treatment-plans"));
    assertThat(collection.getTimestampElement().getValueAsString())
        .isEqualTo(cardComposition.getDateElement().getValueAsString());
    assertThat(collection.getEntry()).hasSize(3);
    assertThat(collection.getEntry()).allMatch(e -> e.getResource() instanceof Bundle);

    List<String> cardStatements =
        cardComposition.getSection().stream()
            .flatMap(s -> s.getEntry().stream())
            .map(Reference::getReference)
            .filter(r -> r.contains("MedicationStatement"))
            .toList();
    for (int i = 0; i < 3; i++) {
      Bundle mtp = (Bundle) collection.getEntry().get(i).getResource();
      Composition c = composition(mtp);
      String id = uuid(cardId, "/treatment-plans/" + i + "/Composition");
      assertThat(c.getIdentifier().getValue()).isEqualTo(id);
      assertThat(mtp.getIdentifier().getValue()).isEqualTo(id);
      assertThat(c.getType().getCoding())
          .extracting(org.hl7.fhir.r4.model.Coding::getCode)
          .containsExactly("761931002", "77603-9");
      assertThat(c.getTitle()).isEqualTo("Therapieentscheid Medikation");
      assertThat(c.getLanguage()).isEqualTo(cardComposition.getLanguage());
      assertThat(c.getDateElement().getValueAsString())
          .isEqualTo(cardComposition.getDateElement().getValueAsString());
      assertThat(c.getConfidentiality()).isEqualTo(cardComposition.getConfidentiality());
      assertThat(c.getConfidentialityElement().getExtension()).isNotEmpty();
      assertThat(c.getSection()).hasSize(1);
      assertThat(c.getSectionFirstRep().getCode().getCodingFirstRep().getCode())
          .isEqualTo("77604-7");
      assertThat(c.getSectionFirstRep().getEntry()).hasSize(1);
      assertThat(c.getSectionFirstRep().hasText()).isTrue();
      List<MedicationStatement> statements =
          mtp.getEntry().stream()
              .map(Bundle.BundleEntryComponent::getResource)
              .filter(MedicationStatement.class::isInstance)
              .map(MedicationStatement.class::cast)
              .toList();
      assertThat(statements).hasSize(1);
      assertThat(cardStatements.get(i)).endsWith(statements.get(0).getIdPart());
      assertThat(mtp.getEntry()).noneMatch(e -> e.getResource() instanceof Binary);
    }
  }

  @Test
  void entriesWithATreatmentPlanAreReportedButStillSplit() {
    ReportCollector report = new ReportCollector();

    Bundle collection = split(card(), options(), report);

    assertThat(collection.getEntry()).hasSize(3);
    assertThat(report.entries())
        .filteredOn(e -> e.code().equals(ReasonCode.TREATMENT_PLAN_EXISTS))
        .hasSize(3)
        .allSatisfy(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.path()).matches("Bundle\\.entry\\[\\d+]\\.resource\\.extension");
            });
  }

  @Test
  void theOriginalRepresentationOptionIsNotUsed() {
    Bundle card = card();
    byte[] pdfA = OriginalRepresentationRenderer.renderPdfA(card, new ReportCollector());
    ReportCollector report = new ReportCollector();

    Bundle with = split(card(), options().withOriginalRepresentation(pdfA), report);
    Bundle without = split(card(), options(), new ReportCollector());

    assertThat(ChEmedJson.serialize(with)).isEqualTo(ChEmedJson.serialize(without));
    assertThat(report.entries())
        .filteredOn(e -> e.message().contains("treatment-plans"))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isFalse();
            });
  }

  @Test
  void aCardWithoutTreatmentsHasNone() {
    Bundle card =
        ChEmedJson.parse(
            EprFixtures.bytes("official/Bundle-DocumentUCSF1CARAPMP004PMLCEmpty.json"));
    ReportCollector report = new ReportCollector();

    Bundle collection = split(card, options(), report);

    assertThat(collection).isNull();
    assertThat(report.entries())
        .extracting(ReportEntry::code, ReportEntry::severity)
        .containsExactly(
            org.assertj.core.api.Assertions.tuple(ReasonCode.NO_TREATMENTS, Severity.ERROR));
  }

  @Test
  void theCollectionOfAThreeEntryCardMatchesTheExpectedFile() throws IOException {
    byte[] actual = ChEmedJson.serialize(split(card(), options(), new ReportCollector()));
    if (Boolean.getBoolean("epr.regenerate")) {
      Path file = Path.of("src/test/resources/fixtures/" + EXPECTED);
      Files.createDirectories(file.getParent());
      Files.write(file, actual);
    }

    assertThat(new String(actual, StandardCharsets.UTF_8))
        .isEqualTo(new String(EprFixtures.bytes(EXPECTED), StandardCharsets.UTF_8));
  }
}
