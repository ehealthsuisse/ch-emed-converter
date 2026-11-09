package ch.emed.converter.epr;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.FormatInfo;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.spi.FormatModule;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/** T010 (feature 004): declarations of the module (data-model.md §1). */
class ChEmedEprFormatModuleTest {

  private final ChEmedEprFormatModule module = new ChEmedEprFormatModule();

  @Test
  void declaresFormatFormsAndPinnedSpecification() {
    assertThat(module.format()).isEqualTo(FormatId.CH_EMED_EPR);
    assertThat(module.pinnedSpecification()).isEqualTo("ch.fhir.ig.ch-emed-epr#3.0.0");
    assertThat(module.inputForms()).containsExactly("json");
    assertThat(module.outputForms())
        .containsExactly(OutputForm.DOCUMENT, OutputForm.TREATMENT_PLANS);
  }

  @Test
  void isFoundThroughServiceLoaderAndListedByTheConverter() {
    assertThat(ServiceLoader.load(FormatModule.class).stream().map(ServiceLoader.Provider::type))
        .contains(ChEmedEprFormatModule.class);

    Converter converter = Converters.create(null);
    assertThat(converter.formats())
        .filteredOn(f -> f.format().equals(FormatId.CH_EMED_EPR))
        .singleElement()
        .extracting(FormatInfo::outputForms)
        .isEqualTo(List.of(OutputForm.DOCUMENT, OutputForm.TREATMENT_PLANS));
    assertThat(converter.pinnedSpecifications())
        .containsEntry("CH_EMED_EPR", "ch.fhir.ig.ch-emed-epr#3.0.0");
  }
}
