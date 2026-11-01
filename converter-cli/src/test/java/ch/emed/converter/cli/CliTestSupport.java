package ch.emed.converter.cli;

import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Runs the CLI in-process with a shared converter (the validation context is expensive). */
final class CliTestSupport {

  private static Converter converter;

  record Run(int exitCode, byte[] stdout, String stderr) {
    String stdoutText() {
      return new String(stdout, StandardCharsets.UTF_8);
    }
  }

  private CliTestSupport() {}

  static synchronized Converter converter() {
    if (converter == null) {
      converter = Converters.create();
    }
    return converter;
  }

  static Path resource(String name) {
    try {
      return Path.of(CliTestSupport.class.getResource("/" + name).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  static Run run(byte[] stdin, String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int exit =
        CliMain.execute(
            CliTestSupport::converter,
            args,
            new ByteArrayInputStream(stdin),
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
    return new Run(exit, out.toByteArray(), err.toString(StandardCharsets.UTF_8));
  }

  static Run run(String... args) {
    return run(new byte[0], args);
  }
}
