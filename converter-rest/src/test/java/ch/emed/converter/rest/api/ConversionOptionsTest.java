package ch.emed.converter.rest.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import ch.emed.converter.rest.RestTestSupport.CliRun;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T017: patient identifier and author travel in the request body and fill what the CHMED16A source
 * lacks, exactly like the CLI options {@code --patient-id} and {@code --author} (FR-002, feature
 * 001 FR-023).
 */
class ConversionOptionsTest extends AbstractRestTest {

  private static final String PATIENT_ID =
      "\"patientIdentifier\":{\"system\":\"urn:oid:2.999.1\",\"value\":\"4711\"}";

  @Test
  void planWithoutPatientIdsNeedsPatientIdentifier() throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.restFixture("plan-no-ids.json"));

    byte[] response =
        mvc.perform(convert("chmed16a", "ch-emed", plan, RestTestSupport.withAuthor("")))
            .andExpect(status().is(422))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    JsonNode entries = RestTestSupport.json(response).get("report").get("entries");
    assertThat(StreamSupport.stream(entries.spliterator(), false))
        .anySatisfy(
            e -> {
              assertThat(e.get("code").asText()).isEqualTo("MISSING_REQUIRED_DATA");
              assertThat(e.get("path").asText()).startsWith("/Patient");
            });
  }

  @Test
  void patientIdentifierAndAuthorGiveSameDocumentAsCli(@TempDir Path temp) throws Exception {
    Path input = RestTestSupport.restFixture("plan-no-ids.json");

    byte[] response =
        mvc.perform(
                convert(
                    "chmed16a",
                    "ch-emed",
                    RestTestSupport.read(input),
                    RestTestSupport.withAuthor(PATIENT_ID)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    CliRun cli =
        RestTestSupport.cli(
            converter(),
            input,
            "chmed16a",
            "ch-emed",
            List.of(
                "--patient-id",
                "urn:oid:2.999.1|4711",
                "--author",
                RestTestSupport.authorFile(temp).toString()));

    byte[] output =
        Base64.getDecoder().decode(RestTestSupport.json(response).get("output").asText());
    assertThat(cli.exitCode()).isZero();
    assertThat(output).isEqualTo(cli.output());
    assertThat(new String(output, StandardCharsets.UTF_8)).contains("4711", "Praxis Beispiel");
  }
}
