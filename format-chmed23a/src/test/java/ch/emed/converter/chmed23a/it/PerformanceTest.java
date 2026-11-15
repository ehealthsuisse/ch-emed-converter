package ch.emed.converter.chmed23a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.testsupport.Corpus;
import ch.emed.converter.chmed23a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * T070, SC-005 (research R14): a plan with 20 medicaments of mixed posologies, taken from the
 * corpus, converted to CH EMED and back including validation, within the limit of feature 001
 * (SC-005b, 30 s warm). Run on 4 vCPUs ({@code tools/remote-build.sh load}).
 */
class PerformanceTest {

  private static final Logger LOG = LoggerFactory.getLogger(PerformanceTest.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int MEDICAMENTS = 20;
  private static final long LIMIT_MILLIS = 30_000;
  private static final int RUNS = 3;

  /** Example 1 with the first medicament of the first 20 plan fixtures of the corpus. */
  static byte[] plan() throws Exception {
    ObjectNode root =
        (ObjectNode) MAPPER.readTree(Fixtures.bytes("chmed23a/official/Example1.json"));
    ArrayNode meds = root.putArray("meds");
    for (String fixture : Corpus.plans()) {
      JsonNode med = MAPPER.readTree(Fixtures.bytes(fixture)).path("meds").path(0);
      if (!med.isMissingNode()) {
        meds.add(med);
      }
      if (meds.size() == MEDICAMENTS) {
        break;
      }
    }
    assertThat(meds).hasSize(MEDICAMENTS);
    return MAPPER.writeValueAsBytes(root);
  }

  @Test
  void twentyMedicamentsToChEmedAndBackWithValidation() throws Exception {
    Converter converter = Converters.create(ChEmedAssertions.validator());
    byte[] plan = plan();
    roundTrip(converter, plan); // warm-up
    List<Long> times = new ArrayList<>();
    for (int i = 0; i < RUNS; i++) {
      long start = System.nanoTime();
      roundTrip(converter, plan);
      times.add((System.nanoTime() - start) / 1_000_000);
    }
    LOG.info("20 medicaments, CHMED23A -> CH EMED -> CHMED23A with validation: {} ms", times);
    assertThat(times).allSatisfy(t -> assertThat(t).isLessThan(LIMIT_MILLIS));
  }

  private static void roundTrip(Converter converter, byte[] plan) {
    ConversionResult forward =
        converter.convert(
            new ConversionRequest(
                FormatId.CHMED23A, FormatId.CH_EMED, plan, TestOptions.withAuthorAndPatientId()));
    byte[] card = forward.output().orElseThrow(() -> new AssertionError(forward.report()));
    ConversionResult back =
        converter.convert(
            new ConversionRequest(
                FormatId.CH_EMED, FormatId.CHMED23A, card, TestOptions.withAuthorAndPatientId()));
    assertThat(back.output()).isPresent();
  }
}
