package ch.emed.converter.rest.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The packaged service ({@code target/ch-emed-converter-rest.jar}) started once per test JVM in its
 * own process, on a free port; stopped at JVM exit. Used by the end-to-end ITs (failsafe runs them
 * after {@code package}).
 */
final class PackagedJar {

  static final Path JAR = Path.of("target", "ch-emed-converter-rest.jar");
  static final Path LOG = Path.of("target", "packaged-jar-it.log");
  static final Path TEMP = Path.of("target", "packaged-jar-it-tmp");

  private static PackagedJar instance;

  private final Process process;
  private final URI base;

  private PackagedJar(Process process, int port) {
    this.process = process;
    this.base = URI.create("http://localhost:" + port);
  }

  /** Starts the JAR if needed and waits until readiness is UP. */
  static synchronized PackagedJar get() {
    if (instance == null) {
      instance = start(LOG, TEMP);
      Runtime.getRuntime().addShutdownHook(new Thread(instance.process::destroy));
    }
    return instance;
  }

  URI uri(String path) {
    return base.resolve(path);
  }

  /** Stops this instance and waits for its end, so that its files are complete. */
  void stop() throws InterruptedException {
    process.destroy();
    process.waitFor();
  }

  String log() {
    try {
      return Files.readString(LOG);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A further instance with its own console log and temp directory; the caller stops it. */
  static PackagedJar start(Path consoleLog, Path tempDirectory, String... serviceArgs) {
    if (!Files.isRegularFile(JAR)) {
      throw new IllegalStateException(JAR + " missing; run the integration tests after package");
    }
    try {
      Files.createDirectories(tempDirectory);
      int port;
      try (ServerSocket socket = new ServerSocket(0)) {
        port = socket.getLocalPort();
      }
      Path java = Path.of(System.getProperty("java.home"), "bin", "java");
      List<String> command = new ArrayList<>();
      command.add(java.toString());
      command.add("-Djava.io.tmpdir=" + tempDirectory.toAbsolutePath());
      command.addAll(List.of("-jar", JAR.toString(), "--server.port=" + port));
      command.addAll(List.of(serviceArgs));
      Process process =
          new ProcessBuilder(command)
              .redirectErrorStream(true)
              .redirectOutput(consoleLog.toFile())
              .start();
      PackagedJar jar = new PackagedJar(process, port);
      jar.awaitReady(Duration.ofMinutes(3));
      return jar;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void awaitReady(Duration timeout) {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest readiness = HttpRequest.newBuilder(uri("/actuator/health/readiness")).build();
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      if (!process.isAlive()) {
        throw new IllegalStateException("Service exited; see " + LOG);
      }
      try {
        HttpResponse<String> response =
            client.send(readiness, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && response.body().contains("\"UP\"")) {
          return;
        }
      } catch (IOException e) {
        // not listening yet
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    process.destroy();
    throw new IllegalStateException("Service not ready within " + timeout + "; see " + LOG);
  }
}
