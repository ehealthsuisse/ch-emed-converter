package ch.emed.converter.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.FormatModule;
import ch.emed.converter.core.spi.FormatRegistry;
import ch.emed.converter.core.spi.MappingContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.Test;

/** T007 (feature 003): output forms, signature data and format capabilities in the pipeline. */
class OutputFormPipelineTest {

  private static final FormatId PLAIN = FormatId.of("PLAIN");
  private static final FormatId QR = FormatId.of("QR");
  private static final SignatureData SIGNATURE = new SignatureData("i", "1767225600", "3066");

  /** Module with configurable output forms; input "signed" sets signature data, "bad" fails. */
  private static final class StubModule implements FormatModule {
    private final FormatId format;
    private final List<OutputForm> outputForms;
    private int fromChEmedCalls;

    StubModule(FormatId format, List<OutputForm> outputForms) {
      this.format = format;
      this.outputForms = outputForms;
    }

    @Override
    public FormatId format() {
      return format;
    }

    @Override
    public String pinnedSpecification() {
      return format.id() + "#1";
    }

    @Override
    public List<String> inputForms() {
      return List.of("json", "image");
    }

    @Override
    public List<OutputForm> outputForms() {
      return outputForms;
    }

    @Override
    public Bundle toChEmed(byte[] input, MappingContext ctx) {
      String text = new String(input, StandardCharsets.UTF_8);
      if (text.startsWith("signed")) {
        ctx.setSignature(SIGNATURE);
      }
      if (text.endsWith("bad")) {
        ctx.report()
            .add(ReportEntry.error(ReasonCode.PARSE_ERROR, Phase.PARSE, "/", "not parseable"));
        return null;
      }
      Bundle bundle = new Bundle();
      bundle.setType(Bundle.BundleType.DOCUMENT);
      bundle.setIdentifier(new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:x"));
      return bundle;
    }

    @Override
    public byte[] fromChEmed(Bundle document, MappingContext ctx) {
      fromChEmedCalls++;
      return ctx.options().outputForm().name().getBytes(StandardCharsets.UTF_8);
    }
  }

  private final StubModule plain = new StubModule(PLAIN, List.of(OutputForm.DOCUMENT));
  private final StubModule qr =
      new StubModule(QR, List.of(OutputForm.DOCUMENT, OutputForm.QR_PAYLOAD, OutputForm.QR_IMAGE));
  private final Converter converter =
      new DefaultConverter(FormatRegistry.of(List.of(qr, plain)), null);

  private static ConversionRequest request(
      FormatId from, FormatId to, String input, OutputForm form) {
    return new ConversionRequest(
        from,
        to,
        input.getBytes(StandardCharsets.UTF_8),
        ConversionOptions.defaults().withValidateOutput(false).withOutputForm(form));
  }

  @Test
  void outputFormNotOfferedByTargetFailsBeforeMapping() {
    ConversionResult result = converter.convert(request(QR, PLAIN, "x", OutputForm.QR_PAYLOAD));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_OUTPUT_FORM);
              assertThat(e.phase()).isEqualTo(Phase.PARSE);
              assertThat(e.message()).contains("qr-payload").contains("PLAIN");
            });
    assertThat(plain.fromChEmedCalls).isZero();
  }

  @Test
  void chEmedTargetOffersDocumentOnly() {
    ConversionResult result =
        converter.convert(request(QR, FormatId.CH_EMED, "x", OutputForm.QR_IMAGE));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .containsExactly(ReasonCode.UNSUPPORTED_OUTPUT_FORM);
  }

  @Test
  void offeredOutputFormReachesTheModule() {
    ConversionResult result = converter.convert(request(PLAIN, QR, "x", OutputForm.QR_IMAGE));

    assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(new String(result.output().orElseThrow(), StandardCharsets.UTF_8))
        .isEqualTo("QR_IMAGE");
  }

  @Test
  void signatureIsReturnedOnSuccess() {
    ConversionResult result =
        converter.convert(request(QR, FormatId.CH_EMED, "signed", OutputForm.DOCUMENT));

    assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(result.signature()).contains(SIGNATURE);
  }

  @Test
  void signatureIsDroppedOnFailure() {
    ConversionResult result =
        converter.convert(request(QR, FormatId.CH_EMED, "signed-bad", OutputForm.DOCUMENT));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.signature()).isEmpty();
  }

  @Test
  void signatureIsNeverInTheReport() {
    ConversionResult result =
        converter.convert(request(QR, FormatId.CH_EMED, "signed", OutputForm.DOCUMENT));

    assertThat(result.report().entries())
        .extracting(ReportEntry::message)
        .noneMatch(m -> m.contains("1767225600") || m.contains("3066"));
  }

  @Test
  void formatsListInputAndOutputFormsSortedById() {
    assertThat(converter.formats())
        .extracting(f -> f.format().id())
        .containsExactly("CH_EMED", "PLAIN", "QR");
    assertThat(converter.formats().get(0))
        .isEqualTo(new FormatInfo(FormatId.CH_EMED, List.of("json"), List.of(OutputForm.DOCUMENT)));
    assertThat(converter.formats().get(2).inputForms()).containsExactly("json", "image");
    assertThat(converter.formats().get(2).outputForms())
        .containsExactly(OutputForm.DOCUMENT, OutputForm.QR_PAYLOAD, OutputForm.QR_IMAGE);
  }

  @Test
  void unknownTargetVersionListsSupportedConversions() {
    // FR-010: the CHMED version is chosen by the target format; CHMED23A has no module yet.
    ConversionResult result =
        converter.convert(
            request(FormatId.CH_EMED, FormatId.of("CHMED23A"), "{}", OutputForm.DOCUMENT));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_CONVERSION);
              assertThat(e.message()).contains("CH_EMED -> QR").contains("QR -> CH_EMED");
            });
  }
}
