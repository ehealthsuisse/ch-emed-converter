package ch.emed.converter.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PackageLoaderTest {

  @Test
  void loadsEveryPackageMarkedForLoading() {
    List<PackageLoader.PackageRef> loaded = PackageLoader.loadManifest("packages");

    assertThat(loaded)
        .extracting(PackageLoader.PackageRef::coordinates)
        .contains(
            "ch.fhir.ig.ch-emed#6.0.0",
            "ch.fhir.ig.ch-core#6.0.0",
            "ch.fhir.ig.ch-term#3.3.0",
            "hl7.terminology.r4#7.0.1",
            "hl7.fhir.uv.extensions.r4#5.2.0")
        .doesNotContain("hl7.terminology.r4#6.5.0");
    assertThat(PackageLoader.rootPackage("packages")).isEqualTo("ch.fhir.ig.ch-emed#6.0.0");
  }

  @Test
  void checksumMismatchIsRejected() {
    assertThatThrownBy(() -> PackageLoader.loadManifest("bad-packages"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dummy.tgz");
  }
}
