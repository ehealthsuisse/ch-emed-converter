package ch.emed.converter.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.emed.converter.core.report.ReasonCode;
import java.util.ArrayList;
import java.util.List;
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

  private static final String DOC = "urn:uuid:0399ef84-c71b-413b-8a66-b5a835f4f4c5";
  private static final String STMT = "urn:uuid:17837392-0340-414d-a3bf-fa9f237b91ff";

  /** T061: treatment-plan references (research R15). */
  @Test
  void treatmentPlanReferenceAcceptsOnlyUrnUuid() {
    assertThat(new TreatmentPlanReference(DOC, STMT).documentId()).isEqualTo(DOC);

    for (String bad : List.of("0399ef84-c71b-413b-8a66-b5a835f4f4c5", "urn:oid:2.16.756", "")) {
      assertThatThrownBy(() -> new TreatmentPlanReference(bad, STMT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageNotContaining(bad.isEmpty() ? "0000" : bad);
      assertThatThrownBy(() -> new TreatmentPlanReference(DOC, bad))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void treatmentPlanReferencesDefaultToEmptyAndAreCopied() {
    ConversionOptions base = ConversionOptions.defaults().withStrict(true);
    List<TreatmentPlanReference> refs = new ArrayList<>();
    refs.add(new TreatmentPlanReference(DOC, STMT));

    ConversionOptions changed = base.withTreatmentPlanReferences(refs);
    refs.clear();

    assertThat(ConversionOptions.defaults().treatmentPlanReferences()).isEmpty();
    assertThat(changed.treatmentPlanReferences())
        .containsExactly(new TreatmentPlanReference(DOC, STMT));
    assertThat(changed.strict()).isTrue();
    assertThat(changed.withTreatmentPlanReferences(List.of())).isEqualTo(base);
  }

  /** T061: original representation supplied by the caller (research R16). */
  @Test
  void originalRepresentationDefaultsToEmptyAndIsCopied() {
    ConversionOptions base = ConversionOptions.defaults().withOutputForm(OutputForm.DOCUMENT);
    byte[] pdf = {0x25, 0x50, 0x44, 0x46};

    ConversionOptions changed = base.withOriginalRepresentation(pdf);
    pdf[0] = 0;
    changed.originalRepresentation().orElseThrow()[1] = 0;

    assertThat(ConversionOptions.defaults().originalRepresentation()).isEmpty();
    assertThat(changed.originalRepresentation().orElseThrow())
        .containsExactly(0x25, 0x50, 0x44, 0x46);
    assertThat(changed.outputForm()).isEqualTo(OutputForm.DOCUMENT);
    assertThat(changed)
        .isEqualTo(base.withOriginalRepresentation(new byte[] {0x25, 0x50, 0x44, 0x46}));
    assertThat(changed.toString()).contains("originalRepresentationBytes=4");
  }

  @Test
  void mismatchReasonCodeExists() {
    assertThat(ReasonCode.TREATMENT_PLAN_REFERENCES_MISMATCH)
        .isEqualTo("TREATMENT_PLAN_REFERENCES_MISMATCH");
  }
}
