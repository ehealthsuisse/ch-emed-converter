package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.LargePlanGenerator;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.api.TreatmentPlanReference;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.validation.HapiChEmedValidator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * T055 (feature 004, SC-005, research R14): times of CH EMED EPR conversions with the official
 * validation and a warm context. Run on andromeda with 4 vCPU; excluded from the normal build like
 * the time test of feature 001. Numbers are recorded in {@code docs/operations/rest-service.md}.
 * Feature 005 (T028, SC-004 as adjusted 2026-11-12): a medication list of about 100 entries made
 * from the official list example (statements, requests, observations; no official list holds a
 * dispense); {@code -Depr.listSize=n} adds n entries of each kind. The time grows linearly.
 */
class PerformanceTest {

  private static final Logger LOG = LoggerFactory.getLogger(PerformanceTest.class);
  private static final long TYPICAL_LIMIT_MILLIS = 5_000;
  private static final long LARGE_LIMIT_MILLIS = 30_000;

  private static long millis(Runnable action) {
    long start = System.nanoTime();
    action.run();
    return (System.nanoTime() - start) / 1_000_000;
  }

  /** A CH EMED card of {@code n} medications from feature 001, with a valid fictitious GLN. */
  private static byte[] card(Converter converter, int n) {
    byte[] out =
        converter
            .convert(
                new ConversionRequest(
                    FormatId.CHMED16A,
                    FormatId.CH_EMED,
                    LargePlanGenerator.plan(n),
                    TestOptions.withAuthorAndPatientId()))
            .output()
            .orElseThrow();
    return new String(out, StandardCharsets.UTF_8)
        .replace(ChEmedToEprIT.INVALID_GLN, ChEmedToEprIT.VALID_GLN)
        .getBytes(StandardCharsets.UTF_8);
  }

  /** The card with an aggregation-service Device as only author, as a PMLC requires (F8). */
  private static byte[] withDeviceAuthor(byte[] card) {
    Bundle bundle = ChEmedJson.parse(card);
    Device device = new Device();
    device.setId("aggregation-service");
    device
        .addDeviceName()
        .setName("Test aggregation service")
        .setType(Device.DeviceNameType.MANUFACTURERNAME);
    String url = "urn:uuid:" + UUID.nameUUIDFromBytes("device".getBytes());
    bundle.addEntry().setFullUrl(url).setResource(device);
    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
    composition.getAuthor().clear();
    composition.addAuthor(new Reference(url));
    return ChEmedJson.serialize(bundle);
  }

  private static List<TreatmentPlanReference> references(byte[] card) {
    long n =
        ChEmedJson.parse(card).getEntry().stream()
            .filter(e -> e.getResource() instanceof MedicationStatement)
            .count();
    List<TreatmentPlanReference> list = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      list.add(
          new TreatmentPlanReference(
              "urn:uuid:" + UUID.nameUUIDFromBytes(("d" + i).getBytes()),
              "urn:uuid:" + UUID.nameUUIDFromBytes(("s" + i).getBytes())));
    }
    return list;
  }

  @Test
  void eprConversionsStayWithinTheTimeLimits() {
    long[] cold = new long[1];
    Converter[] holder = new Converter[1];
    cold[0] = millis(() -> holder[0] = Converters.create(new HapiChEmedValidator()));
    Converter converter = holder[0];
    LOG.info("validation context with the CH EMED EPR package: {} ms", cold[0]);

    byte[] card20 = withDeviceAuthor(card(converter, 20));
    ConversionRequest pmlc =
        new ConversionRequest(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            card20,
            ConversionOptions.defaults().withTreatmentPlanReferences(references(card20)));
    converter.convert(pmlc);
    ConversionResult[] result = new ConversionResult[1];
    long typical = millis(() -> result[0] = converter.convert(pmlc));
    LOG.info("20 medications, CH EMED -> PMLC with validation: {} ms", typical);
    assertThat(result[0].outcome())
        .as(result[0].report().entries().toString())
        .isNotEqualTo(Outcome.FAILED);

    byte[] card100 = card(converter, 100);
    ConversionRequest plans100 =
        new ConversionRequest(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            card100,
            ConversionOptions.defaults().withOutputForm(OutputForm.TREATMENT_PLANS));
    converter.convert(plans100);
    long large = millis(() -> result[0] = converter.convert(plans100));
    LOG.info("100 treatments, CH EMED -> treatment-plans with validation: {} ms", large);
    assertThat(result[0].outcome()).isNotEqualTo(Outcome.FAILED);

    byte[] card20Person = card(converter, 20);
    ConversionRequest plans20 =
        new ConversionRequest(
            FormatId.CH_EMED,
            FormatId.CH_EMED_EPR,
            card20Person,
            ConversionOptions.defaults().withOutputForm(OutputForm.TREATMENT_PLANS));
    converter.convert(plans20);
    long plans = millis(() -> result[0] = converter.convert(plans20));
    LOG.info("20 treatments, CH EMED -> treatment-plans with validation: {} ms", plans);

    assertThat(typical).as("SC-005: 20 medications to PMLC").isLessThan(TYPICAL_LIMIT_MILLIS);
    assertThat(large).as("SC-005: 100 treatments").isLessThan(LARGE_LIMIT_MILLIS);
  }

  /**
   * The official PML {@code Utc6b} with {@code size} more statements, requests and observations.
   */
  private static byte[] largeList() {
    return largeList(Integer.getInteger("epr.listSize", 33));
  }

  private static byte[] largeList(int size) {
    Bundle bundle =
        ChEmedJson.parse(
            ch.emed.converter.epr.testsupport.EprFixtures.bytes(
                "official/Bundle-BundleUtc6bPml.json"));
    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
    Composition.SectionComponent section = composition.getSectionFirstRep();
    List<Bundle.BundleEntryComponent> entries =
        bundle.getEntry().stream()
            .filter(
                e ->
                    e.getResource() instanceof MedicationStatement
                        || e.getResource() instanceof org.hl7.fhir.r4.model.MedicationRequest
                        || e.getResource() instanceof org.hl7.fhir.r4.model.Observation)
            .toList();
    int copies = 0;
    for (Bundle.BundleEntryComponent entry : entries) {
      long existing =
          entries.stream()
              .filter(e -> e.getResource().fhirType().equals(entry.getResource().fhirType()))
              .count();
      for (int k = 0; k < size / existing; k++) {
        String url = "urn:uuid:" + UUID.nameUUIDFromBytes(("pml" + copies++).getBytes());
        bundle.addEntry().setFullUrl(url).setResource(entry.getResource().copy());
        section.addEntry(new Reference(url));
      }
    }
    return ChEmedJson.serialize(bundle);
  }

  @Test
  void aLargeMedicationListStaysWithinTheTimeLimit() {
    Converter converter = Converters.create(new HapiChEmedValidator());
    byte[] pml = largeList();
    ConversionRequest read =
        new ConversionRequest(
            FormatId.CH_EMED_EPR, FormatId.CH_EMED, pml, ConversionOptions.defaults());
    converter.convert(read);
    ConversionResult[] result = new ConversionResult[1];
    long toChEmed = millis(() -> result[0] = converter.convert(read));
    assertThat(result[0].outcome())
        .as(result[0].report().entries().toString())
        .isNotEqualTo(Outcome.FAILED);
    byte[] chEmed = result[0].output().orElseThrow();
    LOG.info(
        "medication list with {} entries, PML -> CH EMED with validation: {} ms",
        ChEmedJson.parse(pml).getEntry().size(),
        toChEmed);

    ConversionRequest write =
        new ConversionRequest(
            FormatId.CH_EMED, FormatId.CH_EMED_EPR, chEmed, ConversionOptions.defaults());
    long toEpr = millis(() -> result[0] = converter.convert(write));
    assertThat(result[0].outcome())
        .as(result[0].report().entries().toString())
        .isNotEqualTo(Outcome.FAILED);
    LOG.info("same list, CH EMED -> PML with validation: {} ms", toEpr);

    assertThat(toChEmed).as("SC-004: list to CH EMED").isLessThan(LARGE_LIMIT_MILLIS);
    assertThat(toEpr).as("SC-004: list to PML").isLessThan(LARGE_LIMIT_MILLIS);
  }
}
