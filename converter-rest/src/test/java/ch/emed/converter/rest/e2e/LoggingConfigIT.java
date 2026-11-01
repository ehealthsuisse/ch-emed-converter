package ch.emed.converter.rest.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.rest.RestTestSupport;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Logging of the packaged service: console only by default, in particular no {@code spring.log} in
 * the temp directory; a log file with metadata only when the operator sets {@code
 * logging.file.name}.
 */
class LoggingConfigIT {

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void noLogFileByDefault() throws Exception {
    PackagedJar service = PackagedJar.get();
    convert(service);

    assertThat(PackagedJar.TEMP.resolve("spring.log")).doesNotExist();
    assertThat(Files.readString(PackagedJar.LOG)).contains("request timestamp=");
  }

  @Test
  void logFileOnlyWhenTheOperatorAsksForItAndWithoutContent() throws Exception {
    Path logFile = Path.of("target", "logging-config-it", "rest-service.log");
    Files.deleteIfExists(logFile);

    PackagedJar service =
        PackagedJar.start(
            Path.of("target", "logging-config-it-console.log"),
            Path.of("target", "logging-config-it-tmp"),
            "--logging.file.name=" + logFile);
    try {
      convert(service);
    } finally {
      service.stop();
    }

    assertThat(logFile).exists();
    assertThat(Files.readString(logFile))
        .contains("request timestamp=")
        .doesNotContain("Muster", "1950-03-21", "Erika", "\"Medicaments\"");
  }

  private void convert(PackagedJar service) throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-basic.json"));
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(service.uri("/api/v1/conversions/chmed16a/ch-emed"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        RestTestSupport.requestMessage(plan, RestTestSupport.withAuthor(""))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
  }
}
