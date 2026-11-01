package ch.emed.converter.rest.load;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * T042: load and isolation (SC-002, SC-003). A large realistic plan has {@value
 * #TYPICAL_MEDICATIONS} different medications; {@value #EXTREME_MEDICATIONS} is the upper bound,
 * which has to work rather than be fast. Measured on the warm service; the HL7 output validation
 * dominates the time.
 */
class ConversionLoadTest extends AbstractRestTest {

  private static final Logger LOG = LoggerFactory.getLogger(ConversionLoadTest.class);

  private static final int TYPICAL_MEDICATIONS = 20;
  private static final int EXTREME_MEDICATIONS = 100;
  private static final int PARALLEL = 20;
  private static final long TYPICAL_LIMIT_MILLIS = 5_000;
  private static final long EXTREME_LIMIT_MILLIS = 30_000;
  private static final long PARALLEL_LIMIT_MILLIS = 40_000;
  private static final long MAX_OVERHEAD_MILLIS = 1_000;
  private static final long MAX_REJECTION_MILLIS = 1_000;

  @BeforeEach
  void warmUp() throws Exception {
    assertThat(post(PlanGenerator.plan(TYPICAL_MEDICATIONS, "Warmup")).getStatus()).isEqualTo(200);
  }

  /** SC-002: a large realistic plan in &lt; 5 s end to end (median of three). */
  @Test
  void typicalPlanIsFast() throws Exception {
    byte[] plan = PlanGenerator.plan(TYPICAL_MEDICATIONS, "Typical");

    long[] durations = new long[3];
    for (int run = 0; run < durations.length; run++) {
      long start = System.nanoTime();
      MockHttpServletResponse response = post(plan);
      durations[run] = (System.nanoTime() - start) / 1_000_000;
      assertThat(response.getStatus()).isEqualTo(200);
    }
    Arrays.sort(durations);
    LOG.info("{} medications through the service: {} ms", TYPICAL_MEDICATIONS, durations);

    assertThat(durations[1]).as("median, ms").isLessThan(TYPICAL_LIMIT_MILLIS);
  }

  /**
   * SC-003: {@value #PARALLEL} simultaneous requests with large realistic plans return no data of
   * another request; answered requests have p95 &lt; 40 s; requests beyond the configured capacity
   * are rejected at once with {@code SERVICE_BUSY}.
   */
  @Test
  void parallelRequestsStayIsolatedAndAreAnsweredInTime() throws Exception {
    List<Callable<Long>> requests = new ArrayList<>();
    for (int i = 0; i < PARALLEL; i++) {
      int patient = i;
      requests.add(
          () -> {
            byte[] plan = PlanGenerator.plan(TYPICAL_MEDICATIONS, family(patient));
            long start = System.nanoTime();
            MockHttpServletResponse response = post(plan);
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (response.getStatus() == 503) {
              assertThat(errorType(response)).isEqualTo("SERVICE_BUSY");
              assertThat(millis).as("rejection, ms").isLessThan(MAX_REJECTION_MILLIS);
              return -1L;
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
            return millis;
          });
    }

    List<Long> answered = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(PARALLEL)) {
      for (Future<Long> result : pool.invokeAll(requests)) {
        if (result.get() >= 0) {
          answered.add(result.get());
        }
      }
    }
    long[] durations = answered.stream().mapToLong(Long::longValue).sorted().toArray();
    LOG.info("{} parallel requests, answered in ms: {}", PARALLEL, durations);
    assertThat(durations).as("answered requests").isNotEmpty();
    long p95 = durations[(int) Math.ceil(0.95 * durations.length) - 1];

    assertThat(p95).as("p95, ms: %s", Arrays.toString(durations)).isLessThan(PARALLEL_LIMIT_MILLIS);
  }

  /**
   * SC-002: the upper bound of {@value #EXTREME_MEDICATIONS} different medications works in &lt; 30
   * s end to end, and the service adds less than 1 s to the library's own time for the same input
   * (fastest of two runs each, to damp the validator's run-to-run variation).
   */
  @Test
  void extremePlanWorksAndTheServiceAddsNoOverhead() throws Exception {
    byte[] plan = PlanGenerator.plan(EXTREME_MEDICATIONS, "Extreme");
    ConversionOptions options = ConversionOptions.defaults().withAuthor(RestTestSupport.AUTHOR);

    long library = Long.MAX_VALUE;
    long service = Long.MAX_VALUE;
    String document = null;
    for (int run = 0; run < 2; run++) {
      long start = System.nanoTime();
      converter()
          .convert(new ConversionRequest(FormatId.CHMED16A, FormatId.CH_EMED, plan, options));
      library = Math.min(library, (System.nanoTime() - start) / 1_000_000);

      start = System.nanoTime();
      MockHttpServletResponse response = post(plan);
      service = Math.min(service, (System.nanoTime() - start) / 1_000_000);
      assertThat(response.getStatus()).isEqualTo(200);
      document = output(response);
    }
    LOG.info("{} medications: service {} ms, library {} ms", EXTREME_MEDICATIONS, service, library);

    assertThat(document.split("\"resourceType\":\"MedicationStatement\"", -1))
        .hasSize(EXTREME_MEDICATIONS + 1);
    assertThat(service).as("through the service, ms").isLessThan(EXTREME_LIMIT_MILLIS);
    assertThat(service - library)
        .as("service %d ms vs. library %d ms", service, library)
        .isLessThan(MAX_OVERHEAD_MILLIS);
  }

  /** CHMED16A→CH EMED through the service. */
  private MockHttpServletResponse post(byte[] plan) throws Exception {
    return mvc.perform(convert("chmed16a", "ch-emed", plan, RestTestSupport.withAuthor("")))
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

  private static String family(int patient) {
    return String.format("Lastpatient%02dx", patient);
  }
}
