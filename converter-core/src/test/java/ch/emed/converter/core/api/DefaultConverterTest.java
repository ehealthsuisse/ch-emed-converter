package ch.emed.converter.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.core.spi.ChEmedValidator;
import ch.emed.converter.core.spi.FormatModule;
import ch.emed.converter.core.spi.FormatRegistry;
import ch.emed.converter.core.spi.MappingContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.Test;

/** Behavioural contract of contracts/library-api.md, verified with fake modules. */
class DefaultConverterTest {

  private static final FormatId FAKE_A = FormatId.of("FAKE_A");
  private static final FormatId FAKE_B = FormatId.of("FAKE_B");

  /** Fake format: input "bad" fails, anything else becomes a tiny document Bundle. */
  private static final class FakeModule implements FormatModule {
    private final FormatId format;
    final List<Optional<ChEmedValidator>> seenValidators = new ArrayList<>();

    FakeModule(FormatId format) {
      this.format = format;
    }

    @Override
    public FormatId format() {
      return format;
    }

    @Override
    public String pinnedSpecification() {
      return format.id() + "-spec#1";
    }

    @Override
    public Bundle toChEmed(byte[] input, MappingContext ctx) {
      seenValidators.add(ctx.validator());
      String text = new String(input, StandardCharsets.UTF_8);
      if (text.equals("bad")) {
        ctx.report()
            .add(
                ReportEntry.of(
                    Severity.ERROR, ReasonCode.PARSE_ERROR, Phase.PARSE, "/", "not parseable"));
        return null;
      }
      ctx.report()
          .add(
              ReportEntry.of(
                  Severity.WARNING,
                  ReasonCode.UNMAPPABLE_ELEMENT,
                  Phase.MAP,
                  "/" + format.id(),
                  "leg " + format.id()));
      Bundle bundle = new Bundle();
      bundle.setType(Bundle.BundleType.DOCUMENT);
      bundle.setIdentifier(
          new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + text));
      return bundle;
    }

    @Override
    public byte[] fromChEmed(Bundle document, MappingContext ctx) {
      seenValidators.add(ctx.validator());
      ctx.report()
          .add(
              ReportEntry.of(
                  Severity.INFORMATION,
                  ReasonCode.UNKNOWN_FIELD_IGNORED,
                  Phase.MAP,
                  "Bundle",
                  "leg to " + format.id()));
      return ("converted:" + document.getIdentifier().getValue()).getBytes(StandardCharsets.UTF_8);
    }
  }

  /** Fake validator returning configurable entries and recording the phases it was called for. */
  private static final class FakeValidator implements ChEmedValidator {
    final List<ReportEntry> toReturn = new ArrayList<>();
    final List<Phase> calls = new ArrayList<>();

    @Override
    public List<ReportEntry> validate(Bundle bundle, Phase phase) {
      calls.add(phase);
      return toReturn;
    }

    @Override
    public String pinnedSpecification() {
      return "ch.fhir.ig.ch-emed#6.0.0";
    }
  }

  private final FakeValidator validator = new FakeValidator();
  private final FakeModule moduleA = new FakeModule(FAKE_A);
  private final FakeModule moduleB = new FakeModule(FAKE_B);
  private final Converter converter =
      new DefaultConverter(FormatRegistry.of(List.of(moduleA, moduleB)), validator);

  private static ConversionRequest request(FormatId from, FormatId to, String input) {
    return new ConversionRequest(
        from, to, input.getBytes(StandardCharsets.UTF_8), ConversionOptions.defaults());
  }

  @Test
  void convertsToChEmedAndValidatesOutput() {
    ConversionResult result = converter.convert(request(FAKE_A, FormatId.CH_EMED, "1234"));

    assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS_WITH_WARNINGS);
    assertThat(result.output()).isPresent();
    Bundle bundle = ChEmedJson.parse(result.output().get());
    assertThat(bundle.getIdentifier().getValue()).isEqualTo("urn:uuid:1234");
    assertThat(validator.calls).containsExactly(Phase.VALIDATE_OUTPUT);
  }

  @Test
  void invalidInputFailsWithoutThrowingAndWithoutOutput() {
    assertThatCode(() -> converter.convert(request(FAKE_A, FormatId.CH_EMED, "bad")))
        .doesNotThrowAnyException();

    ConversionResult result = converter.convert(request(FAKE_A, FormatId.CH_EMED, "bad"));
    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .contains(ReasonCode.PARSE_ERROR);
  }

  @Test
  void unsupportedPairFailsWithUnsupportedConversion() {
    ConversionResult sameFormat = converter.convert(request(FAKE_A, FAKE_A, "1"));
    ConversionResult unknownFormat =
        converter.convert(request(FormatId.of("UNKNOWN"), FormatId.CH_EMED, "1"));

    for (ConversionResult result : List.of(sameFormat, unknownFormat)) {
      assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
      assertThat(result.output()).isEmpty();
      assertThat(result.report().entries())
          .extracting(ReportEntry::code)
          .containsExactly(ReasonCode.UNSUPPORTED_CONVERSION);
    }
  }

  /** Feature 004, FR-004: pairs of two formats other than CH EMED are refused, not composed. */
  @Test
  void pairWithoutChEmedIsRefusedWithThePathThroughChEmed() {
    ConversionResult result = converter.convert(request(FAKE_A, FAKE_B, "42"));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .containsExactly(ReasonCode.UNSUPPORTED_CONVERSION);
    assertThat(result.report().entries().get(0).message())
        .contains("FAKE_A -> CH_EMED, ", "CH_EMED -> FAKE_B")
        .contains("convert through ch-emed: fake-a -> ch-emed -> fake-b");
    assertThat(moduleA.seenValidators).isEmpty();
    assertThat(converter.supportedConversions())
        .doesNotContain(new ConversionPair(FAKE_A, FAKE_B), new ConversionPair(FAKE_B, FAKE_A));
  }

  /** Feature 004, R3: format modules reach the converter's validator through the context. */
  @Test
  void mappingContextCarriesTheValidator() {
    converter.convert(request(FAKE_A, FormatId.CH_EMED, "1"));
    byte[] chEmed =
        converter.convert(request(FAKE_A, FormatId.CH_EMED, "2")).output().orElseThrow();
    converter.convert(
        new ConversionRequest(FormatId.CH_EMED, FAKE_B, chEmed, ConversionOptions.defaults()));

    assertThat(moduleA.seenValidators).isNotEmpty().allMatch(v -> v.orElseThrow() == validator);
    assertThat(moduleB.seenValidators).hasSize(1).allMatch(v -> v.orElseThrow() == validator);

    FakeModule alone = new FakeModule(FAKE_A);
    Converter withoutValidator = new DefaultConverter(FormatRegistry.of(List.of(alone)), null);
    withoutValidator.convert(
        new ConversionRequest(
            FAKE_A,
            FormatId.CH_EMED,
            "3".getBytes(StandardCharsets.UTF_8),
            ConversionOptions.defaults().withValidateOutput(false)));
    assertThat(alone.seenValidators).containsExactly(Optional.empty());
  }

  /** Feature 004: treatment-plans is refused for a target that does not offer it. */
  @Test
  void treatmentPlansForATargetWithoutItIsAnUnsupportedOutputForm() {
    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FAKE_A,
                FormatId.CH_EMED,
                "1".getBytes(StandardCharsets.UTF_8),
                ConversionOptions.defaults().withOutputForm(OutputForm.TREATMENT_PLANS)));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .containsExactly(ReasonCode.UNSUPPORTED_OUTPUT_FORM);
  }

  @Test
  void chEmedInputIsParsedAndValidatedAsInput() {
    byte[] chEmed =
        converter.convert(request(FAKE_A, FormatId.CH_EMED, "77")).output().orElseThrow();
    validator.calls.clear();

    ConversionResult result =
        converter.convert(
            new ConversionRequest(FormatId.CH_EMED, FAKE_B, chEmed, ConversionOptions.defaults()));

    assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(validator.calls).containsExactly(Phase.VALIDATE_INPUT);
  }

  @Test
  void unparseableChEmedInputIsParseError() {
    ConversionResult result = converter.convert(request(FormatId.CH_EMED, FAKE_B, "{not fhir"));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .containsExactly(ReasonCode.PARSE_ERROR);
  }

  @Test
  void outputValidationErrorFailsTheConversion() {
    validator.toReturn.add(
        ReportEntry.of(
            Severity.ERROR,
            ReasonCode.FHIR_VALIDATION,
            Phase.VALIDATE_OUTPUT,
            "Bundle.entry[0]",
            "profile violation"));

    ConversionResult result = converter.convert(request(FAKE_A, FormatId.CH_EMED, "9"));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
  }

  @Test
  void reportCarriesPinnedSpecificationsAndFormats() {
    ConversionResult result = converter.convert(request(FAKE_A, FormatId.CH_EMED, "5"));

    assertThat(result.report().source()).isEqualTo(FAKE_A);
    assertThat(result.report().target()).isEqualTo(FormatId.CH_EMED);
    assertThat(result.report().specifications())
        .containsEntry("CH_EMED", "ch.fhir.ig.ch-emed#6.0.0")
        .containsEntry("FAKE_A", "FAKE_A-spec#1");
    assertThat(converter.supportedConversions())
        .contains(
            new ConversionPair(FAKE_A, FormatId.CH_EMED),
            new ConversionPair(FormatId.CH_EMED, FAKE_B));
  }

  @Test
  void validateOutputWithoutValidatorIsAProgrammingError() {
    Converter withoutValidator =
        new DefaultConverter(FormatRegistry.of(List.of(new FakeModule(FAKE_A))), null);

    assertThatThrownBy(() -> withoutValidator.convert(request(FAKE_A, FormatId.CH_EMED, "1")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void nullRequestIsAProgrammingError() {
    assertThatThrownBy(() -> converter.convert(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void strictModeFailsOnClinicalLossAndSuppressesTheOutput() {
    validator.toReturn.add(
        ReportEntry.of(
                Severity.WARNING,
                ReasonCode.UNMAPPABLE_ELEMENT,
                Phase.VALIDATE_OUTPUT,
                "Bundle.entry[0]",
                "element not represented")
            .withClinicalLoss(true));
    byte[] input = "5".getBytes(StandardCharsets.UTF_8);

    ConversionResult lenient =
        converter.convert(
            new ConversionRequest(FAKE_A, FormatId.CH_EMED, input, ConversionOptions.defaults()));
    ConversionResult strict =
        converter.convert(
            new ConversionRequest(
                FAKE_A, FormatId.CH_EMED, input, ConversionOptions.defaults().withStrict(true)));

    assertThat(lenient.outcome()).isEqualTo(Outcome.SUCCESS_WITH_WARNINGS);
    assertThat(lenient.output()).isPresent();
    assertThat(strict.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(strict.output()).isEmpty();
    assertThat(strict.report().entries()).filteredOn(ReportEntry::clinicalLoss).hasSize(1);
  }
}
