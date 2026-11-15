package ch.emed.converter.chmed23a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/** T019: ChTransmissionFormat ReleaseYear 23, rules M23-IN-03, M23-IN-04, M23-QR-10. */
class TransmissionFormatTest {

  private static final byte[] JSON =
      "{\"patient\":{\"fName\":\"Zoë\"},\"medType\":1}".getBytes(StandardCharsets.UTF_8);
  private static final long LIMIT = 5L * 1024 * 1024;

  /** The example payload of chtransmissionformat/README.md (a pre-release draft, finding F6). */
  private static final String SPEC_EXAMPLE =
      "CHMED23A.H4sIAAAAAAAACq2OOw4CMQxE7zIt2ZUTAmzcLZsGiU+KUCEKYKlokIACRbk7jkLBAWisZz/NyAmb6/gAHxJWI7hsGgqhnsIOnBDBRmF4+9cebCuBtUL0Xy38g73MnIu+DxX/1nRUkCRiv1zLl9tzOF1uIloqxj9FGTKmId1oHcnxtGM7a+28c9YtJqSZCPkD+iD8fPQAAAA=";

  private ReportCollector report = new ReportCollector();

  private byte[] decode(String text) {
    return TransmissionFormat.decode(text, LIMIT, report);
  }

  private ReportEntry singleError() {
    List<ReportEntry> errors =
        report.entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
    assertThat(errors).hasSize(1);
    return errors.get(0);
  }

  private static String payload() {
    return new String(TransmissionFormat.encode(JSON), StandardCharsets.US_ASCII);
  }

  /** Splits the data of a payload into {@code n} chunks. */
  private static List<String> chunks(String payload, int n) {
    String data = payload.substring(TransmissionFormat.PREFIX.length());
    int size = (data.length() + n - 1) / n;
    List<String> chunks = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      String part =
          data.substring(
              Math.min(i * size, data.length()), Math.min((i + 1) * size, data.length()));
      chunks.add("CHMED23A." + (i + 1) + "/" + n + "." + part);
    }
    return chunks;
  }

  @Test
  void encodedPayloadDecodesToTheSameBytes() {
    String payload = payload();
    assertThat(payload).startsWith("CHMED23A.").doesNotContain("\n");
    assertThat(decode(payload)).isEqualTo(JSON);
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void encodingIsDeterministic() {
    assertThat(TransmissionFormat.encode(JSON)).isEqualTo(TransmissionFormat.encode(JSON));
  }

  @Test
  void prefixAndSubVersionAreCaseInsensitive() {
    assertThat(decode("chmed23a." + payload().substring(9))).isEqualTo(JSON);
  }

  @Test
  void otherSubVersionIsUnsupported() {
    assertThat(decode("CHMED23B." + payload().substring(9))).isNull();
    assertThat(singleError().code()).isEqualTo(ReasonCode.UNSUPPORTED_VERSION);
  }

  @Test
  void corruptDataIsParseErrorWithoutContent() {
    assertThat(decode("CHMED23A.%%%notbase64")).isNull();
    assertThat(singleError().code()).isEqualTo(ReasonCode.PARSE_ERROR);
    assertThat(singleError().message()).doesNotContain("notbase64");
  }

  @Test
  void validBase64ButNoGzipIsParseError() {
    assertThat(decode("CHMED23A." + Base64.getEncoder().encodeToString(JSON))).isNull();
    assertThat(singleError().code()).isEqualTo(ReasonCode.PARSE_ERROR);
  }

  @Test
  void decompressedSizeIsLimited() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(new byte[2048]);
    }
    String big = "CHMED23A." + Base64.getEncoder().encodeToString(out.toByteArray());
    assertThat(TransmissionFormat.decode(big, 1024, report)).isNull();
    assertThat(singleError().code()).isEqualTo(ReasonCode.PARSE_ERROR);
    assertThat(singleError().message()).contains("1024");
  }

  @Test
  void specificationExampleDecodesToTheDraftJson() {
    String json = new String(decode(SPEC_EXAMPLE), StandardCharsets.UTF_8);
    assertThat(json).startsWith("{\"Meds\":[");
  }

  @Test
  void chunksInOrderAndShuffledGiveTheSameJson() {
    for (int n : new int[] {2, 3, 4}) {
      List<String> chunks = chunks(payload(), n);
      assertThat(decode(String.join("\n", chunks))).as("%d chunks", n).isEqualTo(JSON);
      List<String> shuffled = new ArrayList<>(chunks);
      Collections.reverse(shuffled);
      assertThat(decode(String.join(" ", shuffled))).as("%d chunks reversed", n).isEqualTo(JSON);
    }
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void missingChunkIsInvalidAndNamesTheIndex() {
    List<String> chunks = new ArrayList<>(chunks(payload(), 3));
    chunks.remove(1);
    assertThat(decode(String.join("\n", chunks))).isNull();
    assertThat(singleError().code()).isEqualTo(ReasonCode.CHUNK_INVALID);
    assertThat(singleError().message()).contains("Chunk 2 of 3");
  }

  @Test
  void duplicatedChunkIsInvalid() {
    List<String> chunks = new ArrayList<>(chunks(payload(), 2));
    chunks.add(chunks.get(0));
    assertThat(decode(String.join("\n", chunks))).isNull();
    assertThat(singleError().code()).isEqualTo(ReasonCode.CHUNK_INVALID);
    assertThat(singleError().message()).contains("Chunk 1").doesNotContain("H4sI");
  }

  @Test
  void outOfRangeMixedTotalsAndSingleChunkAreInvalid() {
    List<String> chunks = chunks(payload(), 2);
    for (String input :
        List.of(
            chunks.get(0) + "\n" + chunks.get(1).replace(".2/2.", ".3/2."),
            chunks.get(0) + "\n" + chunks.get(1).replace(".2/2.", ".2/3."),
            chunks.get(0) + "\n" + chunks.get(1).replace("CHMED23A.", "CHMED23B."),
            "CHMED23A.1/1." + payload().substring(9),
            chunks.get(0) + "\n" + payload())) {
      report = new ReportCollector();
      assertThat(decode(input)).as(input.substring(0, 14)).isNull();
      assertThat(singleError().code()).isEqualTo(ReasonCode.CHUNK_INVALID);
    }
  }

  @Test
  void aSingleTokenWithChunkMetadataIsRecognised() {
    assertThat(TransmissionFormat.isChunk(chunks(payload(), 2).get(0))).isTrue();
    assertThat(TransmissionFormat.isChunk(payload())).isFalse();
  }
}
