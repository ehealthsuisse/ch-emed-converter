package ch.emed.converter.rest.load;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * T042: load and isolation, with SC-002/SC-003 as revised on 2026-10-29 (aligned with feature 001
 * SC-005a/b; the HL7 output validation dominates the time). Measured on the warm service.
 *
 * <p>Measurements that led to the revision (16-core development machine): 100 medications 6-8 s end
 * to end, mapping alone 35-60 ms; 20 simultaneous requests p95 4-6 s, because the validation scales
 * poorly with concurrency (one conversion 0.3-0.5 s, 16 at once about 2 s each).
 */
class ConversionLoadTest extends AbstractRestTest {

  private static final int PARALLEL = 20;
  private static final long LIMIT_MILLIS = 15_000;
  private static final long MAX_OVERHEAD_MILLIS = 1_000;
  private static final long MAX_REJECTION_MILLIS = 1_000;

  private static final String PLAN =
      new String(
          RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-basic.json")),
          StandardCharsets.UTF_8);

  @BeforeEach
  void warmUp() throws Exception {
    MockHttpServletResponse response = post(patientPlan(99));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  /**
   * SC-003: no request returns another request's data; answered requests have p95 &lt; 15 s;
   * requests beyond the configured capacity are rejected at once with {@code SERVICE_BUSY}.
   */
  @Test
  void parallelRequestsStayIsolatedAndAreAnsweredInTime() throws Exception {
    List<Callable<long[]>> requests = new ArrayList<>();
    for (int i = 0; i < PARALLEL; i++) {
      int patient = i;
      requests.add(
          () -> {
            long start = System.nanoTime();
            MockHttpServletResponse response = post(patientPlan(patient));
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (response.getStatus() == 503) {
              assertThat(errorType(response)).isEqualTo("SERVICE_BUSY");
              assertThat(millis).as("rejection, ms").isLessThan(MAX_REJECTION_MILLIS);
              return new long[] {-1};
            }
            assertThat(response.getStatus()).isEqualTo(200);
            // isolation: the response contains this request's patient and no other one
            String document = output(response);
            assertThat(document).contains(family(patient));
            for (int other = 0; other < PARALLEL; other++) {
              if (other != patient) {
                assertThat(document).doesNotContain(family(other));
              }
            }
            return new long[] {millis};
          });
    }

    List<Long> answered = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(PARALLEL)) {
      for (Future<long[]> result : pool.invokeAll(requests)) {
        long millis = result.get()[0];
        if (millis >= 0) {
          answered.add(millis);
        }
      }
    }
    long[] durations = answered.stream().mapToLong(Long::longValue).sorted().toArray();
    assertThat(durations).as("answered requests").isNotEmpty();
    long p95 = durations[(int) Math.ceil(0.95 * durations.length) - 1];

    assertThat(p95)
        .as("p95 of %d answered requests, ms: %s", durations.length, Arrays.toString(durations))
        .isLessThan(LIMIT_MILLIS);
  }

  /**
   * SC-002: 100 medications in &lt; 15 s end to end, and the service adds less than 1 s to the
   * library's own time for the same input (fastest of two runs each, to damp the validator's
   * run-to-run variation).
   */
  @Test
  void planWithHundredMedicationsIsWithinLimitAndAddsNoOverhead() throws Exception {
    String plan = hundredMedications();
    byte[] bytes = plan.getBytes(StandardCharsets.UTF_8);
    ConversionOptions options = ConversionOptions.defaults().withAuthor(RestTestSupport.AUTHOR);

    long library = Long.MAX_VALUE;
    long service = Long.MAX_VALUE;
    String document = null;
    for (int run = 0; run < 2; run++) {
      long start = System.nanoTime();
      converter()
          .convert(new ConversionRequest(FormatId.CHMED16A, FormatId.CH_EMED, bytes, options));
      library = Math.min(library, (System.nanoTime() - start) / 1_000_000);

      start = System.nanoTime();
      MockHttpServletResponse response = post(plan);
      service = Math.min(service, (System.nanoTime() - start) / 1_000_000);
      assertThat(response.getStatus()).isEqualTo(200);
      document = output(response);
    }

    assertThat(document.split("\"resourceType\":\"MedicationStatement\"", -1)).hasSize(101);
    assertThat(service).as("100 medications through the service, ms").isLessThan(LIMIT_MILLIS);
    assertThat(service - library)
        .as("service %d ms vs. library %d ms", service, library)
        .isLessThan(MAX_OVERHEAD_MILLIS);
  }

  private static String hundredMedications() {
    ObjectNode plan = (ObjectNode) RestTestSupport.json(PLAN);
    ArrayNode medicaments = (ArrayNode) plan.get("Medicaments");
    List<JsonNode> templates = new ArrayList<>();
    medicaments.forEach(templates::add);
    medicaments.removeAll();
    for (int i = 0; i < 100; i++) {
      medicaments.add(templates.get(i % templates.size()).deepCopy());
    }
    return plan.toString();
  }

  /** CHMED16A→CH EMED through the service. */
  private MockHttpServletResponse post(String plan) throws Exception {
    return mvc.perform(
            convert(
                "chmed16a",
                "ch-emed",
                plan.getBytes(StandardCharsets.UTF_8),
                RestTestSupport.withAuthor("")))
        .andReturn()
        .getResponse();
  }

  private static String output(MockHttpServletResponse response) throws Exception {
    String output = RestTestSupport.json(response.getContentAsByteArray()).get("output").asText();
    return new String(Base64.getDecoder().decode(output), StandardCharsets.UTF_8);
  }

  private static String errorType(MockHttpServletResponse response) throws Exception {
    return RestTestSupport.json(response.getContentAsByteArray()).get("errorType").asText();
  }

  /** plan-basic with a synthetic patient of its own. */
  private static String patientPlan(int patient) {
    return PLAN.replace("\"LName\":\"Muster\"", "\"LName\":\"" + family(patient) + "\"")
        .replace("\"FName\":\"Hans\"", "\"FName\":\"Last" + patient + "\"");
  }

  private static String family(int patient) {
    return String.format("Lastpatient%02dx", patient);
  }
}
