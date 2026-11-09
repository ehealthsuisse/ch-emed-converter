package ch.emed.converter.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.report.ReasonCode;
import org.junit.jupiter.api.Test;

/** T007 (feature 004): library API additions of contracts/library-api.md. */
class EprApiAdditionsTest {

  @Test
  void chEmedEprHasItsExternalName() {
    assertThat(FormatId.CH_EMED_EPR.id()).isEqualTo("CH_EMED_EPR");
    assertThat(FormatId.CH_EMED_EPR.externalName()).isEqualTo("ch-emed-epr");
    assertThat(FormatId.fromExternalName("ch-emed-epr")).isEqualTo(FormatId.CH_EMED_EPR);
    assertThat(FormatId.CH_EMED_EPR.isChEmed()).isFalse();
  }

  @Test
  void treatmentPlansIsAnOutputForm() {
    assertThat(OutputForm.TREATMENT_PLANS.externalName()).isEqualTo("treatment-plans");
    assertThat(OutputForm.fromExternalName("treatment-plans"))
        .isEqualTo(OutputForm.TREATMENT_PLANS);
  }

  @Test
  void newReasonCodesExist() {
    assertThat(ReasonCode.NO_TREATMENTS).isEqualTo("NO_TREATMENTS");
    assertThat(ReasonCode.TREATMENT_PLAN_EXISTS).isEqualTo("TREATMENT_PLAN_EXISTS");
  }

  @Test
  void defaultsAreUnchanged() {
    assertThat(ConversionOptions.defaults().outputForm()).isEqualTo(OutputForm.DOCUMENT);
    assertThat(ConversionOptions.defaults().confidentiality()).isEqualTo(Confidentiality.NORMAL);
  }
}
