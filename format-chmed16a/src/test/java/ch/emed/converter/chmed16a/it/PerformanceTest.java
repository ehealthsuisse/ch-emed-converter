package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.Chmed16aFormatModule;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.LargePlanGenerator;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.validation.HapiChEmedValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SC-005a/b (research R10): a plan with 100 medicaments. The conversion itself (mapping, PDF,
 * serialization) must take under two seconds; the official HL7 validation of the produced document
 * under thirty seconds with a warm context (measured 10-19 s depending on the machine, dominated by
 * the HL7 validator). 100 is the upper bound; SC-005c: a large realistic plan with 20 medicaments
 * is converted and validated in under five seconds. The cold start is logged separately.
 */
class PerformanceTest {

  private static final Logger LOG = LoggerFactory.getLogger(PerformanceTest.class);
  private static final long CONVERSION_LIMIT_MILLIS = 2_000;
  private static final long VALIDATION_LIMIT_MILLIS = 30_000;
  private static final long TYPICAL_LIMIT_MILLIS = 5_000;
  private static final int MEDICAMENTS = 100;
  private static final int TYPICAL_MEDICAMENTS = 20;
  private static final int RUNS = 3;

  private static long millis(Runnable action) {
    long start = System.nanoTime();
    action.run();
    return (System.nanoTime() - start) / 1_000_000;
  }

  private static <T> List<Long> repeat(Supplier<T> action) {
    List<Long> times = new ArrayList<>();
    for (int i = 0; i < RUNS; i++) {
      times.add(millis(action::get));
    }
    return times;
  }

  @Test
  void theConversionOfAHundredMedicamentsIsFastInBothDirections() {
    Chmed16aFormatModule module = new Chmed16aFormatModule();
    byte[] plan = LargePlanGenerator.plan(MEDICAMENTS);
    MappingContext toContext = new MappingContext(TestOptions.withAuthor(), new ReportCollector());
    Bundle card = module.toChEmed(plan, toContext);
    assertThat(card).as("warm-up").isNotNull();

    List<Long> forward =
        repeat(
            () ->
                module.toChEmed(
                    plan, new MappingContext(TestOptions.withAuthor(), new ReportCollector())));
    List<Long> backward =
        repeat(
            () ->
                module.fromChEmed(
                    ChEmedJson.parse(ChEmedJson.serialize(card)),
                    new MappingContext(ConversionOptions.defaults(), new ReportCollector())));
    LOG.info(
        "100 medicaments, conversion only: CHMED16A -> CH EMED {} ms, CH EMED -> CHMED16A {} ms",
        forward,
        backward);

    assertThat(forward).allSatisfy(t -> assertThat(t).isLessThan(CONVERSION_LIMIT_MILLIS));
    assertThat(backward).allSatisfy(t -> assertThat(t).isLessThan(CONVERSION_LIMIT_MILLIS));
  }

  @Test
  void theValidatedConversionIsMeasuredWithAWarmContextAndTheColdStartSeparately() {
    long coldStart =
        millis(
            () -> {
              Converter warmUp = Converters.create(new HapiChEmedValidator());
              warmUp.convert(
                  new ConversionRequest(
                      FormatId.CHMED16A,
                      FormatId.CH_EMED,
                      Fixtures.bytes("chmed16a/plan-basic.json"),
                      TestOptions.withAuthor()));
            });
    LOG.info("cold start (validation context and first conversion): {} ms", coldStart);

    Converter converter = Converters.create(new HapiChEmedValidator());
    byte[] plan = LargePlanGenerator.plan(MEDICAMENTS);
    ConversionRequest request =
        new ConversionRequest(FormatId.CHMED16A, FormatId.CH_EMED, plan, TestOptions.withAuthor());
    converter.convert(request);

    ConversionResult[] result = new ConversionResult[1];
    long validated = millis(() -> result[0] = converter.convert(request));
    LOG.info("100 medicaments, CHMED16A -> CH EMED with official validation: {} ms", validated);

    assertThat(result[0].outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(validated).as("SC-005b: validated conversion").isLessThan(VALIDATION_LIMIT_MILLIS);

    ConversionRequest typical =
        new ConversionRequest(
            FormatId.CHMED16A,
            FormatId.CH_EMED,
            LargePlanGenerator.plan(TYPICAL_MEDICAMENTS),
            TestOptions.withAuthor());
    converter.convert(typical);
    long typicalValidated = millis(() -> result[0] = converter.convert(typical));
    LOG.info(
        "20 medicaments, CHMED16A -> CH EMED with official validation: {} ms", typicalValidated);

    assertThat(result[0].outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(typicalValidated).as("SC-005c: typical plan").isLessThan(TYPICAL_LIMIT_MILLIS);
  }
}
