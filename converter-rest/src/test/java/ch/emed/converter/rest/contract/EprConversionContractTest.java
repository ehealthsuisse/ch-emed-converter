package ch.emed.converter.rest.contract;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.TreatmentPlanReference;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import java.util.List;
import org.hamcrest.Matchers;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T047 and T067 (feature 004, US5, contracts/rest-api.md), T023 (feature 005): CH EMED EPR through
 * REST, OpenAPI 1.2.1.
 */
class EprConversionContractTest extends AbstractRestTest {

  private static final String PMLC = "Bundle-DocumentPmlcCARAPMP004AxapharmParacetamol.json";
  private static final String PRE = "Bundle-DocumentPreParacetamolAxapharmCARAPMP004.json";
  private static final String DOC = "urn:uuid:11111111-1111-4111-8111-111111111111";
  private static final String MS = "urn:uuid:22222222-2222-4222-8222-222222222222";
  private static final String TREATMENT_PLAN =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-treatmentplan";

  private static byte[] epr(String name) {
    return RestTestSupport.read(RestTestSupport.EPR.resolve(name));
  }

  private static byte[] chEmedCard() {
    return RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-card-example.json"));
  }

  /** The CH EMED prescription example without its treatment-plan reference. */
  private static byte[] prescriptionWithoutReference() {
    Bundle bundle =
        ChEmedJson.parse(
            RestTestSupport.read(
                RestTestSupport.CH_EMED.resolve("medication-prescription-example.json")));
    bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(DomainResource.class::isInstance)
        .map(DomainResource.class::cast)
        .forEach(r -> r.getExtension().removeIf(e -> e.getUrl().equals(TREATMENT_PLAN)));
    return ChEmedJson.serialize(bundle);
  }

  private static JsonNode options(String json) {
    return RestTestSupport.json(json);
  }

  private static String references(int n) {
    StringBuilder sb = new StringBuilder("{\"treatmentPlanReferences\":[");
    for (int i = 0; i < n; i++) {
      sb.append(i == 0 ? "" : ",")
          .append("{\"documentId\":\"")
          .append(DOC)
          .append("\",\"statementId\":\"")
          .append(MS)
          .append("\"}");
    }
    return sb.append("]}").toString();
  }

  private ConversionResult library(byte[] input, ConversionOptions options) {
    return converter()
        .convert(new ConversionRequest(FormatId.CH_EMED, FormatId.CH_EMED_EPR, input, options));
  }

  private static byte[] output(MvcResult result) throws Exception {
    JsonNode body = RestTestSupport.json(result.getResponse().getContentAsByteArray());
    return Base64.getDecoder().decode(body.get("output").asText());
  }

  @Test
  void eprToChEmedConformsToContract() throws Exception {
    mvc.perform(convert("ch-emed-epr", "ch-emed", epr(PMLC), null))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.outputMediaType").value("application/fhir+json"));
  }

  @Test
  void chEmedToEprConformsToContract() throws Exception {
    byte[] chEmed =
        converter()
            .convert(
                new ConversionRequest(
                    FormatId.CH_EMED_EPR, FormatId.CH_EMED, epr(PRE), ConversionOptions.defaults()))
            .output()
            .orElseThrow();

    mvc.perform(convert("ch-emed", "ch-emed-epr", chEmed, null))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.outputMediaType").value("application/fhir+json"));
  }

  @Test
  void treatmentPlansConformToContract() throws Exception {
    MvcResult result =
        mvc.perform(
                convert(
                    "ch-emed",
                    "ch-emed-epr",
                    chEmedCard(),
                    options("{\"outputForm\":\"treatment-plans\"}")))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
            .andExpect(jsonPath("$.outputMediaType").value("application/fhir+json"))
            .andReturn();

    assertThat(ChEmedJson.parse(output(result)).getType()).isEqualTo(Bundle.BundleType.COLLECTION);
  }

  @Test
  void aPairWithChmed16aIsNotFoundAndNamesThePathThroughChEmed() throws Exception {
    mvc.perform(convert("chmed16a", "ch-emed-epr", "{}".getBytes(), null))
        .andExpect(status().isNotFound())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.errorType").value("UNSUPPORTED_CONVERSION"))
        .andExpect(jsonPath("$.supportedConversions").isArray())
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("ch-emed")));
    mvc.perform(convert("ch-emed-epr", "chmed16a", epr(PMLC), null))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorType").value("UNSUPPORTED_CONVERSION"));
  }

  @Test
  void treatmentPlansForAnotherTargetAreMalformed() throws Exception {
    mvc.perform(
            convert(
                "ch-emed",
                "chmed16a",
                chEmedCard(),
                options("{\"outputForm\":\"treatment-plans\"}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorType").value("MALFORMED_REQUEST"));
  }

  @Test
  void invalidEprInputAndUnsupportedDocumentTypesAreInvalidInput() throws Exception {
    mvc.perform(convert("ch-emed-epr", "ch-emed", chEmedCard(), null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.errorType").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.report").exists());
    mvc.perform(
            convert(
                "ch-emed-epr",
                "ch-emed",
                RestTestSupport.read(
                    RestTestSupport.EPR_SYNTHETIC.resolve("Bundle-UnknownDocumentType.json")),
                null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errorType").value("INVALID_INPUT"));
  }

  @Test
  void missingRequiredDataAndNoTreatmentsFailTheConversion() throws Exception {
    // a card with a person as author does not become a PMLC (research F8)
    mvc.perform(convert("ch-emed", "ch-emed-epr", chEmedCard(), null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.errorType").value("CONVERSION_FAILED"))
        .andExpect(
            jsonPath("$.report.entries[*].code").value(Matchers.hasItem("MISSING_REQUIRED_DATA")));

    byte[] emptyCard =
        converter()
            .convert(
                new ConversionRequest(
                    FormatId.CH_EMED_EPR,
                    FormatId.CH_EMED,
                    epr("Bundle-DocumentUCSF1CARAPMP004PMLCEmpty.json"),
                    ConversionOptions.defaults()))
            .output()
            .orElseThrow();
    mvc.perform(
            convert(
                "ch-emed",
                "ch-emed-epr",
                emptyCard,
                options("{\"outputForm\":\"treatment-plans\"}")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errorType").value("CONVERSION_FAILED"))
        .andExpect(jsonPath("$.report.entries[*].code").value(Matchers.hasItem("NO_TREATMENTS")));
  }

  @Test
  void treatmentPlanReferencesGiveTheSamePreAsTheLibrary() throws Exception {
    byte[] prescription = prescriptionWithoutReference();
    byte[] expected =
        library(
                prescription,
                ConversionOptions.defaults()
                    .withTreatmentPlanReferences(List.of(new TreatmentPlanReference(DOC, MS))))
            .output()
            .orElseThrow();

    MvcResult result =
        mvc.perform(convert("ch-emed", "ch-emed-epr", prescription, options(references(1))))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
            .andReturn();

    assertThat(output(result)).isEqualTo(expected);
  }

  @Test
  void malformedReferencesAreRejectedWithoutEchoingThem() throws Exception {
    mvc.perform(
            convert(
                "ch-emed",
                "ch-emed-epr",
                prescriptionWithoutReference(),
                options(
                    "{\"treatmentPlanReferences\":[{\"documentId\":\"secret-doc\","
                        + "\"statementId\":\""
                        + MS
                        + "\"}]}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorType").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("treatmentPlanReferences")))
        .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("secret-doc"))));
  }

  @Test
  void aWrongNumberOfReferencesFailsTheConversion() throws Exception {
    mvc.perform(
            convert(
                "ch-emed", "ch-emed-epr", prescriptionWithoutReference(), options(references(2))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.errorType").value("CONVERSION_FAILED"))
        .andExpect(
            jsonPath("$.report.entries[*].code")
                .value(Matchers.hasItem("TREATMENT_PLAN_REFERENCES_MISMATCH")));
  }

  @Test
  void aSuppliedOriginalRepresentationIsCarriedAndInvalidBase64Rejected() throws Exception {
    byte[] prescription = prescriptionWithoutReference();
    byte[] pdfA =
        OriginalRepresentationRenderer.renderPdfA(
            ChEmedJson.parse(prescription), new ReportCollector());
    String body =
        references(1)
            .replace(
                "]}",
                "],\"originalRepresentation\":\""
                    + Base64.getEncoder().encodeToString(pdfA)
                    + "\"}");

    MvcResult result =
        mvc.perform(convert("ch-emed", "ch-emed-epr", prescription, options(body)))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
            .andReturn();

    assertThat(
            ChEmedJson.parse(output(result)).getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Binary.class::isInstance)
                .map(b -> ((Binary) b).getData()))
        .singleElement()
        .isEqualTo(pdfA);

    mvc.perform(
            convert(
                "ch-emed",
                "ch-emed-epr",
                prescription,
                options("{\"originalRepresentation\":\"%%% not base64 %%%\"}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorType").value("MALFORMED_REQUEST"));
  }

  /** Feature 005: DIS, PADV and PML in both directions, and a dispense with a reference. */
  @Test
  void disPadvPmlConformToContract() throws Exception {
    for (String name :
        List.of(
            "Bundle-DocumentDis1.json",
            "Bundle-DocumentPadvChangeParacetamolCARAPMP004.json",
            "Bundle-BundlePml1.json")) {
      MvcResult read =
          mvc.perform(convert("ch-emed-epr", "ch-emed", epr(name), null))
              .andExpect(status().isOk())
              .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
              .andReturn();
      mvc.perform(convert("ch-emed", "ch-emed-epr", output(read), null))
          .andExpect(status().isOk())
          .andExpect(openApi().isValid(RestTestSupport.OPENAPI));
    }
    byte[] dispense =
        RestTestSupport.read(
            RestTestSupport.EPR_SYNTHETIC.resolve("Bundle-DispenseWithoutTreatmentPlan.json"));
    mvc.perform(convert("ch-emed", "ch-emed-epr", dispense, null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errorType").value("CONVERSION_FAILED"));
    mvc.perform(convert("ch-emed", "ch-emed-epr", dispense, options(references(1))))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI));
  }

  /** Feature 005, M-EPR-04: a contradiction is a warning in the report of a 200 response. */
  @Test
  void contradictingReferencesAreAWarning() throws Exception {
    Bundle dis = ChEmedJson.parse(epr("Bundle-DocumentDis1.json"));
    org.hl7.fhir.r4.model.MedicationDispense dispense =
        dis.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(org.hl7.fhir.r4.model.MedicationDispense.class::isInstance)
            .map(org.hl7.fhir.r4.model.MedicationDispense.class::cast)
            .findFirst()
            .orElseThrow();
    org.hl7.fhir.r4.model.Extension plan = dispense.getExtensionByUrl(TREATMENT_PLAN);
    org.hl7.fhir.r4.model.Extension prescription =
        dispense.getExtensionByUrl(
            "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-prescription");
    prescription.getExtensionByUrl("id").setValue(plan.getExtensionByUrl("id").getValue().copy());
    ((org.hl7.fhir.r4.model.Identifier)
            prescription.getExtensionByUrl("externalDocumentId").getValue())
        .setValue(DOC);

    mvc.perform(convert("ch-emed-epr", "ch-emed", ChEmedJson.serialize(dis), null))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.outcome").value("SUCCESS_WITH_WARNINGS"))
        .andExpect(
            jsonPath("$.report.entries[*].code").value(Matchers.hasItem("REFERENCE_INCONSISTENT")));
  }
}
